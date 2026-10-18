package com.ticketflow.booking.domain;

public enum StepOutcome {
	SUCCEEDED,
	/** The service answered "no" (sold out, declined...). */
	FAILED,
	/** The service was unavailable; the circuit breaker's fallback decided what to do. */
	FALLBACK
}
