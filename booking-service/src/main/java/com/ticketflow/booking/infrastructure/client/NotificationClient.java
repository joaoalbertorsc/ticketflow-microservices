package com.ticketflow.booking.infrastructure.client;

import com.ticketflow.booking.application.port.Ports.NotificationRequest;
import com.ticketflow.booking.application.port.Ports.Notifier;
import com.ticketflow.booking.application.port.Ports.Undo;
import com.ticketflow.booking.infrastructure.security.ServiceTokenIssuer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/** notification-service: tell the user how the booking ended. Idempotent by (booking, type). */
@Component
public class NotificationClient extends DownstreamClient implements Notifier {

	private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

	/** Local copies of notification-service's request (ADR 0005). */
	record Recipient(String name, String email) {
	}

	record SendRequest(UUID bookingId, String type, Recipient recipient, Map<String, String> data) {
	}

	public NotificationClient(RestClient.Builder builder, DownstreamProperties properties, ServiceTokenIssuer tokens) {
		super(builder, properties.notificationUrl(), properties, tokens, "notification-service");
	}

	@Override
	@CircuitBreaker(name = "notificationService", fallbackMethod = "notifyFallback")
	public Undo notify(UUID onBehalfOf, NotificationRequest notification) {
		SendRequest body = new SendRequest(notification.bookingId(), notification.type(),
				new Recipient(notification.recipientName(), notification.recipientEmail()), notification.data());
		return http.post().uri("/internal/notifications").headers(onBehalfOf(onBehalfOf)).body(body)
				.exchange((request, response) -> {
					if (response.getStatusCode().is2xxSuccessful()) {
						return Undo.ok();
					}
					if (response.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
						// Our request is wrong: retrying won't help, so don't leave it pending forever.
						log.error("notification-service rejected the notification of booking {}", notification.bookingId());
						return Undo.ok("rejected by notification-service");
					}
					throw unexpected(response.getStatusCode());
				});
	}

	/** Fallback: the booking keeps its outcome; the notification becomes PENDING and is retried later. */
	private Undo notifyFallback(UUID onBehalfOf, NotificationRequest notification, Throwable cause) {
		return Undo.unavailable(unavailable("notification", cause));
	}
}
