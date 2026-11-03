package com.ticketflow.booking.application;

import com.ticketflow.booking.application.BookingExceptions.BookingNotFoundException;
import com.ticketflow.booking.domain.Booking;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/** The reverse saga: CONFIRMED → CANCELLING → (refund, release, notify) → CANCELLED. */
@Service
public class CancellationService {

	private final BookingStore store;
	private final Compensations compensations;
	private final Clock clock;

	public CancellationService(BookingStore store, Compensations compensations, Clock clock) {
		this.store = store;
		this.compensations = compensations;
		this.clock = clock;
	}

	/** Only the owner can cancel; for anyone else the booking doesn't exist. */
	public void cancel(UUID bookingId, UUID userId) {
		Booking booking = store.get(bookingId);
		if (!booking.isOwnedBy(userId)) {
			throw new BookingNotFoundException(bookingId);
		}
		store.update(bookingId, b -> b.startCancellation(clock.instant())); // 409 if not cancellable
		compensations.undo(bookingId); // if refund/release can't run now, the recovery job finishes it
	}
}
