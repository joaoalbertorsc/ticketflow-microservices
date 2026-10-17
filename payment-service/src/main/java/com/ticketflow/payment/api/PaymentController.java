package com.ticketflow.payment.api;

import com.ticketflow.payment.api.dto.Dtos.PaymentResponse;
import com.ticketflow.payment.application.PaymentExceptions.PaymentNotFoundException;
import com.ticketflow.payment.application.PaymentService;
import com.ticketflow.payment.domain.Payment;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

	private final PaymentService payments;

	public PaymentController(PaymentService payments) {
		this.payments = payments;
	}

	/** Owner or admin. Someone else's payment answers 404, so payment existence can't be probed. */
	@GetMapping("/booking/{bookingId}")
	public PaymentResponse get(@PathVariable UUID bookingId, @AuthenticationPrincipal Jwt jwt) {
		Payment payment = payments.get(bookingId);
		if (!CurrentUser.isAdmin(jwt) && !payment.isOwnedBy(CurrentUser.id(jwt))) {
			throw new PaymentNotFoundException(bookingId);
		}
		return PaymentResponse.from(payment);
	}
}
