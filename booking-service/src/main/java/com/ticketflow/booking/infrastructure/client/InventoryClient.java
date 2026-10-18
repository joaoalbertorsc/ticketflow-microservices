package com.ticketflow.booking.infrastructure.client;

import com.ticketflow.booking.application.port.Ports.Confirmation;
import com.ticketflow.booking.application.port.Ports.Inventory;
import com.ticketflow.booking.application.port.Ports.Reservation;
import com.ticketflow.booking.application.port.Ports.Undo;
import com.ticketflow.booking.infrastructure.security.ServiceTokenIssuer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/** inventory-service: hold, confirm and release seats. Every call is keyed by booking id. */
@Component
public class InventoryClient extends DownstreamClient implements Inventory {

	/** Local copies of inventory-service's bodies (ADR 0005). */
	record ReserveRequest(UUID bookingId, String eventId, String sectorId, int quantity) {
	}

	record ReservationResponse(UUID reservationId, UUID bookingId, String status) {
	}

	record Problem(String title, String detail) {
	}

	public InventoryClient(RestClient.Builder builder, DownstreamProperties properties, ServiceTokenIssuer tokens) {
		super(builder, properties.inventoryUrl(), properties, tokens, "inventory-service");
	}

	@Override
	@CircuitBreaker(name = "inventoryService", fallbackMethod = "reserveFallback")
	public Reservation reserve(UUID onBehalfOf, UUID bookingId, String eventId, String sectorId, int quantity) {
		return http.post().uri("/internal/reservations").headers(onBehalfOf(onBehalfOf))
				.body(new ReserveRequest(bookingId, eventId, sectorId, quantity))
				.exchange((request, response) -> {
					if (response.getStatusCode().is2xxSuccessful()) {
						ReservationResponse reservation = response.bodyTo(ReservationResponse.class);
						if ("RELEASED".equals(reservation.status())) { // a retry after this booking's hold expired
							return Reservation.rejected(Reservation.Status.NOT_ENOUGH_SEATS,
									"The seats held for this booking were already released");
						}
						return Reservation.reserved(reservation.reservationId());
					}
					if (response.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
						return Reservation.rejected(Reservation.Status.NOT_ENOUGH_SEATS,
								response.bodyTo(Problem.class).detail());
					}
					if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
						return Reservation.rejected(Reservation.Status.NO_STOCK, "No seats are on sale for this sector");
					}
					throw unexpected(response.getStatusCode());
				});
	}

	/**
	 * Fallback: if the call timed out, the hold may exist anyway. The saga treats UNAVAILABLE here
	 * as "compensate", so it releases by booking id (a no-op if there was no hold).
	 */
	private Reservation reserveFallback(UUID onBehalfOf, UUID bookingId, String eventId, String sectorId,
			int quantity, Throwable cause) {
		return Reservation.unavailable(unavailable("seat reservation", cause));
	}

	@Override
	@CircuitBreaker(name = "inventoryService", fallbackMethod = "confirmFallback")
	public Confirmation confirm(UUID onBehalfOf, UUID bookingId) {
		return http.post().uri("/internal/reservations/booking/{bookingId}/confirm", bookingId)
				.headers(onBehalfOf(onBehalfOf))
				.exchange((request, response) -> {
					if (response.getStatusCode().is2xxSuccessful()) {
						return Confirmation.confirmed();
					}
					if (response.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)
							|| response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
						return Confirmation.holdExpired(response.bodyTo(Problem.class).detail());
					}
					throw unexpected(response.getStatusCode());
				});
	}

	/** Fallback: payment is taken but seats can't be confirmed: the saga refunds and releases. */
	private Confirmation confirmFallback(UUID onBehalfOf, UUID bookingId, Throwable cause) {
		return Confirmation.unavailable(unavailable("seat confirmation", cause));
	}

	/** A breaker of its own, like refunds: failing reservations must not block giving seats back. */
	@Override
	@CircuitBreaker(name = "inventoryReleases", fallbackMethod = "releaseFallback")
	public Undo release(UUID onBehalfOf, UUID bookingId, boolean cancellation) {
		return http.post().uri(uri -> uri.path("/internal/reservations/booking/{bookingId}/release")
						.queryParam("reason", cancellation ? "CANCELLED" : "COMPENSATION").build(bookingId))
				.headers(onBehalfOf(onBehalfOf))
				.exchange((request, response) -> {
					if (response.getStatusCode().isSameCodeAs(HttpStatus.NO_CONTENT)) {
						return Undo.ok("no hold existed");
					}
					if (response.getStatusCode().is2xxSuccessful()) {
						return Undo.ok();
					}
					throw unexpected(response.getStatusCode());
				});
	}

	/** Fallback: the booking stays COMPENSATING / CANCELLING and the recovery job retries. */
	private Undo releaseFallback(UUID onBehalfOf, UUID bookingId, boolean cancellation, Throwable cause) {
		return Undo.unavailable(unavailable("seat release", cause));
	}
}
