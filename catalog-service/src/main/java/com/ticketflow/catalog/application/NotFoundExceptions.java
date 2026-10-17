package com.ticketflow.catalog.application;

/** "Not found" errors of the catalog, all mapped to HTTP 404. */
public final class NotFoundExceptions {

	private NotFoundExceptions() {
	}

	public abstract static class NotFoundException extends RuntimeException {

		protected NotFoundException(String message) {
			super(message);
		}
	}

	public static class EventNotFoundException extends NotFoundException {

		public EventNotFoundException(String id) {
			super("Event not found: " + id);
		}
	}

	public static class SectorNotFoundException extends NotFoundException {

		public SectorNotFoundException(String eventId, String sectorId) {
			super("Sector " + sectorId + " not found in event " + eventId);
		}
	}

	public static class VenueNotFoundException extends NotFoundException {

		public VenueNotFoundException(String id) {
			super("Venue not found: " + id);
		}
	}
}
