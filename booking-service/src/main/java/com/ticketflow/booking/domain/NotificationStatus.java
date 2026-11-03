package com.ticketflow.booking.domain;

public enum NotificationStatus {
	NONE,
	SENT,
	/** notification-service was unavailable; the recovery job will send it. */
	PENDING
}
