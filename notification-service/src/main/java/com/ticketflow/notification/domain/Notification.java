package com.ticketflow.notification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One message sent (or attempted) to a user, stored as a self-contained document: who it went to,
 * exactly what it said, the data it was rendered from, and how delivery went.
 */
@Document("notifications")
@CompoundIndex(name = "booking_type_unique", def = "{'bookingId': 1, 'type': 1}", unique = true)
@CompoundIndex(name = "user_newest_first", def = "{'userId': 1, 'createdAt': -1}")
public class Notification {

	public static final String CHANNEL_EMAIL = "EMAIL";

	@Id
	private String id;
	private UUID bookingId;
	private UUID userId;
	private NotificationType type;
	private String channel;
	private Recipient recipient;
	private Map<String, String> data;
	private String subject;
	private String body;
	private NotificationStatus status;
	private String failureReason;
	private Instant createdAt;
	private Instant sentAt;

	@Version
	private Long version;

	protected Notification() {
		// required by Spring Data
	}

	/** A rendered e-mail, stored before delivery so the (booking, type) slot is claimed first. */
	public static Notification sending(UUID bookingId, UUID userId, NotificationType type, Recipient recipient,
			Map<String, String> data, String subject, String body, Instant now) {
		Notification notification = new Notification();
		notification.bookingId = bookingId;
		notification.userId = userId;
		notification.type = type;
		notification.channel = CHANNEL_EMAIL;
		notification.recipient = recipient;
		notification.data = Map.copyOf(data);
		notification.subject = subject;
		notification.body = body;
		notification.status = NotificationStatus.SENDING;
		notification.createdAt = now;
		return notification;
	}

	public void markSent(Instant now) {
		requireSending();
		status = NotificationStatus.SENT;
		sentAt = now;
	}

	public void markFailed(String reason) {
		requireSending();
		status = NotificationStatus.FAILED;
		failureReason = reason;
	}

	private void requireSending() {
		if (status != NotificationStatus.SENDING) {
			throw new IllegalStateException("Notification already delivered: " + status);
		}
	}

	public String getId() {
		return id;
	}

	public UUID getBookingId() {
		return bookingId;
	}

	public UUID getUserId() {
		return userId;
	}

	public NotificationType getType() {
		return type;
	}

	public String getChannel() {
		return channel;
	}

	public Recipient getRecipient() {
		return recipient;
	}

	public Map<String, String> getData() {
		return data;
	}

	public String getSubject() {
		return subject;
	}

	public String getBody() {
		return body;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getSentAt() {
		return sentAt;
	}
}
