package com.ticketflow.auth.api.dto;

import com.ticketflow.auth.application.IssuedToken;

/** @param expiresIn token lifetime in seconds */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

	public static TokenResponse from(IssuedToken token) {
		return new TokenResponse(token.value(), "Bearer", token.expiresIn().toSeconds());
	}
}
