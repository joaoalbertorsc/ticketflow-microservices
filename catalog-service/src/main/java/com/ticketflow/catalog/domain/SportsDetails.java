package com.ticketflow.catalog.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SportsDetails(
		@NotBlank @Size(max = 100) String homeTeam,
		@NotBlank @Size(max = 100) String awayTeam,
		@NotBlank @Size(max = 100) String league) implements EventDetails {

	@Override
	public EventType type() {
		return EventType.SPORTS;
	}
}
