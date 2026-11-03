package com.ticketflow.payment.api.dto;

import com.ticketflow.payment.application.ChaosMonkey.ChaosSettings;
import com.ticketflow.payment.application.PaymentService.ChargeCommand;
import com.ticketflow.payment.domain.DeclineReason;
import com.ticketflow.payment.domain.Payment;
import com.ticketflow.payment.domain.PaymentStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request and response bodies of the payment API. */
public final class Dtos {

	private Dtos() {
	}

	public record ChargeRequest(
			@NotNull UUID bookingId,
			@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
			@NotBlank @Pattern(regexp = "BRL", message = "only BRL is supported") String currency,
			@NotBlank @Size(max = 64) String cardToken) {

		public ChargeCommand toCommand(UUID userId) {
			return new ChargeCommand(bookingId, userId, amount, currency, cardToken);
		}
	}

	/** The card token is never returned. */
	public record PaymentResponse(
			UUID paymentId,
			UUID bookingId,
			BigDecimal amount,
			String currency,
			PaymentStatus status,
			DeclineReason declineReason,
			String gatewayReference,
			Instant createdAt,
			Instant refundedAt) {

		public static PaymentResponse from(Payment payment) {
			return new PaymentResponse(payment.getId(), payment.getBookingId(), payment.getAmount(),
					payment.getCurrency(), payment.getStatus(), payment.getDeclineReason(),
					payment.getGatewayReference(), payment.getCreatedAt(), payment.getRefundedAt());
		}
	}

	public record ChaosRequest(
			@NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double failureRate,
			@NotNull @Min(0) @Max(10_000) Long latencyMs) {

		public ChaosSettings toSettings() {
			return new ChaosSettings(failureRate, latencyMs);
		}
	}

	public record ChaosResponse(double failureRate, long latencyMs, boolean enabled) {

		public static ChaosResponse from(ChaosSettings settings) {
			return new ChaosResponse(settings.failureRate(), settings.latencyMs(), settings.enabled());
		}
	}
}
