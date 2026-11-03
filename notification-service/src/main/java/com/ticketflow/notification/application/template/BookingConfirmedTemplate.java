package com.ticketflow.notification.application.template;

import com.ticketflow.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

@Component
public class BookingConfirmedTemplate extends NotificationTemplate {

	@Override
	public NotificationType type() {
		return NotificationType.BOOKING_CONFIRMED;
	}

	@Override
	protected String subject() {
		return "Your tickets for {eventName} are confirmed";
	}

	@Override
	protected String body() {
		return """
				Hi {name},

				Your booking is confirmed. See you there!

				Event:  {eventName}
				When:   {startsAt}
				Where:  {venueName}
				Seats:  {quantity} x {sectorName}
				Total:  {currency} {totalAmount}

				TicketFlow""";
	}
}
