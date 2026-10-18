package com.ticketflow.booking.infrastructure.client;

import com.ticketflow.booking.application.port.Ports.Charge;
import com.ticketflow.booking.application.port.Ports.Payments;
import com.ticketflow.booking.application.port.Ports.Undo;
import com.ticketflow.booking.infrastructure.security.ServiceTokenIssuer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * payment-service: charge and refund, keyed by booking id. A declined card arrives as 201 with
 * status DECLINED, so it is a normal answer here and never trips the circuit breaker.
 */
@Component
public class PaymentClient extends DownstreamClient implements Payments {

	/** Local copies of payment-service's bodies (ADR 0005). */
	record ChargeRequest(UUID bookingId, BigDecimal amount, String currency, String cardToken) {
	}

	record PaymentResponse(UUID paymentId, String status, String declineReason) {
	}

	public PaymentClient(RestClient.Builder builder, DownstreamProperties properties, ServiceTokenIssuer tokens) {
		super(builder, properties.paymentUrl(), properties, tokens, "payment-service");
	}

	@Override
	@CircuitBreaker(name = "paymentService", fallbackMethod = "chargeFallback")
	public Charge charge(UUID onBehalfOf, UUID bookingId, BigDecimal amount, String currency, String cardToken) {
		return http.post().uri("/internal/payments").headers(onBehalfOf(onBehalfOf))
				.body(new ChargeRequest(bookingId, amount, currency, cardToken))
				.exchange((request, response) -> {
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw unexpected(response.getStatusCode()); // 503 from chaos mode lands here
					}
					PaymentResponse payment = response.bodyTo(PaymentResponse.class);
					return switch (payment.status()) {
						case "APPROVED" -> Charge.approved(payment.paymentId());
						case "DECLINED" -> Charge.declined(payment.paymentId(), "Card declined: " + payment.declineReason());
						default -> Charge.voided(); // VOIDED (or REFUNDED on a late retry): money is not held
					};
				});
	}

	/**
	 * Fallback: we don't know whether the card was charged. The saga refunds by booking id, which
	 * also voids a charge still in flight, then releases the seats.
	 */
	private Charge chargeFallback(UUID onBehalfOf, UUID bookingId, BigDecimal amount, String currency,
			String cardToken, Throwable cause) {
		return Charge.unavailable(unavailable("charge", cause));
	}

	/**
	 * A breaker of its own: when charges fail and open {@code paymentService}, refunds (the
	 * compensation) must still go through if they work.
	 */
	@Override
	@CircuitBreaker(name = "paymentRefunds", fallbackMethod = "refundFallback")
	public Undo refund(UUID onBehalfOf, UUID bookingId) {
		return http.post().uri("/internal/payments/booking/{bookingId}/refund", bookingId)
				.headers(onBehalfOf(onBehalfOf))
				.exchange((request, response) -> {
					if (response.getStatusCode().is2xxSuccessful()) {
						PaymentResponse payment = response.bodyTo(PaymentResponse.class);
						return Undo.ok("payment " + payment.status());
					}
					throw unexpected(response.getStatusCode());
				});
	}

	/** Fallback: the booking stays COMPENSATING / CANCELLING and the recovery job retries. */
	private Undo refundFallback(UUID onBehalfOf, UUID bookingId, Throwable cause) {
		return Undo.unavailable(unavailable("refund", cause));
	}
}
