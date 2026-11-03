package com.ticketflow.notification.application.template;

import com.ticketflow.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

@Component
public class BookingCancelledTemplate extends NotificationTemplate {

	@Override
	public NotificationType type() {
		return NotificationType.BOOKING_CANCELLED;
	}

	@Override
	protected String subject() {
		return "Your booking for {eventName} was cancelled";
	}

	@Override
	protected String body() {
		return """
				Hi {name},

				Your booking for {eventName} was cancelled.

				Refund: {currency} {totalAmount}, back to the card you paid with.

				TicketFlow""";
	}
}
