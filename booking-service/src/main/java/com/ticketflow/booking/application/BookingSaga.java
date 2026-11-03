package com.ticketflow.booking.application;

import com.ticketflow.booking.application.port.Ports.Catalog;
import com.ticketflow.booking.application.port.Ports.Charge;
import com.ticketflow.booking.application.port.Ports.Confirmation;
import com.ticketflow.booking.application.port.Ports.Inventory;
import com.ticketflow.booking.application.port.Ports.Payments;
import com.ticketflow.booking.application.port.Ports.Reservation;
import com.ticketflow.booking.application.port.Ports.SaleLookup;
import com.ticketflow.booking.application.port.Ports.UserDirectory;
import com.ticketflow.booking.application.port.Ports.UserLookup;
import com.ticketflow.booking.domain.Booking;
import com.ticketflow.booking.domain.BookingRepository;
import com.ticketflow.booking.domain.FailureCode;
import com.ticketflow.booking.domain.SagaStepName;
import com.ticketflow.booking.domain.StepOutcome;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * The booking saga, orchestrated: user → event → reserve seats → charge → confirm seats → notify.
 * Each step's result is committed before the next call. When a step fails after seats may be held
 * or money may be taken, the booking goes to COMPENSATING and {@link Compensations} undoes it.
 */
@Service
public class BookingSaga {

	private final BookingStore store;
	private final BookingRepository bookings;
	private final UserDirectory users;
	private final Catalog catalog;
	private final Inventory inventory;
	private final Payments payments;
	private final Compensations compensations;
	private final Clock clock;

	public BookingSaga(BookingStore store, BookingRepository bookings, UserDirectory users, Catalog catalog,
			Inventory inventory, Payments payments, Compensations compensations, Clock clock) {
		this.store = store;
		this.bookings = bookings;
		this.users = users;
		this.catalog = catalog;
		this.inventory = inventory;
		this.payments = payments;
		this.compensations = compensations;
		this.clock = clock;
	}

	/** The card token is used for the charge and never stored. */
	public record BookCommand(UUID userId, String idempotencyKey, String eventId, String sectorId, int quantity,
			String cardToken) {
	}

	/** @param created {@code false} when the idempotency key matched an earlier booking */
	public record BookResult(UUID bookingId, boolean created) {
	}

	public BookResult book(BookCommand command) {
		Optional<Booking> earlier = findByIdempotencyKey(command);
		if (earlier.isPresent()) {
			return new BookResult(earlier.get().getId(), false);
		}
		Booking booking;
		try {
			booking = store.insert(Booking.start(command.userId(), command.idempotencyKey(), command.eventId(),
					command.sectorId(), command.quantity(), clock.instant()));
		} catch (DataIntegrityViolationException sameKeyAtTheSameTime) {
			return new BookResult(findByIdempotencyKey(command).orElseThrow().getId(), false);
		}
		run(booking.getId(), command.cardToken());
		return new BookResult(booking.getId(), true);
	}

	private Optional<Booking> findByIdempotencyKey(BookCommand command) {
		return command.idempotencyKey() == null ? Optional.empty()
				: bookings.findByUserIdAndIdempotencyKey(command.userId(), command.idempotencyKey());
	}

