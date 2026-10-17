package com.ticketflow.catalog.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An event and everything needed to sell it, stored as one MongoDB document. Enforces its own
 * lifecycle (draft → published → cancelled), so it can never be put into an invalid state.
 */
@Document("events")
@CompoundIndex(name = "status_startsAt", def = "{'status': 1, 'startsAt': 1}")
public class Event {

	@Id
	private String id;
	private String name;
	private String description;
	@Indexed
	private EventType type;
	private Instant startsAt;
	private EventStatus status;
	private VenueSnapshot venue;
	private List<Sector> sectors;
	private EventDetails details;
	private Instant createdAt;
	private Instant updatedAt;

	@Version
	private Long version;

	protected Event() {
		// required by Spring Data
	}

	public static Event draft(EventData data, Instant now) {
		Event event = new Event();
		event.status = EventStatus.DRAFT;
		event.createdAt = now;
		event.apply(data, now);
		return event;
	}

	public void edit(EventData data, Instant now) {
		if (status != EventStatus.DRAFT) {
			throw new EventStateException("Only draft events can be edited; this event is " + status);
		}
		apply(data, now);
	}

	public void publish(Instant now) {
		if (status != EventStatus.DRAFT) {
			throw new EventStateException("Only draft events can be published; this event is " + status);
		}
		if (sectors.isEmpty()) {
			throw new EventStateException("An event needs at least one sector to be published");
		}
		if (!startsAt.isAfter(now)) {
			throw new EventStateException("An event that has already started cannot be published");
		}
		status = EventStatus.PUBLISHED;
		updatedAt = now;
	}

	public void cancel(Instant now) {
		if (status == EventStatus.CANCELLED) {
			throw new EventStateException("Event is already cancelled");
		}
		status = EventStatus.CANCELLED;
		updatedAt = now;
	}

	/** Tickets can be sold only for published events that haven't started yet. */
	public boolean isOnSale(Instant now) {
		return status == EventStatus.PUBLISHED && startsAt.isAfter(now);
	}

	public Optional<Sector> findSector(String sectorId) {
		return sectors.stream().filter(sector -> sector.id().equals(sectorId)).findFirst();
	}

	private void apply(EventData data, Instant now) {
		if (!data.startsAt().isAfter(now)) {
			throw new InvalidEventException("startsAt must be in the future");
		}
		Set<String> sectorIds = new HashSet<>();
		for (Sector sector : data.sectors()) {
			if (!sectorIds.add(sector.id())) {
				throw new InvalidEventException("Duplicate sector id: " + sector.id());
			}
		}
		this.name = data.name().trim();
		this.description = data.description() == null ? null : data.description().trim();
		this.startsAt = data.startsAt();
		this.venue = data.venue();
		this.sectors = List.copyOf(data.sectors());
		this.details = data.details();
		this.type = data.details().type();
		this.updatedAt = now;
	}

	/** The editable part of an event, used for both creation and edits. */
	public record EventData(String name, String description, Instant startsAt, VenueSnapshot venue,
			List<Sector> sectors, EventDetails details) {
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public EventType getType() {
		return type;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public EventStatus getStatus() {
		return status;
	}

	public VenueSnapshot getVenue() {
		return venue;
	}

	public List<Sector> getSectors() {
		return sectors;
	}

	public EventDetails getDetails() {
		return details;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Long getVersion() {
		return version;
	}
}
