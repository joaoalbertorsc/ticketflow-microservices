package com.ticketflow.catalog.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * An area of the venue sold at one price. The id is a short slug chosen by the admin (e.g.
 * "pista") and is unique within its event; other services refer to it as eventId + sectorId.
 */
public record Sector(
		@NotBlank @Pattern(regexp = "^[a-z0-9-]{1,40}$", message = "must be 1-40 lower-case letters, digits or '-'") String id,
		@NotBlank @Size(max = 60) String name,
		@NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal price) {
}
