package com.ticketflow.booking.domain;

public enum BookingStatus {
	PENDING,
	SEATS_RESERVED,
	PAID,
	CONFIRMED,
	/** Something failed after seats were (maybe) held or money (maybe) taken: undoing it. */
	COMPENSATING,
	FAILED,
	/** The user cancelled; refund and release are in progress. */
	CANCELLING,
	CANCELLED;

	/** States a saga passes through; a booking stuck in one of them was interrupted. */
	public boolean isInProgress() {
		return this == PENDING || this == SEATS_RESERVED || this == PAID;
	}
}
