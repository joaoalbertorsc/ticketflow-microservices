package com.ticketflow.catalog.api;

import com.ticketflow.catalog.api.dto.SaleSnapshotResponse;
import com.ticketflow.catalog.application.EventQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/** Service-to-service endpoints. The gateway does not route {@code /internal/**}. */
@RestController
@RequestMapping("/internal/events")
public class InternalEventController {

	private final EventQueryService queries;
	private final Clock clock;

	public InternalEventController(EventQueryService queries, Clock clock) {
		this.queries = queries;
		this.clock = clock;
	}

	@GetMapping("/{eventId}/sectors/{sectorId}")
	public SaleSnapshotResponse saleSnapshot(@PathVariable String eventId, @PathVariable String sectorId) {
		return SaleSnapshotResponse.from(queries.getSaleSnapshot(eventId, sectorId), clock.instant());
	}
}
