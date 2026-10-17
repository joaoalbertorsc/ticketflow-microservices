package com.ticketflow.inventory.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final Instant EXPIRES = NOW.plus(Duration.ofMinutes(10));

	private final SectorStock stock = SectorStock.create("event-1", "pista", 100, NOW);

	private Reservation hold() {
		return Reservation.hold(UUID.randomUUID(), stock, 2, EXPIRES, NOW);
	}

	@Test
	void holdCanBeConfirmedBeforeItExpires() {
		Reservation reservation = hold();

		reservation.confirm(EXPIRES.minusSeconds(1));

		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
	}

	@Test
	void confirmIsIdempotent() {
		Reservation reservation = hold();
		reservation.confirm(NOW);

		reservation.confirm(NOW);

		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
	}

	@Test
	void holdIsExpiredFromItsExpiryInstant() {
		Reservation reservation = hold();

		assertThat(reservation.isExpiredHold(EXPIRES.minusMillis(1))).isFalse();
		assertThat(reservation.isExpiredHold(EXPIRES)).isTrue();
	}

	@Test
	void confirmedReservationIsNeverConsideredExpired() {
		Reservation reservation = hold();
		reservation.confirm(NOW);

		assertThat(reservation.isExpiredHold(EXPIRES.plusSeconds(60))).isFalse();
	}

	@Test
	void releasedReservationCannotBeConfirmed() {
		Reservation reservation = hold();
		reservation.release(ReleaseReason.COMPENSATION, NOW);

		assertThatThrownBy(() -> reservation.confirm(NOW)).isInstanceOf(InventoryConflictException.class);
	}

	@Test
	void releaseIsIdempotentAndOnlyGivesSeatsBackOnce() {
		Reservation reservation = hold();

		assertThat(reservation.release(ReleaseReason.COMPENSATION, NOW)).isTrue();
		assertThat(reservation.release(ReleaseReason.CANCELLED, NOW)).isFalse();
		assertThat(reservation.getReleaseReason()).isEqualTo(ReleaseReason.COMPENSATION);
	}

	@Test
	void sameRequestDetectionComparesEventSectorAndQuantity() {
		Reservation reservation = hold();

		assertThat(reservation.isSameRequest("event-1", "pista", 2)).isTrue();
		assertThat(reservation.isSameRequest("event-1", "pista", 3)).isFalse();
		assertThat(reservation.isSameRequest("event-1", "vip", 2)).isFalse();
	}

	@Test
	void resizeKeepsTakenSeatsAndRefusesToGoBelowThem() {
		SectorStock sector = SectorStock.create("event-1", "pista", 10, NOW);
		ReflectionTestUtils.setField(sector, "availableSeats", 4); // 6 taken

		sector.resize(20, NOW);
		assertThat(sector.getTotalSeats()).isEqualTo(20);
		assertThat(sector.getAvailableSeats()).isEqualTo(14);

		assertThatThrownBy(() -> sector.resize(5, NOW)).isInstanceOf(InventoryConflictException.class);
	}
}
