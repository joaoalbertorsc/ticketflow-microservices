package com.ticketflow.notification.domain;

public enum NotificationStatus {
	/** Stored, being delivered. Claims the (booking, type) slot so a concurrent duplicate is never sent. */
	SENDING,
	SENT,
	/** The provider rejected the message (e.g. the mailbox doesn't exist). */
	FAILED
}
