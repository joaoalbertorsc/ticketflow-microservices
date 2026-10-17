package com.ticketflow.catalog.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TheaterDetails(
		@NotBlank @Size(max = 100) String director,
		@NotEmpty @Size(max = 50) List<@NotBlank String> cast,
		@NotNull @Positive Integer durationMinutes) implements EventDetails {

	public TheaterDetails {
		cast = cast == null ? null : cast.stream().toList();
	}

	@Override
	public EventType type() {
		return EventType.THEATER;
	}
}
