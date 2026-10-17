package com.ticketflow.auth.application;

/** Same exception for unknown e-mail and wrong password, so callers can't tell which one failed. */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid e-mail or password");
	}
}
