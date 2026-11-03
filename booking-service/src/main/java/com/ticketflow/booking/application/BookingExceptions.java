package com.ticketflow.booking.application;

import java.util.UUID;

public final class BookingExceptions {

	private BookingExceptions() {
	}

	/** 404 — also used for someone else's booking, so booking ids can't be probed. */
	public static class BookingNotFoundException extends RuntimeException {

		public BookingNotFoundException(UUID bookingId) {
			super("Booking not found: " + bookingId);
		}
	}
}
