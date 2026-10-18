package com.ticketflow.booking.api;

import com.ticketflow.booking.api.dto.Dtos.BookRequest;
import com.ticketflow.booking.api.dto.Dtos.PageResponse;
import com.ticketflow.booking.application.BookingQueries;
import com.ticketflow.booking.application.BookingQueries.BookingView;
import com.ticketflow.booking.application.BookingSaga;
import com.ticketflow.booking.application.BookingSaga.BookResult;
import com.ticketflow.booking.application.CancellationService;
import com.ticketflow.booking.domain.BookingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

	private final BookingSaga saga;
	private final CancellationService cancellations;
	private final BookingQueries queries;

	public BookingController(BookingSaga saga, CancellationService cancellations, BookingQueries queries) {
		this.saga = saga;
		this.cancellations = cancellations;
		this.queries = queries;
	}

	/**
	 * Runs the whole saga before answering. The booking always comes back, and the HTTP status says
	 * how it ended: 201 confirmed, 409 business failure, 503 a service was unavailable.
	 */
	@PostMapping
	public ResponseEntity<BookingView> book(@Valid @RequestBody BookRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 100) String idempotencyKey,
			@AuthenticationPrincipal Jwt jwt) {
		UUID userId = CurrentUser.id(jwt);
		BookResult result = saga.book(request.toCommand(userId, idempotencyKey));
		BookingView booking = queries.get(result.bookingId(), userId, false);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(booking.id()).toUri();
		return ResponseEntity.status(statusFor(booking, result.created())).location(location).body(booking);
	}

	@GetMapping("/{id}")
	public BookingView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		return queries.get(id, CurrentUser.id(jwt), CurrentUser.isAdmin(jwt));
	}

	@GetMapping("/me")
	public PageResponse<BookingView> mine(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(queries.mine(CurrentUser.id(jwt), page, size));
	}

	/** 200 CANCELLED, or 202 CANCELLING when refund/release will be finished by the recovery job. */
	@PostMapping("/{id}/cancel")
	public ResponseEntity<BookingView> cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		UUID userId = CurrentUser.id(jwt);
		cancellations.cancel(id, userId);
		BookingView booking = queries.get(id, userId, false);
		return ResponseEntity.status(booking.status() == BookingStatus.CANCELLED ? HttpStatus.OK : HttpStatus.ACCEPTED)
				.body(booking);
	}

	static HttpStatus statusFor(BookingView booking, boolean created) {
		if (booking.status() == BookingStatus.CONFIRMED) {
			return created ? HttpStatus.CREATED : HttpStatus.OK;
		}
		if (booking.failureCode() != null && booking.failureCode().isTechnical()) {
			return HttpStatus.SERVICE_UNAVAILABLE;
		}
		if (booking.failureCode() != null) {
			return HttpStatus.CONFLICT;
		}
		return created ? HttpStatus.ACCEPTED : HttpStatus.OK; // still in progress (an idempotent retry raced the saga)
	}
}
