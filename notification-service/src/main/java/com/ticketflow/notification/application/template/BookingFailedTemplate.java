package com.ticketflow.notification.application.template;

import com.ticketflow.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

@Component
public class BookingFailedTemplate extends NotificationTemplate {

	@Override
	public NotificationType type() {
		return NotificationType.BOOKING_FAILED;
	}

	@Override
	protected String subject() {
		return "We couldn't complete your booking for {eventName}";
	}

	@Override
	protected String body() {
		return """
				Hi {name},

				Unfortunately your booking for {eventName} could not be completed.

				Reason: {reason}

				You have not been charged. Any seats held for you were released.

				TicketFlow""";
	}
}
