package com.ticketflow.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final UUID USER = UUID.randomUUID();

	private static Payment approved() {
		return Payment.approved(UUID.randomUUID(), USER, new BigDecimal("690.00"), "BRL", "tok_ok", "sim_1", NOW);
	}

	@Test
	void approvedPaymentIsRefundedOnce() {
		Payment payment = approved();

		assertThat(payment.refund(NOW)).isTrue();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
		assertThat(payment.getRefundedAt()).isEqualTo(NOW);
		assertThat(payment.refund(NOW.plusSeconds(5))).isFalse();
		assertThat(payment.getRefundedAt()).isEqualTo(NOW);
	}

	@Test
	void declinedAndVoidedPaymentsHaveNothingToRefund() {
		Payment declined = Payment.declined(UUID.randomUUID(), USER, BigDecimal.TEN, "BRL", "tok_declined",
				DeclineReason.INSUFFICIENT_FUNDS, NOW);
		Payment voided = Payment.voided(UUID.randomUUID(), USER, NOW);

		assertThat(declined.refund(NOW)).isFalse();
		assertThat(declined.getStatus()).isEqualTo(PaymentStatus.DECLINED);
		assertThat(voided.refund(NOW)).isFalse();
		assertThat(voided.getStatus()).isEqualTo(PaymentStatus.VOIDED);
	}

	@Test
	void sameChargeComparesAmountsByValueNotScale() {
		Payment payment = approved();

		assertThat(payment.isSameCharge(new BigDecimal("690"))).isTrue();
		assertThat(payment.isSameCharge(new BigDecimal("690.01"))).isFalse();
		assertThat(Payment.voided(UUID.randomUUID(), USER, NOW).isSameCharge(BigDecimal.ONE)).isFalse();
	}

	@Test
	void ownership() {
		assertThat(approved().isOwnedBy(USER)).isTrue();
		assertThat(approved().isOwnedBy(UUID.randomUUID())).isFalse();
	}
}
