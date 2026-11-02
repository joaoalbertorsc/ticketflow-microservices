package com.ticketflow.inventory.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param holdDuration        how long reserved seats stay held without being confirmed
 * @param maxSeatsPerBooking  most seats one booking may reserve (anti-scalper rule)
 * @param expiryBatchSize     how many expired holds the job releases per transaction
 */
@Validated
@ConfigurationProperties("ticketflow.inventory")
public record InventoryProperties(
		@NotNull Duration holdDuration,
		@Min(1) int maxSeatsPerBooking,
		@Min(1) int expiryBatchSize) {
}
