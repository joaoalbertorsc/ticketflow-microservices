package com.ticketflow.catalog.domain;

/** Copy of the venue data an event needs to be displayed, taken when the event is saved. */
public record VenueSnapshot(String venueId, String name, String city) {
}
