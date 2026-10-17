package com.ticketflow.inventory.application;

import com.ticketflow.inventory.application.InventoryExceptions.HoldExpiredException;
import com.ticketflow.inventory.application.InventoryExceptions.NotEnoughSeatsException;
import com.ticketflow.inventory.application.InventoryExceptions.StockNotFoundException;
import com.ticketflow.inventory.application.InventoryExceptions.TooManySeatsException;
import com.ticketflow.inventory.application.ReservationService.ReserveCommand;
import com.ticketflow.inventory.application.ReservationService.ReserveResult;
import com.ticketflow.inventory.domain.InventoryConflictException;
import com.ticketflow.inventory.domain.ReleaseReason;
import com.ticketflow.inventory.domain.Reservation;
import com.ticketflow.inventory.domain.ReservationRepository;
import com.ticketflow.inventory.domain.ReservationStatus;
import com.ticketflow.inventory.domain.SectorStock;
import com.ticketflow.inventory.domain.StockRepository;
import com.ticketflow.inventory.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final UUID BOOKING = UUID.randomUUID();

	@Mock
	StockRepository stocks;

	@Mock
	ReservationRepository reservations;

	MutableClock clock = new MutableClock(NOW);
	ReservationService service;
	SectorStock stock = SectorStock.create("event-1", "pista", 100, NOW);

	@BeforeEach
	void setUp() {
		service = new ReservationService(stocks, reservations,
				new InventoryProperties(Duration.ofMinutes(10), 10, 100), clock);
	}

	@Test
	void reserveTakesSeatsAndCreatesAHoldThatExpiresLater() {
		when(reservations.findByBookingId(BOOKING)).thenReturn(Optional.empty());
		when(stocks.findByEventIdAndSectorId("event-1", "pista")).thenReturn(Optional.of(stock));
		when(stocks.takeSeats(stock.getId(), 2)).thenReturn(1);
		when(reservations.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReserveResult result = service.reserve(new ReserveCommand(BOOKING, "event-1", "pista", 2));

		assertThat(result.created()).isTrue();
		assertThat(result.reservation().getStatus()).isEqualTo(ReservationStatus.HELD);
		assertThat(result.reservation().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
	}

	@Test
	void repeatedReserveReturnsTheSameHoldWithoutTakingMoreSeats() {
		Reservation existing = Reservation.hold(BOOKING, stock, 2, NOW.plusSeconds(600), NOW);
		when(reservations.findByBookingId(BOOKING)).thenReturn(Optional.of(existing));

		ReserveResult result = service.reserve(new ReserveCommand(BOOKING, "event-1", "pista", 2));

		assertThat(result.created()).isFalse();
		assertThat(result.reservation()).isSameAs(existing);
		verify(stocks, never()).takeSeats(any(), anyInt());
	}

	@Test
	void reusingABookingIdForADifferentRequestIsAConflict() {
		Reservation existing = Reservation.hold(BOOKING, stock, 2, NOW.plusSeconds(600), NOW);
		when(reservations.findByBookingId(BOOKING)).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> service.reserve(new ReserveCommand(BOOKING, "event-1", "pista", 5)))
				.isInstanceOf(InventoryConflictException.class);
	}

	@Test
	void notEnoughSeatsReportsHowManyAreLeft() {
		SectorStock almostFull = SectorStock.create("event-1", "pista", 1, NOW);
		when(reservations.findByBookingId(BOOKING)).thenReturn(Optional.empty());
		when(stocks.findByEventIdAndSectorId("event-1", "pista")).thenReturn(Optional.of(almostFull));
		when(stocks.takeSeats(almostFull.getId(), 2)).thenReturn(0);

		assertThatThrownBy(() -> service.reserve(new ReserveCommand(BOOKING, "event-1", "pista", 2)))
				.isInstanceOfSatisfying(NotEnoughSeatsException.class, ex -> assertThat(ex.getAvailable()).isEqualTo(1));
		verify(reservations, never()).save(any());
	}

	@Test
	void unknownSectorFails() {
		when(reservations.findByBookingId(BOOKING)).thenReturn(Optional.empty());
		when(stocks.findByEventIdAndSectorId("event-1", "vip")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.reserve(new ReserveCommand(BOOKING, "event-1", "vip", 1)))
				.isInstanceOf(StockNotFoundException.class);
	}

	@Test
	void moreThanTheMaximumSeatsIsRejectedBeforeTouchingTheDatabase() {
		assertThatThrownBy(() -> service.reserve(new ReserveCommand(BOOKING, "event-1", "pista", 11)))
				.isInstanceOf(TooManySeatsException.class);
		verify(reservations, never()).findByBookingId(any());
	}

	@Test
	void confirmAfterExpiryReleasesTheSeatsAndFails() {
		Reservation hold = Reservation.hold(BOOKING, stock, 2, NOW.plusSeconds(600), NOW);
		when(reservations.findByBookingIdForUpdate(BOOKING)).thenReturn(Optional.of(hold));
		clock.advance(Duration.ofMinutes(11));

		assertThatThrownBy(() -> service.confirm(BOOKING)).isInstanceOf(HoldExpiredException.class);
		assertThat(hold.getStatus()).isEqualTo(ReservationStatus.RELEASED);
		assertThat(hold.getReleaseReason()).isEqualTo(ReleaseReason.EXPIRED);
		verify(stocks).returnSeats(stock.getId(), 2);
	}

	@Test
	void releasingTwiceGivesSeatsBackOnlyOnce() {
		Reservation hold = Reservation.hold(BOOKING, stock, 2, NOW.plusSeconds(600), NOW);
		when(reservations.findByBookingIdForUpdate(BOOKING)).thenReturn(Optional.of(hold));

		service.release(BOOKING, ReleaseReason.COMPENSATION);
		service.release(BOOKING, ReleaseReason.COMPENSATION);

		verify(stocks, times(1)).returnSeats(stock.getId(), 2);
	}

	@Test
	void releasingABookingThatNeverReservedIsANoOp() {
		when(reservations.findByBookingIdForUpdate(BOOKING)).thenReturn(Optional.empty());

		assertThat(service.release(BOOKING, ReleaseReason.COMPENSATION)).isEmpty();
		verify(stocks, never()).returnSeats(any(), anyInt());
	}
}
