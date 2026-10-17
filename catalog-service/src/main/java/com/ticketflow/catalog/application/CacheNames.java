package com.ticketflow.catalog.application;

public final class CacheNames {

	/** Published event by id. */
	public static final String EVENTS = "events";

	/** Sale snapshot by "eventId:sectorId". */
	public static final String EVENT_SECTORS = "event-sectors";

	private CacheNames() {
	}
}
