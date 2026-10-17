package com.ticketflow.catalog.api;

import com.ticketflow.catalog.application.EventQueryService;
import com.ticketflow.catalog.application.EventView;
import com.ticketflow.catalog.application.PageResponse;
import com.ticketflow.catalog.domain.EventType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Public browsing of published events. No token needed. */
@RestController
@RequestMapping("/api/events")
public class EventController {

	private final EventQueryService queries;

	public EventController(EventQueryService queries) {
		this.queries = queries;
	}

	@GetMapping
	public PageResponse<EventView> search(
			@RequestParam(required = false) EventType type,
			@RequestParam(required = false) String city,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return queries.searchPublished(type, city, from, to, PageRequest.of(page, size));
	}

	@GetMapping("/{id}")
	public EventView get(@PathVariable String id) {
		return queries.getPublishedEvent(id);
	}
}
