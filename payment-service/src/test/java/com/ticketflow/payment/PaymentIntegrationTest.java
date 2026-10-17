package com.ticketflow.payment;

import com.ticketflow.payment.application.ChaosMonkey;
import com.ticketflow.payment.application.ChaosMonkey.ChaosSettings;
import com.ticketflow.payment.application.PaymentGateway;
import com.ticketflow.payment.application.PaymentService;
import com.ticketflow.payment.application.PaymentService.ChargeCommand;
import com.ticketflow.payment.application.PaymentService.ChargeResult;
import com.ticketflow.payment.domain.DeclineReason;
import com.ticketflow.payment.domain.Payment;
import com.ticketflow.payment.domain.PaymentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PostgreSQL: constraints, the per-booking lock, and the void-before-charge rule. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PaymentIntegrationTest {

	private static final UUID USER = UUID.randomUUID();
	private static final BigDecimal AMOUNT = new BigDecimal("690.00");

	@Autowired
	PaymentService payments;

	@Autowired
	ChaosMonkey chaos;

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	PaymentGateway gateway;

	@AfterEach
	void chaosOff() {
		chaos.reset();
	}

	private ChargeResult charge(UUID bookingId, String cardToken) {
		return payments.charge(new ChargeCommand(bookingId, USER, AMOUNT, "BRL", cardToken));
	}

	private int rowsFor(UUID bookingId) {
		return jdbc.queryForObject("SELECT count(*) FROM payments WHERE booking_id = ?", Integer.class, bookingId);
	}

	@Test
	void chargeThenRefund() {
		UUID booking = UUID.randomUUID();

		Payment charged = charge(booking, "tok_visa").payment();
		assertThat(charged.getStatus()).isEqualTo(PaymentStatus.APPROVED);
		assertThat(charged.getGatewayReference()).startsWith("sim_");

		Payment refunded = payments.refund(booking, USER);
		assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
		assertThat(payments.get(booking).getRefundedAt()).isNotNull();

		// a second refund changes nothing
		assertThat(payments.refund(booking, USER).getRefundedAt()).isEqualTo(refunded.getRefundedAt());
	}

	@Test
	void repeatedChargeDoesNotCallTheGatewayAgain() {
		UUID booking = UUID.randomUUID();

		ChargeResult first = charge(booking, "tok_visa");
		ChargeResult second = charge(booking, "tok_visa");

		assertThat(first.created()).isTrue();
		assertThat(second.created()).isFalse();
		assertThat(second.payment().getId()).isEqualTo(first.payment().getId());
		verify(gateway, times(1)).charge(any(), any(), any());
		assertThat(rowsFor(booking)).isEqualTo(1);
	}

	@Test
	void declinedPaymentIsStoredAndHasNothingToRefund() {
		UUID booking = UUID.randomUUID();

		Payment declined = charge(booking, "tok_declined").payment();
		assertThat(declined.getStatus()).isEqualTo(PaymentStatus.DECLINED);
		assertThat(declined.getDeclineReason()).isEqualTo(DeclineReason.INSUFFICIENT_FUNDS);

		assertThat(payments.refund(booking, USER).getStatus()).isEqualTo(PaymentStatus.DECLINED);
	}

	@Test
	void refundBeforeChargeVoidsTheBooking() {
		UUID booking = UUID.randomUUID();

		assertThat(payments.refund(booking, USER).getStatus()).isEqualTo(PaymentStatus.VOIDED);
		ChargeResult late = charge(booking, "tok_visa");

		assertThat(late.payment().getStatus()).isEqualTo(PaymentStatus.VOIDED);
		verify(gateway, never()).charge(any(), any(), any());
	}

	/** The real timeout scenario: the refund overtakes a charge that is still in flight. */
	@Test
	void refundThatOvertakesASlowChargeStopsIt() throws Exception {
		UUID booking = UUID.randomUUID();
		chaos.set(new ChaosSettings(0, 1500)); // the charge is slow...

		CompletableFuture<ChargeResult> slowCharge = CompletableFuture.supplyAsync(() -> charge(booking, "tok_visa"));
		Thread.sleep(300); // ...the orchestrator gives up and compensates while it's still sleeping
		Payment refund = payments.refund(booking, USER);
		ChargeResult charge = slowCharge.get(10, TimeUnit.SECONDS);

		assertThat(refund.getStatus()).isEqualTo(PaymentStatus.VOIDED);
		assertThat(charge.payment().getStatus()).isEqualTo(PaymentStatus.VOIDED);
		verify(gateway, never()).charge(any(), any(), any());
		assertThat(rowsFor(booking)).isEqualTo(1);
	}

	@Test
	void chaosFailureIs503AndWritesNothing() throws Exception {
		UUID booking = UUID.randomUUID();
		chaos.set(new ChaosSettings(1.0, 0));

		mvc.perform(post("/internal/payments").with(jwt().jwt(token -> token.subject(USER.toString())))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "amount": 690.00, "currency": "BRL", "cardToken": "tok_visa"}
								""".formatted(booking)))
				.andExpect(status().isServiceUnavailable());

		assertThat(rowsFor(booking)).isZero();
		verify(gateway, never()).charge(any(), any(), any());
	}

	@Test
	void refundsAreNotAffectedByChaos() {
		UUID booking = UUID.randomUUID();
		charge(booking, "tok_visa");
		chaos.set(new ChaosSettings(1.0, 0));

		assertThat(payments.refund(booking, USER).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
	}
}
