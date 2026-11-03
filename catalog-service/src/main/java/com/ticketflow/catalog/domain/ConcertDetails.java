package com.ticketflow.catalog.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConcertDetails(
		@NotEmpty @Size(max = 50) List<@NotBlank String> lineup,
		@NotBlank @Size(max = 50) String genre) implements EventDetails {

	public ConcertDetails {
		lineup = lineup == null ? null : lineup.stream().toList();
	}

	@Override
	public EventType type() {
		return EventType.CONCERT;
	}
}
