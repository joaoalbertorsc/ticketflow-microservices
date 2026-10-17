package com.ticketflow.catalog.domain;

/** The event data itself is invalid (e.g. duplicate sector ids, date in the past). */
public class InvalidEventException extends RuntimeException {

	public InvalidEventException(String message) {
		super(message);
	}
}
