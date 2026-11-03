package com.ticketflow.auth.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret     HMAC key shared with every service that validates tokens (at least 32 bytes for HS256)
 * @param issuer     value of the {@code iss} claim
 * @param expiration token lifetime
 */
@Validated
@ConfigurationProperties("ticketflow.jwt")
public record JwtProperties(
		@NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
		@NotBlank String issuer,
		@NotNull Duration expiration) {
}
