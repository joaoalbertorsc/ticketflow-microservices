package com.ticketflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * A hold on seats for one booking. Enforces its own state machine:
 * HELD → CONFIRMED, HELD → RELEASED, CONFIRMED → RELEASED. Nothing leaves RELEASED.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

	@Id
	private UUID id;

	@Column(name = "booking_id", nullable = false, unique = true)
	private UUID bookingId;

	/** FK to sector_stock, kept as a plain id: seats are moved with SQL updates, not through the entity. */
	@Column(name = "stock_id", nullable = false)
	private UUID stockId;

	@Column(name = "event_id", nullable = false, length = 24)
	private String eventId;

	@Column(name = "sector_id", nullable = false, length = 40)
	private String sectorId;

	@Column(nullable = false)
	private int quantity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReservationStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "release_reason", length = 20)
	private ReleaseReason releaseReason;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Version
	private Long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Reservation() {
		// required by JPA
	}

	public static Reservation hold(UUID bookingId, SectorStock stock, int quantity, Instant expiresAt, Instant now) {
		Reservation reservation = new Reservation();
		reservation.id = UUID.randomUUID();
		reservation.bookingId = bookingId;
		reservation.stockId = stock.getId();
		reservation.eventId = stock.getEventId();
		reservation.sectorId = stock.getSectorId();
		reservation.quantity = quantity;
		reservation.status = ReservationStatus.HELD;
		reservation.expiresAt = expiresAt;
		reservation.createdAt = now;
		reservation.updatedAt = now;
		return reservation;
	}

	/** Whether a repeated reserve call with this booking id asks for exactly the same thing. */
	public boolean isSameRequest(String eventId, String sectorId, int quantity) {
		return this.eventId.equals(eventId) && this.sectorId.equals(sectorId) && this.quantity == quantity;
	}

	public boolean isExpiredHold(Instant now) {
		return status == ReservationStatus.HELD && !now.isBefore(expiresAt);
	}

	/** Idempotent: confirming an already confirmed reservation changes nothing. */
	public void confirm(Instant now) {
		if (status == ReservationStatus.CONFIRMED) {
			return;
		}
		if (status == ReservationStatus.RELEASED) {
			throw new InventoryConflictException("Reservation was already released (" + releaseReason
					+ ") and can't be confirmed");
		}
		if (isExpiredHold(now)) {
			throw new IllegalStateException("Expired hold must be released, not confirmed");
		}
		status = ReservationStatus.CONFIRMED;
		updatedAt = now;
	}

	/**
	 * Idempotent: releasing an already released reservation changes nothing.
	 *
	 * @return {@code true} if the seats must be given back to the stock
	 */
	public boolean release(ReleaseReason reason, Instant now) {
		if (status == ReservationStatus.RELEASED) {
			return false;
		}
		status = ReservationStatus.RELEASED;
		releaseReason = reason;
		updatedAt = now;
		return true;
	}

	public UUID getId() {
		return id;
	}

	public UUID getBookingId() {
		return bookingId;
	}

	public UUID getStockId() {
		return stockId;
	}

	public String getEventId() {
		return eventId;
	}

	public String getSectorId() {
		return sectorId;
	}

	public int getQuantity() {
		return quantity;
	}

	public ReservationStatus getStatus() {
		return status;
	}

	public ReleaseReason getReleaseReason() {
		return releaseReason;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
