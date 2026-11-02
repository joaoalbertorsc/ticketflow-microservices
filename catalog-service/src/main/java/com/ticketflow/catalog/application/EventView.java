package com.ticketflow.catalog.application;

import com.ticketflow.catalog.domain.Event;
import com.ticketflow.catalog.domain.EventDetails;
import com.ticketflow.catalog.domain.EventStatus;
import com.ticketflow.catalog.domain.EventType;
import com.ticketflow.catalog.domain.Sector;
import com.ticketflow.catalog.domain.VenueSnapshot;

import java.time.Instant;
import java.util.List;

/** Read model of an event: what the API returns and what is stored in the Redis cache. */
public record EventView(
		String id,
		String name,
		String description,
		EventType type,
		Instant startsAt,
		EventStatus status,
		VenueSnapshot venue,
		List<Sector> sectors,
		@PolymorphicDetails EventDetails details) {

	public static EventView from(Event event) {
		return new EventView(event.getId(), event.getName(), event.getDescription(), event.getType(),
				event.getStartsAt(), event.getStatus(), event.getVenue(), event.getSectors(), event.getDetails());
	}
}
