package com.ticketflow.payment.domain;

public enum PaymentStatus {
	APPROVED,
	DECLINED,
	REFUNDED,
	/** A refund arrived before any charge: this booking must never be charged. */
	VOIDED
}
