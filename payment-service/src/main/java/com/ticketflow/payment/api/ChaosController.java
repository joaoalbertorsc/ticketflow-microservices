package com.ticketflow.payment.api;

import com.ticketflow.payment.api.dto.Dtos.ChaosRequest;
import com.ticketflow.payment.api.dto.Dtos.ChaosResponse;
import com.ticketflow.payment.application.ChaosMonkey;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Turns fault injection on and off, to demonstrate the circuit breaker in booking-service. */
@RestController
@RequestMapping("/api/payments/admin/chaos")
@PreAuthorize("hasRole('ADMIN')")
public class ChaosController {

	private final ChaosMonkey chaos;

	public ChaosController(ChaosMonkey chaos) {
		this.chaos = chaos;
	}

	@GetMapping
	public ChaosResponse get() {
		return ChaosResponse.from(chaos.current());
	}

	@PutMapping
	public ChaosResponse set(@Valid @RequestBody ChaosRequest request) {
		chaos.set(request.toSettings());
		return ChaosResponse.from(chaos.current());
	}

	@DeleteMapping
	public ChaosResponse reset() {
		chaos.reset();
		return ChaosResponse.from(chaos.current());
	}
}
