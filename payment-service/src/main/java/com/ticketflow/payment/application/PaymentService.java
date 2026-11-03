package com.ticketflow.payment.application;

import com.ticketflow.payment.application.PaymentExceptions.ConflictingChargeException;
import com.ticketflow.payment.application.PaymentExceptions.PaymentNotFoundException;
import com.ticketflow.payment.application.PaymentGateway.GatewayResult;
import com.ticketflow.payment.domain.Payment;
import com.ticketflow.payment.domain.PaymentRepository;
import com.ticketflow.payment.domain.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Charges and refunds for the booking saga. Both are keyed by booking id, safe to repeat, and
 * serialized per booking, so the gateway is never called twice for the same booking and a
 * refund that overtakes its charge voids it.
 */
@Service
public class PaymentService {

	private final PaymentRepository payments;
	private final PaymentGateway gateway;
	private final ChaosMonkey chaos;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public PaymentService(PaymentRepository payments, PaymentGateway gateway, ChaosMonkey chaos,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.payments = payments;
		this.gateway = gateway;
		this.chaos = chaos;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	public record ChargeCommand(UUID bookingId, UUID userId, BigDecimal amount, String currency, String cardToken) {
	}

	/** @param created {@code false} when this booking already had a payment (a repeated call) */
	public record ChargeResult(Payment payment, boolean created) {
	}

	public ChargeResult charge(ChargeCommand command) {
		// Fast path for retries: no chaos, no lock, no gateway.
		Optional<Payment> existing = payments.findByBookingId(command.bookingId());
		if (existing.isPresent()) {
			return repeated(existing.get(), command);
		}

		chaos.beforeCharge(); // may sleep or fail, before anything is written

		return transaction.execute(status -> {
			payments.lockBooking(command.bookingId());
			// Re-check under the lock: a concurrent charge or refund may have won the race.
			Optional<Payment> raced = payments.findByBookingId(command.bookingId());
			if (raced.isPresent()) {
				return repeated(raced.get(), command);
			}
			GatewayResult result = gateway.charge(command.amount(), command.currency(), command.cardToken());
			Instant now = clock.instant();
			Payment payment = result.approved()
					? Payment.approved(command.bookingId(), command.userId(), command.amount(), command.currency(),
							command.cardToken(), result.reference(), now)
					: Payment.declined(command.bookingId(), command.userId(), command.amount(), command.currency(),
							command.cardToken(), result.declineReason(), now);
			return new ChargeResult(payments.save(payment), true);
		});
	}

	/**
	 * Refunds the booking's payment. If there is no payment yet, records the booking as VOIDED so a
	 * charge arriving late is refused.
	 */
	public Payment refund(UUID bookingId, UUID userId) {
		return transaction.execute(status -> {
			payments.lockBooking(bookingId);
			Optional<Payment> payment = payments.findByBookingId(bookingId);
			if (payment.isEmpty()) {
				return payments.save(Payment.voided(bookingId, userId, clock.instant()));
			}
			payment.get().refund(clock.instant());
			return payment.get();
		});
	}

	public Payment get(UUID bookingId) {
		return payments.findByBookingId(bookingId).orElseThrow(() -> new PaymentNotFoundException(bookingId));
	}

	private static ChargeResult repeated(Payment existing, ChargeCommand command) {
		if (existing.getStatus() != PaymentStatus.VOIDED && !existing.isSameCharge(command.amount())) {
			throw new ConflictingChargeException(command.bookingId());
		}
		return new ChargeResult(existing, false);
	}
}
