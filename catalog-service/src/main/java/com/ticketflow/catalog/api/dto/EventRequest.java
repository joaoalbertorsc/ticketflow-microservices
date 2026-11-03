package com.ticketflow.catalog.api.dto;

import com.ticketflow.catalog.application.EventCommand;
import com.ticketflow.catalog.application.PolymorphicDetails;
import com.ticketflow.catalog.domain.EventDetails;
import com.ticketflow.catalog.domain.Sector;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Body for creating or editing an event. The event type comes from {@code details.type}. */
public record EventRequest(
		@NotBlank @Size(max = 150) String name,
		@Size(max = 2000) String description,
		@NotNull @Future Instant startsAt,
		@NotBlank String venueId,
		@NotNull @Size(max = 20) List<@Valid @NotNull Sector> sectors,
		@NotNull @Valid @PolymorphicDetails EventDetails details) {

	public EventCommand toCommand() {
		return new EventCommand(name, description, startsAt, venueId, sectors, details);
	}
}
