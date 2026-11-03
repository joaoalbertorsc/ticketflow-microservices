package com.ticketflow.booking.api;

import com.ticketflow.booking.application.BookingExceptions.BookingNotFoundException;
import com.ticketflow.booking.application.BookingQueries;
import com.ticketflow.booking.application.BookingQueries.BookingView;
import com.ticketflow.booking.application.BookingSaga;
import com.ticketflow.booking.application.BookingSaga.BookCommand;
import com.ticketflow.booking.application.BookingSaga.BookResult;
import com.ticketflow.booking.application.CancellationService;
import com.ticketflow.booking.domain.BookingStatus;
import com.ticketflow.booking.domain.FailureCode;
import com.ticketflow.booking.domain.NotificationStatus;
import com.ticketflow.booking.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class BookingControllerTest {

	private static final UUID USER = UUID.randomUUID();
	private static final UUID BOOKING = UUID.randomUUID();
	private static final String BODY = """
			{"eventId": "6650f1aa0000000000000001", "sectorId": "plateia", "quantity": 2, "cardToken": "tok_visa"}
			""";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	BookingSaga saga;

	@MockitoBean
	CancellationService cancellations;

	@MockitoBean
	BookingQueries queries;

	private static RequestPostProcessor user() {
		return jwt().jwt(token -> token.subject(USER.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private static BookingView view(BookingStatus status, FailureCode code) {
		return new BookingView(BOOKING, USER, status, code, code == null ? null : "reason", "e1", "Hamlet",
				Instant.parse("2099-12-01T20:00:00Z"), "Theatro", "plateia", "Plateia", 2, new BigDecimal("120.00"),
				new BigDecimal("240.00"), "BRL", null, null, NotificationStatus.SENT, Instant.now(), Instant.now(), List.of());
	}

	private void sagaEndsWith(BookingStatus status, FailureCode code) {
		when(saga.book(any())).thenReturn(new BookResult(BOOKING, true));
		when(queries.get(BOOKING, USER, false)).thenReturn(view(status, code));
	}

	@Test
	void confirmedIs201WithLocation() throws Exception {
		sagaEndsWith(BookingStatus.CONFIRMED, null);

		mvc.perform(post("/api/bookings").with(user()).contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/bookings/" + BOOKING))
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	@Test
	void businessFailureIs409WithTheBooking() throws Exception {
		sagaEndsWith(BookingStatus.FAILED, FailureCode.SOLD_OUT);

		mvc.perform(post("/api/bookings").with(user()).contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.failureCode").value("SOLD_OUT"));
	}

	@Test
	void unavailableDependencyIs503() throws Exception {
		sagaEndsWith(BookingStatus.COMPENSATING, FailureCode.SERVICE_UNAVAILABLE);

		mvc.perform(post("/api/bookings").with(user()).contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("COMPENSATING"));
	}

	@Test
	void idempotencyKeyAndCallerArePassedToTheSaga() throws Exception {
		sagaEndsWith(BookingStatus.CONFIRMED, null);

		mvc.perform(post("/api/bookings").with(user()).header("Idempotency-Key", "abc-123")
				.contentType(MediaType.APPLICATION_JSON).content(BODY));

		ArgumentCaptor<BookCommand> command = ArgumentCaptor.forClass(BookCommand.class);
		verify(saga).book(command.capture());
		assertThat(command.getValue().userId()).isEqualTo(USER);
		assertThat(command.getValue().idempotencyKey()).isEqualTo("abc-123");
		assertThat(command.getValue().quantity()).isEqualTo(2);
	}

	@Test
	void invalidRequestCreatesNothing() throws Exception {
		mvc.perform(post("/api/bookings").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
						{"eventId": "", "sectorId": "plateia", "quantity": 11}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.eventId").exists())
				.andExpect(jsonPath("$.errors.quantity").exists())
				.andExpect(jsonPath("$.errors.cardToken").exists());
		verifyNoInteractions(saga);
	}

	@Test
	void bookingRequiresAToken() throws Exception {
		mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void someoneElsesBookingIs404() throws Exception {
		when(queries.get(eq(BOOKING), eq(USER), eq(false))).thenThrow(new BookingNotFoundException(BOOKING));

		mvc.perform(get("/api/bookings/{id}", BOOKING).with(user())).andExpect(status().isNotFound());
	}

	@Test
	void adminListIsAdminOnly() throws Exception {
		mvc.perform(get("/api/admin/bookings").with(user())).andExpect(status().isForbidden());
	}

	@Test
	void circuitBreakerStateIsPublic() throws Exception {
		// The actuator endpoint itself isn't in this web slice: here we only check security lets it through.
		mvc.perform(get("/actuator/circuitbreakers")).andExpect(status().isNotFound());
	}
}
