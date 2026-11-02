package com.ticketflow.catalog.domain;

import java.util.List;
import java.util.Optional;

public interface VenueRepository {

	Venue save(Venue venue);

	Optional<Venue> findById(String id);

	List<Venue> findAll();
}
