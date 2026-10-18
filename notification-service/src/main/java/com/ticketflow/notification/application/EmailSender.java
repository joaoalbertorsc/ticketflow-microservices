package com.ticketflow.notification.application;

/** Delivers an e-mail. Today a simulator; SMTP or a provider would be another implementation. */
public interface EmailSender {

	DeliveryResult send(String to, String subject, String body);

	/** Whether the provider accepted the message; a rejection is a result, not an exception. */
	record DeliveryResult(boolean delivered, String failureReason) {

		public static DeliveryResult ok() {
			return new DeliveryResult(true, null);
		}

		public static DeliveryResult rejected(String reason) {
			return new DeliveryResult(false, reason);
		}
	}
}
