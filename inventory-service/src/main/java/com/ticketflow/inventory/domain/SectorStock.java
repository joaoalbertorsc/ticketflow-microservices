package com.ticketflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * The seats of one sector of one event. Seats are taken and given back with atomic SQL updates
 * (see {@link StockRepository}); this entity only handles creation and resizing.
 */
@Entity
@Table(name = "sector_stock")
public class SectorStock {

	@Id
	private UUID id;

	@Column(name = "event_id", nullable = false, length = 24)
	private String eventId;

	@Column(name = "sector_id", nullable = false, length = 40)
	private String sectorId;

	@Column(name = "total_seats", nullable = false)
	private int totalSeats;

	@Column(name = "available_seats", nullable = false)
	private int availableSeats;

	/** Bumped by every seat update too, so a resize never overwrites a concurrent reservation. */
	@Version
	private Long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SectorStock() {
		// required by JPA
	}

	public static SectorStock create(String eventId, String sectorId, int totalSeats, Instant now) {
		if (totalSeats < 0) {
			throw new IllegalArgumentException("totalSeats must not be negative");
		}
		SectorStock stock = new SectorStock();
		stock.id = UUID.randomUUID();
		stock.eventId = eventId;
		stock.sectorId = sectorId;
		stock.totalSeats = totalSeats;
		stock.availableSeats = totalSeats;
		stock.createdAt = now;
		stock.updatedAt = now;
		return stock;
	}

	/** Changes capacity, keeping the seats already held or sold. Can't go below those. */
	public void resize(int newTotalSeats, Instant now) {
		int taken = takenSeats();
		if (newTotalSeats < taken) {
			throw new InventoryConflictException("Cannot resize to " + newTotalSeats + " seats: "
					+ taken + " are already held or sold");
		}
		this.availableSeats = newTotalSeats - taken;
		this.totalSeats = newTotalSeats;
		this.updatedAt = now;
	}

	public int takenSeats() {
		return totalSeats - availableSeats;
	}

	public UUID getId() {
		return id;
	}

	public String getEventId() {
		return eventId;
	}

	public String getSectorId() {
		return sectorId;
	}

	public int getTotalSeats() {
		return totalSeats;
	}

	public int getAvailableSeats() {
		return availableSeats;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
