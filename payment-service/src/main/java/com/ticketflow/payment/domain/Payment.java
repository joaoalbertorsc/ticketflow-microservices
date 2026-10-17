package com.ticketflow.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The payment of one booking. Created already decided (approved, declined or voided), and the
 * only transition afterwards is APPROVED → REFUNDED.
 */
@Entity
@Table(name = "payments")
public class Payment {

	@Id
	private UUID id;

	@Column(name = "booking_id", nullable = false, unique = true)
	private UUID bookingId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(length = 3)
	private String currency;

	@Column(name = "card_token", length = 64)
	private String cardToken;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "decline_reason", length = 30)
	private DeclineReason declineReason;

	@Column(name = "gateway_reference", length = 64)
	private String gatewayReference;

	@Column(name = "refunded_at")
	private Instant refundedAt;

	@Version
	private Long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Payment() {
		// required by JPA
	}

	private static Payment base(UUID bookingId, UUID userId, PaymentStatus status, Instant now) {
		Payment payment = new Payment();
		payment.id = UUID.randomUUID();
		payment.bookingId = bookingId;
		payment.userId = userId;
		payment.status = status;
		payment.createdAt = now;
		payment.updatedAt = now;
		return payment;
	}

	private static Payment charge(UUID bookingId, UUID userId, BigDecimal amount, String currency, String cardToken,
			PaymentStatus status, Instant now) {
		Payment payment = base(bookingId, userId, status, now);
		payment.amount = amount;
		payment.currency = currency;
		payment.cardToken = cardToken;
		return payment;
	}

	public static Payment approved(UUID bookingId, UUID userId, BigDecimal amount, String currency, String cardToken,
			String gatewayReference, Instant now) {
		Payment payment = charge(bookingId, userId, amount, currency, cardToken, PaymentStatus.APPROVED, now);
		payment.gatewayReference = gatewayReference;
		return payment;
	}

	public static Payment declined(UUID bookingId, UUID userId, BigDecimal amount, String currency, String cardToken,
			DeclineReason reason, Instant now) {
		Payment payment = charge(bookingId, userId, amount, currency, cardToken, PaymentStatus.DECLINED, now);
		payment.declineReason = reason;
		return payment;
	}

	/** Tombstone written when a refund arrives before any charge, so a late charge is refused. */
	public static Payment voided(UUID bookingId, UUID userId, Instant now) {
		return base(bookingId, userId, PaymentStatus.VOIDED, now);
	}

	/** Whether a repeated charge for this booking asks for the same amount. */
	public boolean isSameCharge(BigDecimal requestedAmount) {
		return amount != null && amount.compareTo(requestedAmount) == 0;
	}

	/**
	 * Refunds an approved payment. Idempotent: anything else (already refunded, declined, voided)
	 * has nothing to give back and is left unchanged.
	 *
	 * @return {@code true} if money was given back
	 */
	public boolean refund(Instant now) {
		if (status != PaymentStatus.APPROVED) {
			return false;
		}
		status = PaymentStatus.REFUNDED;
		refundedAt = now;
		updatedAt = now;
		return true;
	}

	public boolean isOwnedBy(UUID candidate) {
		return userId.equals(candidate);
	}

	public UUID getId() {
		return id;
	}

	public UUID getBookingId() {
		return bookingId;
	}

	public UUID getUserId() {
		return userId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public DeclineReason getDeclineReason() {
		return declineReason;
	}

	public String getGatewayReference() {
		return gatewayReference;
	}

	public Instant getRefundedAt() {
		return refundedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	/** {@code null} until first saved. */
	public Long getVersion() {
		return version;
	}
}
