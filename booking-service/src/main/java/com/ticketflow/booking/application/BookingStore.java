package com.ticketflow.booking.application;

import com.ticketflow.booking.domain.Booking;
import com.ticketflow.booking.domain.BookingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Commits each saga transition in its own short transaction: load, change, commit. Never holds a
 * transaction open during an HTTP call, and after a crash the database says exactly how far the
 * saga got. Inside an outer transaction (the recovery job's) it joins it instead.
 */
@Component
public class BookingStore {

	private final BookingRepository bookings;
	private final TransactionTemplate transaction;

	public BookingStore(BookingRepository bookings, PlatformTransactionManager transactionManager) {
		this.bookings = bookings;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public Booking insert(Booking booking) {
		return transaction.execute(status -> bookings.save(booking));
	}

	/** Applies {@code change} to the current state of the booking and commits it. */
	public Booking update(UUID bookingId, Consumer<Booking> change) {
		return transaction.execute(status -> {
			Booking booking = load(bookingId);
			change.accept(booking);
			return booking;
		});
	}

	public Booking get(UUID bookingId) {
		return transaction.execute(status -> load(bookingId));
	}

	private Booking load(UUID bookingId) {
		return bookings.findById(bookingId)
				.orElseThrow(() -> new BookingExceptions.BookingNotFoundException(bookingId));
	}
}
