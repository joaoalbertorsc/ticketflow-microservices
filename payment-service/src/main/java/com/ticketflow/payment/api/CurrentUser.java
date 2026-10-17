package com.ticketflow.payment.api;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** The user behind a request: auth-service puts the user id (a UUID) in the token's subject. */
final class CurrentUser {

	private CurrentUser() {
	}

	static UUID id(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new InvalidSubjectException();
		}
	}

	static boolean isAdmin(Jwt jwt) {
		return jwt.getClaimAsStringList("roles") != null && jwt.getClaimAsStringList("roles").contains("ADMIN");
	}

	/** 401: the token is valid but its subject isn't a user id. */
	static class InvalidSubjectException extends RuntimeException {

		InvalidSubjectException() {
			super("Token subject is not a user id");
		}
	}
}
