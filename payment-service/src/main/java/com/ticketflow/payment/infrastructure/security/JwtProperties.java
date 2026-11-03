package com.ticketflow.payment.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret HMAC key shared with auth-service, which signs the tokens
 * @param issuer expected {@code iss} claim
 */
@Validated
@ConfigurationProperties("ticketflow.jwt")
public record JwtProperties(
		@NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
		@NotBlank String issuer) {
}
