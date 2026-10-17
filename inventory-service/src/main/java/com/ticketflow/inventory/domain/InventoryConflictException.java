package com.ticketflow.inventory.domain;

/** The request is valid but conflicts with the current state of the inventory (HTTP 409). */
public class InventoryConflictException extends RuntimeException {

	public InventoryConflictException(String message) {
		super(message);
	}
}
