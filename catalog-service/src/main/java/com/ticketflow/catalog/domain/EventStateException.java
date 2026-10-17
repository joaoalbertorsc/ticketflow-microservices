package com.ticketflow.catalog.domain;

/** The operation is not allowed in the event's current status (e.g. editing a published event). */
public class EventStateException extends RuntimeException {

	public EventStateException(String message) {
		super(message);
	}
}
