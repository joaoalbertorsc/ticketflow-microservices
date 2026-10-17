package com.ticketflow.payment.domain;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

	Payment save(Payment payment);

	Optional<Payment> findByBookingId(UUID bookingId);

	/**
	 * Serializes every charge and refund of one booking until the current transaction ends, even
	 * before any row exists for it (a row lock can't do that).
	 */
	void lockBooking(UUID bookingId);
}
