package com.ticketflow.booking.application;

import com.ticketflow.booking.application.port.Ports.Inventory;
import com.ticketflow.booking.application.port.Ports.NotificationRequest;
import com.ticketflow.booking.application.port.Ports.Notifier;
import com.ticketflow.booking.application.port.Ports.Payments;
import com.ticketflow.booking.application.port.Ports.Undo;
import com.ticketflow.booking.domain.Booking;
import com.ticketflow.booking.domain.BookingStatus;
import com.ticketflow.booking.domain.SagaStepName;
import com.ticketflow.booking.domain.StepOutcome;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * The undo steps and the final notification, shared by the create saga, cancellation and the
 * recovery job. Every call here is idempotent downstream, so running it again is always safe.
 */
@Component
public class Compensations {

	private final BookingStore store;
	private final Payments payments;
	private final Inventory inventory;
	private final Notifier notifier;
	private final Clock clock;

	public Compensations(BookingStore store, Payments payments, Inventory inventory, Notifier notifier, Clock clock) {
		this.store = store;
		this.payments = payments;
		this.inventory = inventory;
		this.notifier = notifier;
		this.clock = clock;
	}

	/**
	 * Refunds (if a charge was attempted) and releases the seats. When both are done the booking
	 * moves from COMPENSATING to FAILED, or from CANCELLING to CANCELLED, and the user is notified.
	 * If either can't run now, the booking stays where it is for the recovery job.
	 *
	 * @return {@code true} if the undo finished
	 */
	public boolean undo(UUID bookingId) {
		Booking booking = store.get(bookingId);
		boolean cancellation = booking.getStatus() == BookingStatus.CANCELLING;

		boolean refunded = true;
		if (booking.isChargeAttempted()) {
			Undo refund = payments.refund(booking.getUserId(), bookingId);
			log(bookingId, SagaStepName.REFUND, refund);
			refunded = refund.done();
		}
		Undo release = inventory.release(booking.getUserId(), bookingId, cancellation);
		log(bookingId, SagaStepName.RELEASE_SEATS, release);

		if (!refunded || !release.done()) {
			return false;
		}
		store.update(bookingId, b -> {
			if (cancellation) {
				b.cancelled(clock.instant());
			} else {
				b.compensated(clock.instant());
			}
		});
		notifyOutcome(bookingId);
		return true;
	}

	/**
	 * Tells the user how the booking ended. Skipped when we never learned who the user is or what
	 * they tried to book (the saga failed before the snapshot).
	 */
	/** @return {@code true} if the notification was sent (or there was nothing to send) */
	public boolean notifyOutcome(UUID bookingId) {
		Booking booking = store.get(bookingId);
		if (!booking.isNotifiable() || !booking.hasSnapshot()) {
			return true;
		}
		Undo sent = notifier.notify(booking.getUserId(), notificationFor(booking));
		store.update(bookingId, b -> {
			b.log(SagaStepName.NOTIFY, sent.done() ? StepOutcome.SUCCEEDED : StepOutcome.FALLBACK,
					sent.done() ? sent.detail() : sent.detail() + " — notification left PENDING", clock.instant());
			if (sent.done()) {
				b.notificationSent(clock.instant());
			} else {
				b.notificationPending(clock.instant());
			}
		});
		return sent.done();
	}

	private void log(UUID bookingId, SagaStepName step, Undo result) {
		store.update(bookingId, b -> b.log(step, result.done() ? StepOutcome.SUCCEEDED : StepOutcome.FALLBACK,
				result.detail(), clock.instant()));
	}

	static NotificationRequest notificationFor(Booking booking) {
		String total = booking.getTotalAmount() == null ? "" : booking.getTotalAmount().toPlainString();
		return switch (booking.getStatus()) {
			case CONFIRMED -> new NotificationRequest(booking.getId(), "BOOKING_CONFIRMED", booking.getUserName(),
					booking.getUserEmail(), Map.of(
							"eventName", booking.getEventName(),
							"startsAt", booking.getStartsAt().toString(),
							"venueName", booking.getVenueName(),
							"sectorName", booking.getSectorName(),
							"quantity", String.valueOf(booking.getQuantity()),
							"totalAmount", total,
							"currency", booking.getCurrency()));
			case FAILED -> new NotificationRequest(booking.getId(), "BOOKING_FAILED", booking.getUserName(),
					booking.getUserEmail(), Map.of(
							"eventName", booking.getEventName(),
							"reason", userFacingReason(booking)));
			case CANCELLED -> new NotificationRequest(booking.getId(), "BOOKING_CANCELLED", booking.getUserName(),
					booking.getUserEmail(), Map.of(
							"eventName", booking.getEventName(),
							"totalAmount", total,
							"currency", booking.getCurrency()));
			default -> throw new IllegalStateException("No notification for status " + booking.getStatus());
		};
	}

	private static String userFacingReason(Booking booking) {
		return switch (booking.getFailureCode()) {
			case SOLD_OUT -> "There were not enough seats left in that sector.";
			case PAYMENT_DECLINED -> "Your card was declined.";
			case HOLD_EXPIRED -> "The payment took too long and your seats were released.";
			case EVENT_NOT_AVAILABLE -> "This event is not on sale.";
			case USER_NOT_FOUND -> "We could not find your account.";
			case SERVICE_UNAVAILABLE, SAGA_INTERRUPTED -> "A temporary problem on our side. Please try again.";
		};
	}
}
