package com.ticketflow.payment.application;

import com.ticketflow.payment.application.PaymentExceptions.ChaosFailureException;
import com.ticketflow.payment.application.PaymentExceptions.ConflictingChargeException;
import com.ticketflow.payment.application.PaymentGateway.GatewayResult;
import com.ticketflow.payment.application.PaymentService.ChargeCommand;
import com.ticketflow.payment.application.PaymentService.ChargeResult;
import com.ticketflow.payment.domain.DeclineReason;
import com.ticketflow.payment.domain.Payment;
import com.ticketflow.payment.domain.PaymentRepository;
import com.ticketflow.payment.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final UUID BOOKING = UUID.randomUUID();
	private static final UUID USER = UUID.randomUUID();
	private static final BigDecimal AMOUNT = new BigDecimal("690.00");

	@Mock
	PaymentRepository payments;

	@Mock
	PaymentGateway gateway;

	@Mock
	ChaosMonkey chaos;

	@Mock
	PlatformTransactionManager transactionManager;

	PaymentService service;

	@BeforeEach
	void setUp() {
		service = new PaymentService(payments, gateway, chaos, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static ChargeCommand charge(BigDecimal amount) {
		return new ChargeCommand(BOOKING, USER, amount, "BRL", "tok_visa");
	}

	@Test
	void newChargeLocksTheBookingThenCallsTheGateway() {
		when(payments.findByBookingId(BOOKING)).thenReturn(Optional.empty());
		when(gateway.charge(AMOUNT, "BRL", "tok_visa")).thenReturn(GatewayResult.approved("sim_1"));
		when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ChargeResult result = service.charge(charge(AMOUNT));

		assertThat(result.created()).isTrue();
		assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.APPROVED);
		assertThat(result.payment().getUserId()).isEqualTo(USER);
		InOrder order = inOrder(chaos, payments, gateway);
		order.verify(chaos).beforeCharge();
		order.verify(payments).lockBooking(BOOKING);
		order.verify(gateway).charge(AMOUNT, "BRL", "tok_visa");
	}

	@Test
	void declinedCardIsRecordedNotThrown() {
		when(payments.findByBookingId(BOOKING)).thenReturn(Optional.empty());
		when(gateway.charge(any(), any(), any())).thenReturn(GatewayResult.declined(DeclineReason.INSUFFICIENT_FUNDS));
		when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Payment payment = service.charge(charge(AMOUNT)).payment();

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
		assertThat(payment.getDeclineReason()).isEqualTo(DeclineReason.INSUFFICIENT_FUNDS);
	}

	@Test
	void repeatedChargeReturnsTheExistingPaymentWithoutChaosOrGateway() {
		Payment existing = Payment.approved(BOOKING, USER, AMOUNT, "BRL", "tok_visa", "sim_1", NOW);
		when(payments.findByBookingId(BOOKING)).thenReturn(Optional.of(existing));

		ChargeResult result = service.charge(charge(new BigDecimal("690")));

		assertThat(result.created()).isFalse();
		assertThat(result.payment()).isSameAs(existing);
		verify(chaos, never()).beforeCharge();
		verify(gateway, never()).charge(any(), any(), any());
	}

	@Test
	void sameBookingWithADifferentAmountIsAConflict() {
		when(payments.findByBookingId(BOOKING))
				.thenReturn(Optional.of(Payment.approved(BOOKING, USER, AMOUNT, "BRL", "tok_visa", "sim_1", NOW)));

		assertThatThrownBy(() -> service.charge(charge(new BigDecimal("100.00"))))
				.isInstanceOf(ConflictingChargeException.class);
	}

	@Test
	void lateChargeOfAVoidedBookingIsNeverSentToTheGateway() {
		when(payments.findByBookingId(BOOKING)).thenReturn(Optional.of(Payment.voided(BOOKING, USER, NOW)));

		ChargeResult result = service.charge(charge(AMOUNT));

		assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.VOIDED);
		verify(gateway, never()).charge(any(), any(), any());
	}

	@Test
	void chargeThatLosesTheRaceUnderTheLockReturnsTheWinner() {
		Payment winner = Payment.approved(BOOKING, USER, AMOUNT, "BRL", "tok_visa", "sim_1", NOW);
		when(payments.findByBookingId(BOOKING)).thenReturn(Optional.empty(), Optional.of(winner));

		ChargeResult result = service.charge(charge(AMOUNT));

		assertThat(result.created()).isFalse();
		assertThat(result.payment()).isSameAs(winner);
		verify(gateway, never()).charge(any(), any(), any());
	}

	@Test
	void chaosFailureHappensBeforeAnythingIsWritten() {
		when(payments.findByBookingId(BOOKING)).thenReturn(Optional.empty());
		doThrow(new ChaosFailureException()).when(chaos).beforeCharge();

		assertThatThrownBy(() -> service.charge(charge(AMOUNT))).isInstanceOf(ChaosFailureException.class);
		verify(payments, never()).save(any());
		verify(gateway, never()).charge(any(), any(), any());
	}

	@Test
	void refundOfApprovedPaymentGivesTheMoneyBack() {
		Payment payment = Payment.approved(BOOKING, USER, AMOUNT, "BRL", "tok_visa", "sim_1", NOW);
		when(payments.findByBookingId(BOOKING)).thenReturn(Optional.of(payment));

		assertThat(service.refund(BOOKING, USER).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
		verify(payments).lockBooking(BOOKING);
	}

	@Test
	void refundBeforeAnyChargeWritesAVoidedTombstone() {
		when(payments.findByBookingId(BOOKING)).thenReturn(Optional.empty());
		when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Payment payment = service.refund(BOOKING, USER);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
		assertThat(payment.getBookingId()).isEqualTo(BOOKING);
	}
}
