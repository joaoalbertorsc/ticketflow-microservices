package com.ticketflow.catalog.application;

import com.ticketflow.catalog.domain.Event;
import com.ticketflow.catalog.domain.EventStatus;
import com.ticketflow.catalog.domain.Sector;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Everything booking-service needs to sell one sector of one event. Cached in Redis, so it keeps
 * {@code status} and {@code startsAt} instead of a pre-computed "on sale" flag, which would go
 * stale while cached once the event starts.
 */
public record SaleSnapshot(
		String eventId,
		String eventName,
		Instant startsAt,
		String venueName,
		EventStatus status,
		String sectorId,
		String sectorName,
		BigDecimal unitPrice,
		String currency) {

	public static final String CURRENCY = "BRL";

	public static SaleSnapshot of(Event event, Sector sector) {
		return new SaleSnapshot(event.getId(), event.getName(), event.getStartsAt(), event.getVenue().name(),
				event.getStatus(), sector.id(), sector.name(), sector.price(), CURRENCY);
	}

	public boolean onSale(Instant now) {
		return status == EventStatus.PUBLISHED && startsAt.isAfter(now);
	}
}
