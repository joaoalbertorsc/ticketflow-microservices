package com.ticketflow.payment.infrastructure.gateway;

import com.ticketflow.payment.application.PaymentGateway;
import com.ticketflow.payment.domain.DeclineReason;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Deterministic stand-in for a card processor, so demos and tests are repeatable:
 * {@code tok_declined} and {@code tok_fraud} are declined, amounts above the limit are declined,
 * every other token is approved.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

	public static final String DECLINED_TOKEN = "tok_declined";
	public static final String FRAUD_TOKEN = "tok_fraud";

	private final BigDecimal limit;

	public SimulatedPaymentGateway(@Value("${ticketflow.payment.limit:50000.00}") BigDecimal limit) {
		this.limit = limit;
	}

	@Override
	public GatewayResult charge(BigDecimal amount, String currency, String cardToken) {
		if (DECLINED_TOKEN.equals(cardToken)) {
			return GatewayResult.declined(DeclineReason.INSUFFICIENT_FUNDS);
		}
		if (FRAUD_TOKEN.equals(cardToken)) {
			return GatewayResult.declined(DeclineReason.SUSPECTED_FRAUD);
		}
		if (amount.compareTo(limit) > 0) {
			return GatewayResult.declined(DeclineReason.OVER_LIMIT);
		}
		return GatewayResult.approved("sim_" + UUID.randomUUID());
	}
}
