package com.ticketflow.payment.infrastructure.gateway;

import com.ticketflow.payment.application.PaymentGateway.GatewayResult;
import com.ticketflow.payment.domain.DeclineReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedPaymentGatewayTest {

	private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(new BigDecimal("50000.00"));

	@Test
	void anyOtherTokenIsApprovedWithAReference() {
		GatewayResult result = gateway.charge(new BigDecimal("100.00"), "BRL", "tok_visa");

		assertThat(result.approved()).isTrue();
		assertThat(result.reference()).startsWith("sim_");
	}

	@Test
	void specialTokensAreDeclined() {
		assertThat(gateway.charge(BigDecimal.TEN, "BRL", "tok_declined").declineReason())
				.isEqualTo(DeclineReason.INSUFFICIENT_FUNDS);
		assertThat(gateway.charge(BigDecimal.TEN, "BRL", "tok_fraud").declineReason())
				.isEqualTo(DeclineReason.SUSPECTED_FRAUD);
	}

	@Test
	void amountsAboveTheLimitAreDeclined() {
		assertThat(gateway.charge(new BigDecimal("50000.00"), "BRL", "tok_visa").approved()).isTrue();
		assertThat(gateway.charge(new BigDecimal("50000.01"), "BRL", "tok_visa").declineReason())
				.isEqualTo(DeclineReason.OVER_LIMIT);
	}
}
