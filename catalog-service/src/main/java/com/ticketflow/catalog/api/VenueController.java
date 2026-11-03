package com.ticketflow.catalog.api;

import com.ticketflow.catalog.api.dto.VenueRequest;
import com.ticketflow.catalog.api.dto.VenueResponse;
import com.ticketflow.catalog.application.VenueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/venues")
public class VenueController {

	private final VenueService venues;

	public VenueController(VenueService venues) {
		this.venues = venues;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMIN')")
	public VenueResponse create(@Valid @RequestBody VenueRequest request) {
		return VenueResponse.from(venues.create(request.name(), request.city(), request.address()));
	}

	@GetMapping
	public List<VenueResponse> list() {
		return venues.list().stream().map(VenueResponse::from).toList();
	}
}
