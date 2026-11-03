package com.ticketflow.inventory.application;

import java.util.UUID;

/** Application errors of the inventory; each one maps to a single HTTP status. */
public final class InventoryExceptions {

	private InventoryExceptions() {
	}

	/** 404 */
	public abstract static class NotFoundException extends RuntimeException {

		protected NotFoundException(String message) {
			super(message);
		}
	}

	public static class StockNotFoundException extends NotFoundException {

		public StockNotFoundException(String eventId, String sectorId) {
			super("No seats configured for event " + eventId + ", sector " + sectorId);
		}
	}

	public static class ReservationNotFoundException extends NotFoundException {

		public ReservationNotFoundException(UUID bookingId) {
			super("No reservation for booking " + bookingId);
		}
	}

	/** 409, with how many seats are left so the caller can tell the user. */
	public static class NotEnoughSeatsException extends RuntimeException {

		private final int available;

		public NotEnoughSeatsException(int requested, int available) {
			super("Requested " + requested + " seats but only " + available + " are available");
			this.available = available;
		}

		public int getAvailable() {
			return available;
		}
	}

	/** 409: the hold ran out before it was confirmed; the seats have been given back. */
	public static class HoldExpiredException extends RuntimeException {

		public HoldExpiredException(UUID bookingId) {
			super("The hold for booking " + bookingId + " expired and its seats were released");
		}
	}

	/** 400 */
	public static class TooManySeatsException extends RuntimeException {

		public TooManySeatsException(int requested, int max) {
			super("A booking can reserve at most " + max + " seats (requested " + requested + ")");
		}
	}
}
