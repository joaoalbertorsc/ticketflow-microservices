package com.ticketflow.booking.application;

import com.ticketflow.booking.domain.Booking;
import com.ticketflow.booking.domain.BookingRepository;
import com.ticketflow.booking.domain.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Just enough of a repository for the saga's unit tests; the real queries are tested against PostgreSQL. */
class InMemoryBookingRepository implements BookingRepository {

	private final Map<UUID, Booking> rows = new ConcurrentHashMap<>();

	@Override
	public Booking save(Booking booking) {
		rows.put(booking.getId(), booking);
		return booking;
	}

	@Override
	public Optional<Booking> findById(UUID id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public Optional<Booking> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
		return rows.values().stream()
				.filter(b -> b.getUserId().equals(userId) && Objects.equals(b.getIdempotencyKey(), idempotencyKey))
				.findFirst();
	}

	int count() {
		return rows.size();
	}

	@Override
	public Page<Booking> findByUserId(UUID userId, Pageable pageable) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Page<Booking> findByStatus(BookingStatus status, Pageable pageable) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Page<Booking> findAll(Pageable pageable) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<UUID> findUnfinishedUndo(int limit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<UUID> findStuckSagas(Instant before, int limit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<UUID> findPendingNotifications(int limit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Optional<Booking> lockIfFree(UUID id) {
		throw new UnsupportedOperationException();
	}
}
