package com.ticketflow.booking.application.port;

import com.ticketflow.booking.domain.Snapshots.SaleSnapshot;
import com.ticketflow.booking.domain.Snapshots.UserSnapshot;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * What the saga needs from each other service. Every answer is either a business result or
 * UNAVAILABLE (the service is down, slow, or its circuit breaker is open). The saga never sees
 * HTTP, timeouts or Resilience4j: those live in the infrastructure clients.
 *
 * Every call is made on behalf of a user, whose id goes into the token the client sends.
 */
public final class Ports {

	private Ports() {
	}

	// ---- auth-service ------------------------------------------------------------------------

	public interface UserDirectory {
		UserLookup findUser(UUID userId);
	}

	public record UserLookup(Status status, UserSnapshot user, String detail) {
		public enum Status { FOUND, NOT_FOUND, UNAVAILABLE }

		public static UserLookup found(UserSnapshot user) {
			return new UserLookup(Status.FOUND, user, null);
		}

		public static UserLookup notFound() {
			return new UserLookup(Status.NOT_FOUND, null, "User not found");
		}

		public static UserLookup unavailable(String detail) {
			return new UserLookup(Status.UNAVAILABLE, null, detail);
		}
	}

	// ---- catalog-service ---------------------------------------------------------------------

	public interface Catalog {
		SaleLookup findSale(UUID onBehalfOf, String eventId, String sectorId);
	}

	public record SaleLookup(Status status, SaleSnapshot sale, String detail) {
		public enum Status { FOUND, NOT_FOUND, UNAVAILABLE }

		public static SaleLookup found(SaleSnapshot sale) {
			return new SaleLookup(Status.FOUND, sale, null);
		}

		public static SaleLookup notFound(String detail) {
			return new SaleLookup(Status.NOT_FOUND, null, detail);
		}

		public static SaleLookup unavailable(String detail) {
			return new SaleLookup(Status.UNAVAILABLE, null, detail);
		}
	}

	// ---- inventory-service -------------------------------------------------------------------

	public interface Inventory {
		Reservation reserve(UUID onBehalfOf, UUID bookingId, String eventId, String sectorId, int quantity);

		Confirmation confirm(UUID onBehalfOf, UUID bookingId);

		Undo release(UUID onBehalfOf, UUID bookingId, boolean cancellation);
	}

	public record Reservation(Status status, UUID reservationId, String detail) {
		public enum Status { RESERVED, NOT_ENOUGH_SEATS, NO_STOCK, UNAVAILABLE }

		public static Reservation reserved(UUID reservationId) {
			return new Reservation(Status.RESERVED, reservationId, null);
		}

		public static Reservation rejected(Status status, String detail) {
			return new Reservation(status, null, detail);
		}

		public static Reservation unavailable(String detail) {
			return new Reservation(Status.UNAVAILABLE, null, detail);
		}
	}

	public record Confirmation(Status status, String detail) {
		public enum Status { CONFIRMED, HOLD_EXPIRED, UNAVAILABLE }

		public static Confirmation confirmed() {
			return new Confirmation(Status.CONFIRMED, null);
		}

		public static Confirmation holdExpired(String detail) {
			return new Confirmation(Status.HOLD_EXPIRED, detail);
		}

		public static Confirmation unavailable(String detail) {
			return new Confirmation(Status.UNAVAILABLE, detail);
		}
	}

	// ---- payment-service ---------------------------------------------------------------------

	public interface Payments {
		Charge charge(UUID onBehalfOf, UUID bookingId, BigDecimal amount, String currency, String cardToken);

		Undo refund(UUID onBehalfOf, UUID bookingId);
	}

	public record Charge(Status status, UUID paymentId, String detail) {
		/** VOIDED: a refund for this booking overtook the charge; it was never taken. */
		public enum Status { APPROVED, DECLINED, VOIDED, UNAVAILABLE }

		public static Charge approved(UUID paymentId) {
			return new Charge(Status.APPROVED, paymentId, null);
		}

		public static Charge declined(UUID paymentId, String detail) {
			return new Charge(Status.DECLINED, paymentId, detail);
		}

		public static Charge voided() {
			return new Charge(Status.VOIDED, null, "Booking was voided before the charge");
		}

		public static Charge unavailable(String detail) {
			return new Charge(Status.UNAVAILABLE, null, detail);
		}
	}

	// ---- notification-service ----------------------------------------------------------------

	public interface Notifier {
		Undo notify(UUID onBehalfOf, NotificationRequest request);
	}

	public record NotificationRequest(UUID bookingId, String type, String recipientName, String recipientEmail,
			Map<String, String> data) {
	}

	// ---- shared ------------------------------------------------------------------------------

	/** Result of an idempotent side effect (release, refund, notify): done, or try again later. */
	public record Undo(boolean done, String detail) {

		public static Undo ok() {
			return new Undo(true, null);
		}

		public static Undo ok(String detail) {
			return new Undo(true, detail);
		}

		public static Undo unavailable(String detail) {
			return new Undo(false, detail);
		}
	}
}
