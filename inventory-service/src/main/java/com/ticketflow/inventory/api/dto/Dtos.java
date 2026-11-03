package com.ticketflow.inventory.api.dto;

import com.ticketflow.inventory.application.ReservationService.ReserveCommand;
import com.ticketflow.inventory.domain.ReleaseReason;
import com.ticketflow.inventory.domain.Reservation;
import com.ticketflow.inventory.domain.ReservationStatus;
import com.ticketflow.inventory.domain.SectorStock;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Request and response bodies of the inventory API. */
public final class Dtos {

	private Dtos() {
	}

	public record CapacityRequest(@NotNull @Min(0) Integer totalSeats) {
	}

	public record StockResponse(String eventId, String sectorId, int totalSeats, int availableSeats) {

		public static StockResponse from(SectorStock stock) {
			return new StockResponse(stock.getEventId(), stock.getSectorId(), stock.getTotalSeats(),
					stock.getAvailableSeats());
		}
	}

	public record ReserveRequest(
			@NotNull UUID bookingId,
			@NotBlank @Size(max = 24) String eventId,
			@NotBlank @Size(max = 40) String sectorId,
			@NotNull @Min(1) Integer quantity) {

		public ReserveCommand toCommand() {
			return new ReserveCommand(bookingId, eventId, sectorId, quantity);
		}
	}

	public record ReservationResponse(
			UUID reservationId,
			UUID bookingId,
			String eventId,
			String sectorId,
			int quantity,
			ReservationStatus status,
			ReleaseReason releaseReason,
			Instant expiresAt) {

		public static ReservationResponse from(Reservation reservation) {
			return new ReservationResponse(reservation.getId(), reservation.getBookingId(), reservation.getEventId(),
					reservation.getSectorId(), reservation.getQuantity(), reservation.getStatus(),
					reservation.getReleaseReason(), reservation.getExpiresAt());
		}
	}

	/** Reasons a caller may give for a release; EXPIRED is reserved for the expiry job. */
	public enum ReleaseRequestReason {
		COMPENSATION,
		CANCELLED;

		public ReleaseReason toDomain() {
			return ReleaseReason.valueOf(name());
		}
	}
}
