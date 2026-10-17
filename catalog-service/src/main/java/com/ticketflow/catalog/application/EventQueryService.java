package com.ticketflow.catalog.application;

import com.ticketflow.catalog.application.NotFoundExceptions.EventNotFoundException;
import com.ticketflow.catalog.application.NotFoundExceptions.SectorNotFoundException;
import com.ticketflow.catalog.domain.Event;
import com.ticketflow.catalog.domain.EventRepository;
import com.ticketflow.catalog.domain.EventSearchCriteria;
import com.ticketflow.catalog.domain.EventStatus;
import com.ticketflow.catalog.domain.EventType;
import com.ticketflow.catalog.domain.Sector;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/** Read side of the catalog. Single-item lookups are cached; writes live in {@link EventCommandService}. */
@Service
public class EventQueryService {

	private final EventRepository events;
	private final Clock clock;

	public EventQueryService(EventRepository events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	/** Public view: drafts and cancelled events are reported as not found. */
	@Cacheable(cacheNames = CacheNames.EVENTS, key = "#id")
	public EventView getPublishedEvent(String id) {
		Event event = events.findById(id)
				.filter(found -> found.getStatus() == EventStatus.PUBLISHED)
				.orElseThrow(() -> new EventNotFoundException(id));
		return EventView.from(event);
	}

	/** Public listing: published events; when no start date is given, only upcoming ones. */
	public PageResponse<EventView> searchPublished(EventType type, String city, Instant from, Instant to,
			Pageable pageable) {
		Instant effectiveFrom = from != null ? from : clock.instant();
		EventSearchCriteria criteria = new EventSearchCriteria(EventStatus.PUBLISHED, type, city, effectiveFrom, to);
		return PageResponse.from(events.search(criteria, pageable), EventView::from);
	}

	/** Admin listing: any status, no implicit date filter. */
	public PageResponse<EventView> searchAll(EventStatus status, EventType type, String city, Pageable pageable) {
		EventSearchCriteria criteria = new EventSearchCriteria(status, type, city, null, null);
		return PageResponse.from(events.search(criteria, pageable), EventView::from);
	}

	/** Admin view of a single event in any status (not cached: drafts change often). */
	public EventView getAnyEvent(String id) {
		return events.findById(id).map(EventView::from).orElseThrow(() -> new EventNotFoundException(id));
	}

	@Cacheable(cacheNames = CacheNames.EVENT_SECTORS, key = "#eventId + ':' + #sectorId")
	public SaleSnapshot getSaleSnapshot(String eventId, String sectorId) {
		Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
		Sector sector = event.findSector(sectorId).orElseThrow(() -> new SectorNotFoundException(eventId, sectorId));
		return SaleSnapshot.of(event, sector);
	}
}
