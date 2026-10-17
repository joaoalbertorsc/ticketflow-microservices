package com.ticketflow.catalog.api;

import com.ticketflow.catalog.api.dto.EventRequest;
import com.ticketflow.catalog.application.EventCommandService;
import com.ticketflow.catalog.application.EventQueryService;
import com.ticketflow.catalog.application.EventView;
import com.ticketflow.catalog.application.PageResponse;
import com.ticketflow.catalog.domain.EventStatus;
import com.ticketflow.catalog.domain.EventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Event management. Every endpoint requires the ADMIN role. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminEventController {

	private final EventCommandService commands;
	private final EventQueryService queries;

	public AdminEventController(EventCommandService commands, EventQueryService queries) {
		this.commands = commands;
		this.queries = queries;
	}

	@PostMapping("/api/events")
	@ResponseStatus(HttpStatus.CREATED)
	public EventView create(@Valid @RequestBody EventRequest request) {
		return commands.create(request.toCommand());
	}

	@PutMapping("/api/events/{id}")
	public EventView edit(@PathVariable String id, @Valid @RequestBody EventRequest request) {
		return commands.edit(id, request.toCommand());
	}

	@PostMapping("/api/events/{id}/publish")
	public EventView publish(@PathVariable String id) {
		return commands.publish(id);
	}

	@PostMapping("/api/events/{id}/cancel")
	public EventView cancel(@PathVariable String id) {
		return commands.cancel(id);
	}

	@GetMapping("/api/admin/events")
	public PageResponse<EventView> searchAll(
			@RequestParam(required = false) EventStatus status,
			@RequestParam(required = false) EventType type,
			@RequestParam(required = false) String city,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return queries.searchAll(status, type, city, PageRequest.of(page, size));
	}

	@GetMapping("/api/admin/events/{id}")
	public EventView get(@PathVariable String id) {
		return queries.getAnyEvent(id);
	}
}
