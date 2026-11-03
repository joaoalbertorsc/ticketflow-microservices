package com.ticketflow.inventory;

import com.ticketflow.inventory.application.ExpiredHoldsJob;
import com.ticketflow.inventory.application.InventoryExceptions.HoldExpiredException;
import com.ticketflow.inventory.application.InventoryExceptions.NotEnoughSeatsException;
import com.ticketflow.inventory.application.ReservationService;
import com.ticketflow.inventory.application.ReservationService.ReserveCommand;
import com.ticketflow.inventory.application.StockService;
import com.ticketflow.inventory.domain.InventoryConflictException;
import com.ticketflow.inventory.domain.ReleaseReason;
import com.ticketflow.inventory.domain.Reservation;
import com.ticketflow.inventory.domain.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real PostgreSQL: the SQL guards, constraints, locks and the expiry job. */
@SpringBootTest
@Import(IntegrationTestConfig.class)
@ActiveProfiles("test")
class InventoryIntegrationTest {

	@Autowired
	StockService stocks;

	@Autowired
	ReservationService reservations;

	@Autowired
	ExpiredHoldsJob expiredHoldsJob;

	@Autowired
	MutableClock clock;

	String eventId;

	@BeforeEach
	void setUp() {
		// a fresh event per test, so tests don't share seats
		eventId = UUID.randomUUID().toString().substring(0, 24);
		stocks.setCapacity(eventId, "pista", 10);
	}

	private int available() {
		return stocks.get(eventId, "pista").getAvailableSeats();
	}

	private Reservation reserve(UUID bookingId, int quantity) {
		return reservations.reserve(new ReserveCommand(bookingId, eventId, "pista", quantity)).reservation();
	}

	@Test
	void fullLifecycleKeepsTheSeatCountRight() {
		UUID booking = UUID.randomUUID();

		reserve(booking, 3);
		assertThat(available()).isEqualTo(7);

		reservations.confirm(booking);
		assertThat(available()).isEqualTo(7);

		reservations.release(booking, ReleaseReason.CANCELLED);
		assertThat(available()).isEqualTo(10);
		assertThat(reservations.get(booking).getStatus()).isEqualTo(ReservationStatus.RELEASED);
	}

	@Test
	void repeatedCallsChangeNothing() {
		UUID booking = UUID.randomUUID();

		Reservation first = reserve(booking, 2);
		Reservation again = reserve(booking, 2);
		assertThat(again.getId()).isEqualTo(first.getId());
		assertThat(available()).isEqualTo(8);

		reservations.release(booking, ReleaseReason.COMPENSATION);
		reservations.release(booking, ReleaseReason.COMPENSATION);
		assertThat(available()).isEqualTo(10);
	}

	@Test
	void cannotTakeMoreSeatsThanAvailable() {
		reserve(UUID.randomUUID(), 8);

		assertThatThrownBy(() -> reserve(UUID.randomUUID(), 3))
				.isInstanceOfSatisfying(NotEnoughSeatsException.class, ex -> assertThat(ex.getAvailable()).isEqualTo(2));
		assertThat(available()).isEqualTo(2);
	}

	@Test
	void confirmAfterExpiryFailsAndTheReleaseIsCommitted() {
		UUID booking = UUID.randomUUID();
		reserve(booking, 4);

		clock.advance(Duration.ofMinutes(11));

		assertThatThrownBy(() -> reservations.confirm(booking)).isInstanceOf(HoldExpiredException.class);
		// the exception did not roll back the release
		assertThat(available()).isEqualTo(10);
		assertThat(reservations.get(booking).getReleaseReason()).isEqualTo(ReleaseReason.EXPIRED);
	}

	@Test
	void jobReleasesOnlyExpiredHolds() {
		UUID old = UUID.randomUUID();
		UUID confirmed = UUID.randomUUID();
		reserve(old, 2);
		reserve(confirmed, 3);
		reservations.confirm(confirmed);
		clock.advance(Duration.ofMinutes(11));
		UUID fresh = UUID.randomUUID();
		reserve(fresh, 1);

		expiredHoldsJob.releaseExpiredHolds();

		assertThat(reservations.get(old).getStatus()).isEqualTo(ReservationStatus.RELEASED);
		assertThat(reservations.get(confirmed).getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(reservations.get(fresh).getStatus()).isEqualTo(ReservationStatus.HELD);
		assertThat(available()).isEqualTo(10 - 3 - 1);
	}

	@Test
	void resizeKeepsTakenSeatsAndCannotGoBelowThem() {
		reserve(UUID.randomUUID(), 6);

		stocks.setCapacity(eventId, "pista", 20);
		assertThat(available()).isEqualTo(14);

		assertThatThrownBy(() -> stocks.setCapacity(eventId, "pista", 5)).isInstanceOf(InventoryConflictException.class);
	}
}
