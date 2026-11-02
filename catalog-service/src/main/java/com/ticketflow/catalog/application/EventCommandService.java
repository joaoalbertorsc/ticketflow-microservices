package com.ticketflow.catalog.application;

import com.ticketflow.catalog.application.NotFoundExceptions.EventNotFoundException;
import com.ticketflow.catalog.application.NotFoundExceptions.VenueNotFoundException;
import com.ticketflow.catalog.domain.Event;
import com.ticketflow.catalog.domain.EventRepository;
import com.ticketflow.catalog.domain.Venue;
import com.ticketflow.catalog.domain.VenueRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Write side of the catalog. Every change to an existing event evicts its cache entries, so a
 * reader never sees stale data once the write has finished.
 */
@Service
public class EventCommandService {

	private final EventRepository events;
	private final VenueRepository venues;
	private final Clock clock;

	public EventCommandService(EventRepository events, VenueRepository venues, Clock clock) {
		this.events = events;
		this.venues = venues;
		this.clock = clock;
	}

	public EventView create(EventCommand command) {
		return EventView.from(events.save(Event.draft(toData(command), clock.instant())));
	}

	@Caching(evict = {
			@CacheEvict(cacheNames = CacheNames.EVENTS, key = "#id"),
			@CacheEvict(cacheNames = CacheNames.EVENT_SECTORS, allEntries = true)
	})
	public EventView edit(String id, EventCommand command) {
		Event event = load(id);
		event.edit(toData(command), clock.instant());
		return EventView.from(events.save(event));
	}

	@Caching(evict = {
			@CacheEvict(cacheNames = CacheNames.EVENTS, key = "#id"),
			@CacheEvict(cacheNames = CacheNames.EVENT_SECTORS, allEntries = true)
	})
	public EventView publish(String id) {
		Event event = load(id);
		event.publish(clock.instant());
		return EventView.from(events.save(event));
	}

	@Caching(evict = {
			@CacheEvict(cacheNames = CacheNames.EVENTS, key = "#id"),
			@CacheEvict(cacheNames = CacheNames.EVENT_SECTORS, allEntries = true)
	})
	public EventView cancel(String id) {
		Event event = load(id);
		event.cancel(clock.instant());
		return EventView.from(events.save(event));
	}

	private Event load(String id) {
		return events.findById(id).orElseThrow(() -> new EventNotFoundException(id));
	}

	private Event.EventData toData(EventCommand command) {
		Venue venue = venues.findById(command.venueId())
				.orElseThrow(() -> new VenueNotFoundException(command.venueId()));
		return new Event.EventData(command.name(), command.description(), command.startsAt(), venue.snapshot(),
				command.sectors(), command.details());
	}
}
