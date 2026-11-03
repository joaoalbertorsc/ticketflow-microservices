package com.ticketflow.payment.application;

import java.util.UUID;

/** Application errors of the payment service; each one maps to a single HTTP status. */
public final class PaymentExceptions {

	private PaymentExceptions() {
	}

	/** 404 */
	public static class PaymentNotFoundException extends RuntimeException {

		public PaymentNotFoundException(UUID bookingId) {
			super("No payment for booking " + bookingId);
		}
	}

	/** 409: the booking id was already used for a charge of a different amount. */
	public static class ConflictingChargeException extends RuntimeException {

		public ConflictingChargeException(UUID bookingId) {
			super("Booking " + bookingId + " was already charged with a different amount");
		}
	}

	/** 503: injected by chaos mode, before anything is written. A technical failure, not a decline. */
	public static class ChaosFailureException extends RuntimeException {

		public ChaosFailureException() {
			super("Payment gateway unavailable (chaos mode)");
		}
	}
}
