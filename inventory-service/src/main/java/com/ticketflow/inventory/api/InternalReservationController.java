package com.ticketflow.inventory.api;

import com.ticketflow.inventory.api.dto.Dtos.ReleaseRequestReason;
import com.ticketflow.inventory.api.dto.Dtos.ReservationResponse;
import com.ticketflow.inventory.api.dto.Dtos.ReserveRequest;
import com.ticketflow.inventory.application.ReservationService;
import com.ticketflow.inventory.application.ReservationService.ReserveResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Saga steps called by booking-service. All keyed by booking id and safe to repeat.
 * The gateway does not route {@code /internal/**}.
 */
@RestController
@RequestMapping("/internal/reservations")
public class InternalReservationController {

	private final ReservationService reservations;

	public InternalReservationController(ReservationService reservations) {
		this.reservations = reservations;
	}

	/** {@code 201} for a new hold, {@code 200} when this booking had already reserved. */
	@PostMapping
	public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveRequest request) {
		ReserveResult result = reservations.reserve(request.toCommand());
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ReservationResponse.from(result.reservation()));
	}

	@GetMapping("/booking/{bookingId}")
	public ReservationResponse get(@PathVariable UUID bookingId) {
		return ReservationResponse.from(reservations.get(bookingId));
	}

	@PostMapping("/booking/{bookingId}/confirm")
	public ReservationResponse confirm(@PathVariable UUID bookingId) {
		return ReservationResponse.from(reservations.confirm(bookingId));
	}

	/** {@code 204} when the booking never reserved: there is nothing to undo, and that's fine. */
	@PostMapping("/booking/{bookingId}/release")
	public ResponseEntity<ReservationResponse> release(@PathVariable UUID bookingId,
			@RequestParam(defaultValue = "COMPENSATION") ReleaseRequestReason reason) {
		return reservations.release(bookingId, reason.toDomain())
				.map(released -> ResponseEntity.ok(ReservationResponse.from(released)))
				.orElseGet(() -> ResponseEntity.noContent().build());
	}
}
