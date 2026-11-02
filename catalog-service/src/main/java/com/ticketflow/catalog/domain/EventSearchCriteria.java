package com.ticketflow.catalog.domain;

import java.time.Instant;

/** Filters for listing events. A {@code null} field means "no filter". */
public record EventSearchCriteria(EventStatus status, EventType type, String city, Instant from, Instant to) {
}
