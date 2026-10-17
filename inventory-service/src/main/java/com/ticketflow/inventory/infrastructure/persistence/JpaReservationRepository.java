package com.ticketflow.inventory.infrastructure.persistence;

import com.ticketflow.inventory.domain.Reservation;
import com.ticketflow.inventory.domain.ReservationRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface JpaReservationRepository extends JpaRepository<Reservation, UUID>, ReservationRepository {

	/** SELECT … FOR UPDATE: a confirm and a release of the same booking run one after the other. */
	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reservation r where r.bookingId = :bookingId")
	Optional<Reservation> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);

	/** SKIP LOCKED: rows a confirm (or another job run) is working on are left alone, not waited for. */
	@Override
	@Query(value = """
			SELECT * FROM reservations
			 WHERE status = 'HELD' AND expires_at <= :now
			 ORDER BY expires_at
			 LIMIT :limit
			   FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<Reservation> lockExpiredHolds(@Param("now") Instant now, @Param("limit") int limit);
}
