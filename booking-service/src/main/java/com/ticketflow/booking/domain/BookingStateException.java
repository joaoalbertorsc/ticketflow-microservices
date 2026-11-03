package com.ticketflow.booking.domain;

/** The operation is not allowed in the booking's current state (HTTP 409). */
public class BookingStateException extends RuntimeException {

	public BookingStateException(String message) {
		super(message);
	}
}
