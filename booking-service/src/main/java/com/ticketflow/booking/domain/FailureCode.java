package com.ticketflow.booking.domain;

/** Why a booking failed. Business codes answer HTTP 409; SERVICE_UNAVAILABLE answers 503. */
public enum FailureCode {
	USER_NOT_FOUND,
	EVENT_NOT_AVAILABLE,
	SOLD_OUT,
	PAYMENT_DECLINED,
	HOLD_EXPIRED,
	SERVICE_UNAVAILABLE,
	/** The service stopped mid-saga; the recovery job undid the steps already done. */
	SAGA_INTERRUPTED;

	public boolean isTechnical() {
		return this == SERVICE_UNAVAILABLE || this == SAGA_INTERRUPTED;
	}
}
