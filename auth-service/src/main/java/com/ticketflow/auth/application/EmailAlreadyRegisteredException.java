package com.ticketflow.auth.application;

public class EmailAlreadyRegisteredException extends RuntimeException {

	public EmailAlreadyRegisteredException(String email) {
		super("E-mail already registered: " + email);
	}
}
