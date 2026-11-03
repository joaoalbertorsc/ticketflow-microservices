package com.ticketflow.booking.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {

	Booking save(Booking booking);

	Optional<Booking> findById(UUID id);

	Optional<Booking> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

	Page<Booking> findByUserId(UUID userId, Pageable pageable);

	Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

	Page<Booking> findAll(Pageable pageable);

	/** COMPENSATING / CANCELLING bookings: an undo step still has to run. */
	List<UUID> findUnfinishedUndo(int limit);

	/** PENDING / SEATS_RESERVED / PAID bookings untouched since {@code before}: interrupted sagas. */
	List<UUID> findStuckSagas(Instant before, int limit);

	/** Bookings whose notification couldn't be sent yet. */
	List<UUID> findPendingNotifications(int limit);

	/**
	 * Locks the booking until the transaction ends, or returns empty right away if another
	 * transaction holds it (SKIP LOCKED): two job instances never work on the same booking.
	 */
	Optional<Booking> lockIfFree(UUID id);
}
