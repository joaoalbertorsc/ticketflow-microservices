package com.ticketflow.catalog.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("venues")
public class Venue {

	@Id
	private String id;
	private String name;
	private String city;
	private String address;

	protected Venue() {
		// required by Spring Data
	}

	private Venue(String name, String city, String address) {
		this.name = name;
		this.city = city;
		this.address = address;
	}

	public static Venue create(String name, String city, String address) {
		return new Venue(name.trim(), city.trim(), address.trim());
	}

	public VenueSnapshot snapshot() {
		return new VenueSnapshot(id, name, city);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCity() {
		return city;
	}

	public String getAddress() {
		return address;
	}
}
