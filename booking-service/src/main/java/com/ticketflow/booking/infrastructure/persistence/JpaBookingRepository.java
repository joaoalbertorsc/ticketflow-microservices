package com.ticketflow.booking.infrastructure.persistence;

import com.ticketflow.booking.domain.Booking;
import com.ticketflow.booking.domain.BookingRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The recovery job lists candidates without locking, then locks each booking (skipping any that
 * another transaction holds) in the transaction that handles it.
 */
interface JpaBookingRepository extends JpaRepository<Booking, UUID>, BookingRepository {

	@Override
	@Query(value = """
			SELECT id FROM bookings
			 WHERE status IN ('COMPENSATING', 'CANCELLING')
			 ORDER BY updated_at
			 LIMIT :limit
			""", nativeQuery = true)
	List<UUID> findUnfinishedUndo(@Param("limit") int limit);

	@Override
	@Query(value = """
			SELECT id FROM bookings
			 WHERE status IN ('PENDING', 'SEATS_RESERVED', 'PAID') AND updated_at < :before
			 ORDER BY updated_at
			 LIMIT :limit
			""", nativeQuery = true)
	List<UUID> findStuckSagas(@Param("before") Instant before, @Param("limit") int limit);

	@Override
	@Query(value = """
			SELECT id FROM bookings
			 WHERE notification_status = 'PENDING'
			 ORDER BY updated_at
			 LIMIT :limit
			""", nativeQuery = true)
	List<UUID> findPendingNotifications(@Param("limit") int limit);

	@Override
	@Query(value = "SELECT * FROM bookings WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
	Optional<Booking> lockIfFree(@Param("id") UUID id);
}
