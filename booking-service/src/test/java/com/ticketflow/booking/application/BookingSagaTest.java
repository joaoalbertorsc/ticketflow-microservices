package com.ticketflow.booking.application;

import com.ticketflow.booking.application.BookingExceptions.BookingNotFoundException;
import com.ticketflow.booking.application.BookingSaga.BookCommand;
import com.ticketflow.booking.application.BookingSaga.BookResult;
import com.ticketflow.booking.application.port.Ports.Charge;
import com.ticketflow.booking.application.port.Ports.Confirmation;
import com.ticketflow.booking.application.port.Ports.Reservation;
import com.ticketflow.booking.application.port.Ports.SaleLookup;
import com.ticketflow.booking.application.port.Ports.Undo;
import com.ticketflow.booking.application.port.Ports.UserLookup;
import com.ticketflow.booking.domain.Booking;
import com.ticketflow.booking.domain.BookingStateException;
import com.ticketflow.booking.domain.BookingStatus;
import com.ticketflow.booking.domain.FailureCode;
import com.ticketflow.booking.domain.NotificationStatus;
import com.ticketflow.booking.domain.SagaStep;
import com.ticketflow.booking.domain.SagaStepName;
import com.ticketflow.booking.domain.Snapshots.SaleSnapshot;
import com.ticketflow.booking.domain.StepOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Every row of the design's "step by step, with failures and fallbacks" table, against fakes. */
class BookingSagaTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final UUID USER = UUID.randomUUID();

	FakeServices services;
	InMemoryBookingRepository repository;
	BookingStore store;
	BookingSaga saga;
	CancellationService cancellations;

	@BeforeEach
	void setUp() {
		services = new FakeServices();
		repository = new InMemoryBookingRepository();
		store = new BookingStore(repository, mock(PlatformTransactionManager.class));
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		Compensations compensations = new Compensations(store, services, services, services, clock);
		saga = new BookingSaga(store, repository, services, services, services, services, compensations, clock);
		cancellations = new CancellationService(store, compensations, clock);
	}

	private Booking book() {
		return book(null);
	}

	private Booking book(String idempotencyKey) {
		BookResult result = saga.book(new BookCommand(USER, idempotencyKey, "event-1", "plateia", 2, "tok_visa"));
		return store.get(result.bookingId());
	}

	@Test
	void happyPathConfirmsSnapshotsAndNotifies() {
		Booking booking = book();

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(booking.getTotalAmount()).isEqualByComparingTo("240.00");
		assertThat(services.chargedAmount).isEqualByComparingTo("240.00");
		assertThat(booking.getUserEmail()).isEqualTo("ana@mail.com");
		assertThat(booking.getEventName()).isEqualTo("Hamlet");
		assertThat(booking.getNotificationStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(services.calls).containsExactly(
				"findUser", "findSale", "reserve", "charge", "confirm", "notify(BOOKING_CONFIRMED)");
		assertThat(booking.getSteps()).extracting(SagaStep::getStep).containsExactly(
				SagaStepName.FETCH_USER, SagaStepName.FETCH_EVENT, SagaStepName.RESERVE_SEATS, SagaStepName.CHARGE,
				SagaStepName.CONFIRM_SEATS, SagaStepName.NOTIFY);
		assertThat(services.notifications.getFirst().data()).containsEntry("quantity", "2").containsEntry("totalAmount", "240.00");
	}

	@Test
	void unknownUserFailsWithoutTouchingAnythingElse() {
		services.user = UserLookup.notFound();

		Booking booking = book();

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
		assertThat(booking.getFailureCode()).isEqualTo(FailureCode.USER_NOT_FOUND);
		assertThat(services.calls).containsExactly("findUser"); // and no notification: we don't know who to tell
	}

	@Test
	void authUnavailableIsAServiceFailureWithNothingToUndo() {
		services.user = UserLookup.unavailable("auth-service unavailable (circuit open)");

		Booking booking = book();

		assertThat(booking.getFailureCode()).isEqualTo(FailureCode.SERVICE_UNAVAILABLE);
		assertThat(booking.getSteps().getFirst().getOutcome()).isEqualTo(StepOutcome.FALLBACK);
		assertThat(services.calls).containsExactly("findUser");
	}

	@Test
	void eventNotOnSaleFails() {
		services.sale = SaleLookup.found(new SaleSnapshot("Hamlet", FakeServices.STARTS_AT, "Theatro", "Plateia",
				BigDecimal.TEN, "BRL", false));

		Booking booking = book();

		assertThat(booking.getFailureCode()).isEqualTo(FailureCode.EVENT_NOT_AVAILABLE);
		assertThat(services.calls).containsExactly("findUser", "findSale");
	}

	@Test
	void catalogUnavailableFailsWithoutReserving() {
		services.sale = SaleLookup.unavailable("catalog-service unavailable");

		assertThat(book().getFailureCode()).isEqualTo(FailureCode.SERVICE_UNAVAILABLE);
		assertThat(services.calls).doesNotContain("reserve");
	}

	@Test
	void soldOutFailsAndTellsTheUser() {
		services.reservation = Reservation.rejected(Reservation.Status.NOT_ENOUGH_SEATS, "Only 1 seat left");

		Booking booking = book();

		assertThat(booking.getFailureCode()).isEqualTo(FailureCode.SOLD_OUT);
		assertThat(services.calls).containsExactly("findUser", "findSale", "reserve", "notify(BOOKING_FAILED)");
		assertThat(services.notifications.getFirst().data().get("reason")).contains("not enough seats");
	}

	@Test
	void reserveUnavailableReleasesInCaseTheHoldWasMade() {
		services.reservation = Reservation.unavailable("inventory-service unavailable (no answer in time)");

		Booking booking = book();

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
		assertThat(booking.getFailureCode()).isEqualTo(FailureCode.SERVICE_UNAVAILABLE);
		assertThat(services.calls).containsExactly("findUser", "findSale", "reserve", "release",
				"notify(BOOKING_FAILED)"); // no refund: nothing was charged
	}

	@Test
	void declinedCardRefundsAndReleases() {
		services.charge = Charge.declined(UUID.randomUUID(), "Card declined: INSUFFICIENT_FUNDS");

		Booking booking = book();

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
		assertThat(booking.getFailureCode()).isEqualTo(FailureCode.PAYMENT_DECLINED);
		assertThat(services.calls).containsSequence("charge", "refund", "release", "notify(BOOKING_FAILED)");
	}

	@Test
	void paymentUnavailableRefundsByBookingAndReleases() {
		services.charge = Charge.unavailable("payment-service unavailable (circuit open)");

		Booking booking = book();

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
		assertThat(booking.getFailureCode()).isEqualTo(FailureCode.SERVICE_UNAVAILABLE);
		assertThat(services.calls).containsSequence("charge", "refund", "release");
	}

	@Test
	void undoThatCantRunNowLeavesTheBookingCompensatingNotFailed() {
		services.charge = Charge.unavailable("payment-service unavailable");
		services.refund = Undo.unavailable("payment-service unavailable");

		Booking booking = book();

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPENSATING);
		assertThat(services.calls).noneMatch(call -> call.startsWith("notify"));
	}

	@Test
	void holdExpiredOnConfirmRefunds() {
		services.confirmation = Confirmation.holdExpired("The hold expired");

		Booking booking = book();

		assertThat(booking.getFailureCode()).isEqualTo(FailureCode.HOLD_EXPIRED);
		assertThat(services.calls).containsSequence("confirm", "refund", "release", "notify(BOOKING_FAILED)");
	}

	@Test
	void notificationFallbackKeepsTheBookingConfirmed() {
		services.notification = Undo.unavailable("notification-service unavailable");

		Booking booking = book();

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
		assertThat(booking.getNotificationStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(booking.getSteps().getLast().getOutcome()).isEqualTo(StepOutcome.FALLBACK);
	}

	@Test
	void sameIdempotencyKeyReturnsTheSameBookingAndRunsTheSagaOnce() {
		Booking first = book("key-1");
		Booking again = book("key-1");

		assertThat(again.getId()).isEqualTo(first.getId());
		assertThat(repository.count()).isEqualTo(1);
		assertThat(services.calls).filteredOn("charge"::equals).hasSize(1);
	}

	@Test
	void cancellationRefundsReleasesAndNotifies() {
		Booking booking = book();
		services.calls.clear();

		cancellations.cancel(booking.getId(), USER);

		assertThat(store.get(booking.getId()).getStatus()).isEqualTo(BookingStatus.CANCELLED);
		assertThat(services.calls).containsExactly("refund", "release(cancelled)", "notify(BOOKING_CANCELLED)");
	}

	@Test
	void onlyTheOwnerCanCancel() {
		Booking booking = book();

		assertThatThrownBy(() -> cancellations.cancel(booking.getId(), UUID.randomUUID()))
				.isInstanceOf(BookingNotFoundException.class);
	}

	@Test
	void failedBookingCantBeCancelled() {
		services.reservation = Reservation.rejected(Reservation.Status.NOT_ENOUGH_SEATS, "Sold out");
		Booking booking = book();

		assertThatThrownBy(() -> cancellations.cancel(booking.getId(), USER)).isInstanceOf(BookingStateException.class);
	}
}
