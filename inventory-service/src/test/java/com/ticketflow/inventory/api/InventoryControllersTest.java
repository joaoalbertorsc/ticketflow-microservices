package com.ticketflow.inventory.api;

import com.ticketflow.inventory.application.InventoryExceptions.HoldExpiredException;
import com.ticketflow.inventory.application.InventoryExceptions.NotEnoughSeatsException;
import com.ticketflow.inventory.application.ReservationService;
import com.ticketflow.inventory.application.ReservationService.ReserveResult;
import com.ticketflow.inventory.application.StockService;
import com.ticketflow.inventory.application.StockService.StockResult;
import com.ticketflow.inventory.domain.ReleaseReason;
import com.ticketflow.inventory.domain.Reservation;
import com.ticketflow.inventory.domain.SectorStock;
import com.ticketflow.inventory.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class InventoryControllersTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final UUID BOOKING = UUID.randomUUID();

	@Autowired
	MockMvc mvc;

	@MockitoBean
	StockService stocks;

	@MockitoBean
	ReservationService reservations;

	private final SectorStock stock = SectorStock.create("event-1", "pista", 100, NOW);

	private static RequestPostProcessor admin() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	private static RequestPostProcessor user() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private static String reserveBody(int quantity) {
		return """
				{"bookingId": "%s", "eventId": "event-1", "sectorId": "pista", "quantity": %d}
				""".formatted(BOOKING, quantity);
	}

	@Test
	void availabilityIsPublic() throws Exception {
		when(stocks.listForEvent("event-1")).thenReturn(List.of(stock));

		mvc.perform(get("/api/inventory/events/event-1/sectors"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].sectorId").value("pista"))
				.andExpect(jsonPath("$[0].availableSeats").value(100));
	}

	@Test
	void adminCreatesStockWith201AndResizesWith200() throws Exception {
		when(stocks.setCapacity("event-1", "pista", 100)).thenReturn(new StockResult(stock, true));
		mvc.perform(put("/api/inventory/events/event-1/sectors/pista").with(admin())
						.contentType(MediaType.APPLICATION_JSON).content("{\"totalSeats\": 100}"))
				.andExpect(status().isCreated());

		when(stocks.setCapacity("event-1", "pista", 100)).thenReturn(new StockResult(stock, false));
		mvc.perform(put("/api/inventory/events/event-1/sectors/pista").with(admin())
						.contentType(MediaType.APPLICATION_JSON).content("{\"totalSeats\": 100}"))
				.andExpect(status().isOk());
	}

	@Test
	void regularUserGets403EvenWithAnInvalidBody() throws Exception {
		mvc.perform(put("/api/inventory/events/event-1/sectors/pista").with(user())
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(stocks);
	}

	@Test
	void negativeCapacityIsRejected() throws Exception {
		mvc.perform(put("/api/inventory/events/event-1/sectors/pista").with(admin())
						.contentType(MediaType.APPLICATION_JSON).content("{\"totalSeats\": -1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.totalSeats").exists());
	}

	@Test
	void reservationsRequireAToken() throws Exception {
		mvc.perform(post("/internal/reservations").contentType(MediaType.APPLICATION_JSON).content(reserveBody(2)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void newHoldReturns201AndRepeatedCallReturns200() throws Exception {
		Reservation hold = Reservation.hold(BOOKING, stock, 2, NOW.plusSeconds(600), NOW);
		when(reservations.reserve(any())).thenReturn(new ReserveResult(hold, true));
		mvc.perform(post("/internal/reservations").with(user()).contentType(MediaType.APPLICATION_JSON)
						.content(reserveBody(2)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("HELD"))
				.andExpect(jsonPath("$.bookingId").value(BOOKING.toString()));

		when(reservations.reserve(any())).thenReturn(new ReserveResult(hold, false));
		mvc.perform(post("/internal/reservations").with(user()).contentType(MediaType.APPLICATION_JSON)
						.content(reserveBody(2)))
				.andExpect(status().isOk());
	}

	@Test
	void zeroSeatsIsRejected() throws Exception {
		mvc.perform(post("/internal/reservations").with(user()).contentType(MediaType.APPLICATION_JSON)
						.content(reserveBody(0)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.quantity").exists());
	}

	@Test
	void notEnoughSeatsReturns409WithAvailable() throws Exception {
		when(reservations.reserve(any())).thenThrow(new NotEnoughSeatsException(2, 1));

		mvc.perform(post("/internal/reservations").with(user()).contentType(MediaType.APPLICATION_JSON)
						.content(reserveBody(2)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Not enough seats"))
				.andExpect(jsonPath("$.available").value(1));
	}

	@Test
	void confirmOfExpiredHoldReturns409() throws Exception {
		when(reservations.confirm(BOOKING)).thenThrow(new HoldExpiredException(BOOKING));

		mvc.perform(post("/internal/reservations/booking/{id}/confirm", BOOKING).with(user()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Hold expired"));
	}

	@Test
	void releaseOfUnknownBookingReturns204() throws Exception {
		when(reservations.release(BOOKING, ReleaseReason.COMPENSATION)).thenReturn(Optional.empty());

		mvc.perform(post("/internal/reservations/booking/{id}/release", BOOKING).with(user()))
				.andExpect(status().isNoContent());
	}

	@Test
	void releaseAcceptsCancelledButNotExpiredAsReason() throws Exception {
		Reservation hold = Reservation.hold(BOOKING, stock, 2, NOW.plusSeconds(600), NOW);
		when(reservations.release(eq(BOOKING), eq(ReleaseReason.CANCELLED))).thenReturn(Optional.of(hold));

		mvc.perform(post("/internal/reservations/booking/{id}/release", BOOKING).param("reason", "CANCELLED").with(user()))
				.andExpect(status().isOk());
		verify(reservations).release(BOOKING, ReleaseReason.CANCELLED);

		mvc.perform(post("/internal/reservations/booking/{id}/release", BOOKING).param("reason", "EXPIRED").with(user()))
				.andExpect(status().isBadRequest());
	}
}
