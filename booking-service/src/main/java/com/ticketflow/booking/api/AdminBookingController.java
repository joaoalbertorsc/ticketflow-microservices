package com.ticketflow.booking.api;

import com.ticketflow.booking.api.dto.Dtos.PageResponse;
import com.ticketflow.booking.application.BookingQueries;
import com.ticketflow.booking.application.BookingQueries.BookingView;
import com.ticketflow.booking.domain.BookingStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminBookingController {

	private final BookingQueries queries;

	public AdminBookingController(BookingQueries queries) {
		this.queries = queries;
	}

	@GetMapping("/api/admin/bookings")
	@PreAuthorize("hasRole('ADMIN')")
	public PageResponse<BookingView> search(@RequestParam(required = false) BookingStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(queries.search(status, page, size));
	}
}
