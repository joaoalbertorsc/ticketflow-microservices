package com.ticketflow.booking.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data copied from other services at booking time. If the catalog later changes a price or a user
 * changes their name, the booking keeps what was true when it was made.
 */
public final class Snapshots {

	private Snapshots() {
	}

	public record UserSnapshot(String name, String email) {
	}

	public record SaleSnapshot(String eventName, Instant startsAt, String venueName, String sectorName,
			BigDecimal unitPrice, String currency, boolean onSale) {
	}
}
