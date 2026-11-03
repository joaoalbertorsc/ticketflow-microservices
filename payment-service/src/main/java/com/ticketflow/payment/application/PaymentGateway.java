package com.ticketflow.payment.application;

import com.ticketflow.payment.domain.DeclineReason;

import java.math.BigDecimal;

/**
 * The card processor, seen from the application. Today a simulator; a real provider would be
 * another implementation and nothing else would change.
 */
public interface PaymentGateway {

	GatewayResult charge(BigDecimal amount, String currency, String cardToken);

	/** A decision from the processor: approved (with its reference) or declined (with a reason). */
	record GatewayResult(boolean approved, String reference, DeclineReason declineReason) {

		public static GatewayResult approved(String reference) {
			return new GatewayResult(true, reference, null);
		}

		public static GatewayResult declined(DeclineReason reason) {
			return new GatewayResult(false, null, reason);
		}
	}
}
