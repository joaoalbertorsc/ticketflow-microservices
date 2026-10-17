package com.ticketflow.inventory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {

	Reservation save(Reservation reservation);

	Optional<Reservation> findByBookingId(UUID bookingId);

	/** Same as {@link #findByBookingId} but locks the row until the transaction ends. */
	Optional<Reservation> findByBookingIdForUpdate(UUID bookingId);

	/**
	 * Locks up to {@code limit} holds that expired before {@code now}, skipping rows another
	 * transaction already locked (so concurrent job runs never release the same hold twice).
	 */
	List<Reservation> lockExpiredHolds(Instant now, int limit);
}
