package com.ticketflow.booking.infrastructure.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Where the other services are, and how long to wait for them. The read timeout is what turns a
 * hanging service into a failure the circuit breaker can count.
 */
@Validated
@ConfigurationProperties("ticketflow.downstream")
public record DownstreamProperties(
		@NotBlank String authUrl,
		@NotBlank String catalogUrl,
		@NotBlank String inventoryUrl,
		@NotBlank String paymentUrl,
		@NotBlank String notificationUrl,
		@NotNull Duration connectTimeout,
		@NotNull Duration readTimeout) {
}
