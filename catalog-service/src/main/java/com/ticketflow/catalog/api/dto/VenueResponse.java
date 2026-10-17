package com.ticketflow.catalog.api.dto;

import com.ticketflow.catalog.domain.Venue;

public record VenueResponse(String id, String name, String city, String address) {

	public static VenueResponse from(Venue venue) {
		return new VenueResponse(venue.getId(), venue.getName(), venue.getCity(), venue.getAddress());
	}
}
