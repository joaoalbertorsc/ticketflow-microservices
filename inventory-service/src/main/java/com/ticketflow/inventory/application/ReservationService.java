package com.ticketflow.inventory.application;

import com.ticketflow.inventory.application.InventoryExceptions.HoldExpiredException;
import com.ticketflow.inventory.application.InventoryExceptions.NotEnoughSeatsException;
import com.ticketflow.inventory.application.InventoryExceptions.ReservationNotFoundException;
import com.ticketflow.inventory.application.InventoryExceptions.StockNotFoundException;
import com.ticketflow.inventory.application.InventoryExceptions.TooManySeatsException;
import com.ticketflow.inventory.domain.InventoryConflictException;
import com.ticketflow.inventory.domain.ReleaseReason;
import com.ticketflow.inventory.domain.Reservation;
import com.ticketflow.inventory.domain.ReservationRepository;
import com.ticketflow.inventory.domain.SectorStock;
import com.ticketflow.inventory.domain.StockRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The reservation steps of the booking saga. Every operation is keyed by the booking id and is
 * safe to repeat: the orchestrator can retry after a timeout without double-booking anything.
 */
@Service
@EnableConfigurationProperties(InventoryProperties.class)
public class ReservationService {

	private final StockRepository stocks;
	private final ReservationRepository reservations;
	private final InventoryProperties properties;
	private final Clock clock;

	public ReservationService(StockRepository stocks, ReservationRepository reservations,
			InventoryProperties properties, Clock clock) {
		this.stocks = stocks;
		this.reservations = reservations;
		this.properties = properties;
		this.clock = clock;
	}

	public record ReserveCommand(UUID bookingId, String eventId, String sectorId, int quantity) {
	}

	/** @param created {@code false} when this booking had already reserved (a repeated call) */
	public record ReserveResult(Reservation reservation, boolean created) {
	}

	@Transactional
	public ReserveResult reserve(ReserveCommand command) {
		if (command.quantity() > properties.maxSeatsPerBooking()) {
			throw new TooManySeatsException(command.quantity(), properties.maxSeatsPerBooking());
		}
		Optional<Reservation> existing = reservations.findByBookingId(command.bookingId());
		if (existing.isPresent()) {
			if (!existing.get().isSameRequest(command.eventId(), command.sectorId(), command.quantity())) {
				throw new InventoryConflictException("Booking " + command.bookingId()
						+ " already has a different reservation");
			}
			return new ReserveResult(existing.get(), false);
		}

		SectorStock stock = findStock(command.eventId(), command.sectorId());
		if (stocks.takeSeats(stock.getId(), command.quantity()) == 0) {
			int available = findStock(command.eventId(), command.sectorId()).getAvailableSeats();
			throw new NotEnoughSeatsException(command.quantity(), available);
		}
		Instant now = clock.instant();
		Reservation hold = Reservation.hold(command.bookingId(), stock, command.quantity(),
				now.plus(properties.holdDuration()), now);
		return new ReserveResult(reservations.save(hold), true);
	}

	/**
	 * Confirms the hold. If it already expired, the seats are released (and that is committed) and
	 * the caller gets {@link HoldExpiredException}: the orchestrator must then refund the payment.
	 */
	@Transactional(noRollbackFor = HoldExpiredException.class)
	public Reservation confirm(UUID bookingId) {
		Reservation reservation = reservations.findByBookingIdForUpdate(bookingId)
				.orElseThrow(() -> new ReservationNotFoundException(bookingId));
		Instant now = clock.instant();
		if (reservation.isExpiredHold(now)) {
			releaseAndReturnSeats(reservation, ReleaseReason.EXPIRED, now);
			throw new HoldExpiredException(bookingId);
		}
		reservation.confirm(now);
		return reservation;
	}

	/** @return the released reservation, or empty if this booking never reserved (nothing to undo) */
	@Transactional
	public Optional<Reservation> release(UUID bookingId, ReleaseReason reason) {
		Optional<Reservation> reservation = reservations.findByBookingIdForUpdate(bookingId);
		reservation.ifPresent(found -> releaseAndReturnSeats(found, reason, clock.instant()));
		return reservation;
	}

	@Transactional(readOnly = true)
	public Reservation get(UUID bookingId) {
		return reservations.findByBookingId(bookingId).orElseThrow(() -> new ReservationNotFoundException(bookingId));
	}

	/**
	 * Releases one batch of expired holds in a single transaction.
	 *
	 * @return how many holds were released
	 */
	@Transactional
	public int releaseExpiredHolds() {
		Instant now = clock.instant();
		List<Reservation> expired = reservations.lockExpiredHolds(now, properties.expiryBatchSize());
		expired.forEach(reservation -> releaseAndReturnSeats(reservation, ReleaseReason.EXPIRED, now));
		return expired.size();
	}

	private void releaseAndReturnSeats(Reservation reservation, ReleaseReason reason, Instant now) {
		// Change the reservation first: returnSeats flushes the persistence context before running.
		if (reservation.release(reason, now)) {
			stocks.returnSeats(reservation.getStockId(), reservation.getQuantity());
		}
	}

	private SectorStock findStock(String eventId, String sectorId) {
		return stocks.findByEventIdAndSectorId(eventId, sectorId)
				.orElseThrow(() -> new StockNotFoundException(eventId, sectorId));
	}
}