	private void run(UUID id, String cardToken) {
		Booking booking = store.get(id);
		UUID userId = booking.getUserId();

		// 1. Who is booking — nothing to undo yet if this fails
		UserLookup user = users.findUser(userId);
		switch (user.status()) {
			case NOT_FOUND -> {
				fail(id, SagaStepName.FETCH_USER, StepOutcome.FAILED, FailureCode.USER_NOT_FOUND, user.detail());
				return;
			}
			case UNAVAILABLE -> {
				fail(id, SagaStepName.FETCH_USER, StepOutcome.FALLBACK, FailureCode.SERVICE_UNAVAILABLE, user.detail());
				return;
			}
			case FOUND -> step(id, SagaStepName.FETCH_USER, user.user().email());
		}

		// 2. What is being bought and for how much — still nothing to undo
		SaleLookup sale = catalog.findSale(userId, booking.getEventId(), booking.getSectorId());
		switch (sale.status()) {
			case NOT_FOUND -> {
				fail(id, SagaStepName.FETCH_EVENT, StepOutcome.FAILED, FailureCode.EVENT_NOT_AVAILABLE, sale.detail());
				return;
			}
			case UNAVAILABLE -> {
				fail(id, SagaStepName.FETCH_EVENT, StepOutcome.FALLBACK, FailureCode.SERVICE_UNAVAILABLE, sale.detail());
				return;
			}
			case FOUND -> {
				if (!sale.sale().onSale()) {
					fail(id, SagaStepName.FETCH_EVENT, StepOutcome.FAILED, FailureCode.EVENT_NOT_AVAILABLE,
							"Event is not on sale");
					return;
				}
				booking = store.update(id, b -> {
					b.takeSnapshots(user.user(), sale.sale(), clock.instant());
					b.log(SagaStepName.FETCH_EVENT, StepOutcome.SUCCEEDED,
							sale.sale().eventName() + ", " + sale.sale().currency() + " " + sale.sale().unitPrice() + " each",
							clock.instant());
				});
			}
		}

		// 3. Hold the seats — from here on, a failure may need an undo
		Reservation reservation = inventory.reserve(userId, id, booking.getEventId(), booking.getSectorId(),
				booking.getQuantity());
		switch (reservation.status()) {
			case NOT_ENOUGH_SEATS -> {
				fail(id, SagaStepName.RESERVE_SEATS, StepOutcome.FAILED, FailureCode.SOLD_OUT, reservation.detail());
				compensations.notifyOutcome(id);
				return;
			}
			case NO_STOCK -> {
				fail(id, SagaStepName.RESERVE_SEATS, StepOutcome.FAILED, FailureCode.EVENT_NOT_AVAILABLE, reservation.detail());
				compensations.notifyOutcome(id);
				return;
			}
			case UNAVAILABLE -> {
				// The call may have timed out after the seats were held: release them to be sure.
				compensate(id, SagaStepName.RESERVE_SEATS, StepOutcome.FALLBACK, FailureCode.SERVICE_UNAVAILABLE,
						reservation.detail());
				return;
			}
			case RESERVED -> store.update(id, b -> {
				b.seatsReserved(reservation.reservationId(), clock.instant());
				b.log(SagaStepName.RESERVE_SEATS, StepOutcome.SUCCEEDED, "reservation " + reservation.reservationId(),
						clock.instant());
				b.chargeAttempted(clock.instant()); // recorded before calling: any undo now includes a refund
			});
		}

		// 4. Take the money
		Charge charge = payments.charge(userId, id, booking.getTotalAmount(), booking.getCurrency(), cardToken);
		switch (charge.status()) {
			case DECLINED -> {
				compensate(id, SagaStepName.CHARGE, StepOutcome.FAILED, FailureCode.PAYMENT_DECLINED, charge.detail());
				return;
			}
			case VOIDED, UNAVAILABLE -> {
				compensate(id, SagaStepName.CHARGE, StepOutcome.FALLBACK, FailureCode.SERVICE_UNAVAILABLE, charge.detail());
				return;
			}
			case APPROVED -> store.update(id, b -> {
				b.paid(charge.paymentId(), clock.instant());
				b.log(SagaStepName.CHARGE, StepOutcome.SUCCEEDED, "payment " + charge.paymentId(), clock.instant());
			});
		}

		// 5. Make the seats permanent
		Confirmation confirmation = inventory.confirm(userId, id);
		switch (confirmation.status()) {
			case HOLD_EXPIRED -> {
				compensate(id, SagaStepName.CONFIRM_SEATS, StepOutcome.FAILED, FailureCode.HOLD_EXPIRED, confirmation.detail());
				return;
			}
			case UNAVAILABLE -> {
				compensate(id, SagaStepName.CONFIRM_SEATS, StepOutcome.FALLBACK, FailureCode.SERVICE_UNAVAILABLE,
						confirmation.detail());
				return;
			}
			case CONFIRMED -> store.update(id, b -> {
				b.confirmed(clock.instant());
				b.log(SagaStepName.CONFIRM_SEATS, StepOutcome.SUCCEEDED, null, clock.instant());
			});
		}

		// 6. Tell the user — if this fails the booking is still CONFIRMED (notification PENDING)
		compensations.notifyOutcome(id);
	}

	private void step(UUID id, SagaStepName step, String detail) {
		store.update(id, b -> b.log(step, StepOutcome.SUCCEEDED, detail, clock.instant()));
	}

	/** A failure before anything was held or charged: no undo needed. */
	private void fail(UUID id, SagaStepName step, StepOutcome outcome, FailureCode code, String detail) {
		store.update(id, b -> {
			b.log(step, outcome, detail, clock.instant());
			b.failed(code, detail, clock.instant());
		});
	}

	/** A failure after seats may be held or money may be taken: undo it now (or later, via recovery). */
	private void compensate(UUID id, SagaStepName step, StepOutcome outcome, FailureCode code, String detail) {
		store.update(id, b -> {
			b.log(step, outcome, detail, clock.instant());
			b.startCompensation(code, detail, clock.instant());
		});
		compensations.undo(id);
	}
}
