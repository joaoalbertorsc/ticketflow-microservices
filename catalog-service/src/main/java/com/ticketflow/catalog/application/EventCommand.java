package com.ticketflow.catalog.application;

import com.ticketflow.catalog.domain.EventDetails;
import com.ticketflow.catalog.domain.Sector;

import java.time.Instant;
import java.util.List;

/** Input for creating or editing an event. The venue is given by id and copied into the event. */
public record EventCommand(String name, String description, Instant startsAt, String venueId,
		List<Sector> sectors, EventDetails details) {
}
