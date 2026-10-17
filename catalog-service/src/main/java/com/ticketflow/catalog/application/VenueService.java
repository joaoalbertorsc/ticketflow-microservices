package com.ticketflow.catalog.application;

import com.ticketflow.catalog.domain.Venue;
import com.ticketflow.catalog.domain.VenueRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class VenueService {

	private final VenueRepository venues;

	public VenueService(VenueRepository venues) {
		this.venues = venues;
	}

	public Venue create(String name, String city, String address) {
		return venues.save(Venue.create(name, city, address));
	}

	public List<Venue> list() {
		return venues.findAll().stream().sorted(Comparator.comparing(Venue::getName)).toList();
	}
}
