package com.ticketflow.payment.api;

import com.ticketflow.payment.api.dto.Dtos.ChargeRequest;
import com.ticketflow.payment.api.dto.Dtos.PaymentResponse;
import com.ticketflow.payment.application.PaymentService;
import com.ticketflow.payment.application.PaymentService.ChargeResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Saga steps called by booking-service, which forwards the user's token: the payment is recorded
 * as belonging to that user. All keyed by booking id and safe to repeat. Not routed by the gateway.
 */
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

	private final PaymentService payments;

	public InternalPaymentController(PaymentService payments) {
		this.payments = payments;
	}

	/**
	 * {@code 201} with APPROVED or DECLINED for a new charge — a declined card is a normal answer,
	 * not an error. {@code 200} when this booking already had a payment.
	 */
	@PostMapping
	public ResponseEntity<PaymentResponse> charge(@Valid @RequestBody ChargeRequest request, @AuthenticationPrincipal Jwt jwt) {
		ChargeResult result = payments.charge(request.toCommand(CurrentUser.id(jwt)));
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(PaymentResponse.from(result.payment()));
	}

	@PostMapping("/booking/{bookingId}/refund")
	public PaymentResponse refund(@PathVariable UUID bookingId, @AuthenticationPrincipal Jwt jwt) {
		return PaymentResponse.from(payments.refund(bookingId, CurrentUser.id(jwt)));
	}

	@GetMapping("/booking/{bookingId}")
	public PaymentResponse get(@PathVariable UUID bookingId) {
		return PaymentResponse.from(payments.get(bookingId));
	}
}
