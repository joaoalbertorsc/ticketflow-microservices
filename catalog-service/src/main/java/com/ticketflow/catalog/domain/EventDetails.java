package com.ticketflow.catalog.domain;

/**
 * Type-specific data of an event. Sealed, so the set of event types is explicit; adding a type
 * means adding one record here and one {@link EventType} value.
 */
public sealed interface EventDetails permits ConcertDetails, SportsDetails, TheaterDetails {

	EventType type();
}
