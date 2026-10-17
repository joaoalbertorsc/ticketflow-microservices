package com.ticketflow.catalog.api.dto;

import com.ticketflow.catalog.application.SaleSnapshot;

import java.math.BigDecimal;
import java.time.Instant;

/** What booking-service receives; {@code onSale} is computed at request time, never cached. */
public record SaleSnapshotResponse(
		String eventId,
		String eventName,
		Instant startsAt,
		String venueName,
		String sectorId,
		String sectorName,
		BigDecimal unitPrice,
		String currency,
		boolean onSale) {

	public static SaleSnapshotResponse from(SaleSnapshot snapshot, Instant now) {
		return new SaleSnapshotResponse(snapshot.eventId(), snapshot.eventName(), snapshot.startsAt(),
				snapshot.venueName(), snapshot.sectorId(), snapshot.sectorName(), snapshot.unitPrice(),
				snapshot.currency(), snapshot.onSale(now));
	}
}
