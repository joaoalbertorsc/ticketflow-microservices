package com.ticketflow.booking;

import com.jayway.jsonpath.JsonPath;
import com.ticketflow.booking.application.BookingQueries;
import com.ticketflow.booking.application.BookingQueries.BookingView;
import com.ticketflow.booking.application.BookingStore;
import com.ticketflow.booking.application.RecoveryJob;
import com.ticketflow.booking.domain.Booking;
import com.ticketflow.booking.domain.BookingStatus;
import com.ticketflow.booking.domain.FailureCode;
import com.ticketflow.booking.domain.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole saga over HTTP, persisted in PostgreSQL, against WireMock downstream services. */
class SagaIntegrationTest extends IntegrationTestBase {

	private final UUID user = UUID.randomUUID();

	@Autowired
	MockMvc mvc;

	@Autowired
	BookingQueries queries;

	@Autowired
	BookingStore store;

	@Autowired
	RecoveryJob recoveryJob;

	@Autowired
	JdbcTemplate jdbc;

	private RequestPostProcessor token(UUID userId) {
		return jwt().jwt(t -> t.subject(userId.toString()));
	}

	private ResultActions book() throws Exception {
		return mvc.perform(post("/api/bookings").with(token(user)).contentType(MediaType.APPLICATION_JSON).content("""
				{"eventId": "%s", "sectorId": "%s", "quantity": 2, "cardToken": "tok_visa"}
				""".formatted(DownstreamStubs.EVENT_ID, DownstreamStubs.SECTOR_ID)));
	}

	private UUID idOf(ResultActions result) throws Exception {
		return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
	}

	private BookingView reload(UUID id) {
		return queries.get(id, user, false);
	}

	@Test
	void confirmedBookingIsPersistedWithItsWholeSagaLog() throws Exception {
		stubs.allHealthy();

		UUID id = idOf(book()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.totalAmount").value(240.00))
				.andExpect(jsonPath("$.eventName").value("Hamlet"))
				.andExpect(jsonPath("$.steps.length()").value(6)));

		mvc.perform(get("/api/bookings/{id}", id).with(token(user))).andExpect(status().isOk());
		mvc.perform(get("/api/bookings/{id}", id).with(token(UUID.randomUUID()))).andExpect(status().isNotFound());
		mvc.perform(get("/api/bookings/me").with(token(user))).andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void declinedCardIs409AndTheSeatsAreReleased() throws Exception {
		stubs.allHealthy();
		stubs.chargeDeclined();

		book().andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureCode").value("PAYMENT_DECLINED"));

		downstream.verify(1, postRequestedFor(urlPathMatching("/internal/reservations/booking/.+/release")));
		downstream.verify(1, postRequestedFor(urlPathEqualTo("/internal/notifications")));
	}

	@Test
	void paymentDownIs503AndEverythingIsUndone() throws Exception {
		stubs.allHealthy();
		stubs.down(DownstreamStubs.CHARGE);

		book().andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureCode").value("SERVICE_UNAVAILABLE"));

		downstream.verify(1, postRequestedFor(urlPathMatching("/internal/payments/booking/.+/refund")));
		downstream.verify(1, postRequestedFor(urlPathMatching("/internal/reservations/booking/.+/release")));
	}

	@Test
	void undoThatCantRunNowIsFinishedByTheRecoveryJob() throws Exception {
		stubs.allHealthy();
		stubs.down(DownstreamStubs.CHARGE);
		stubs.down(DownstreamStubs.REFUND);

		UUID id = idOf(book().andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("COMPENSATING")));

		stubs.allHealthy(); // payment-service is back
		recoveryJob.run();

		BookingView booking = reload(id);
		assertThat(booking.status()).isEqualTo(BookingStatus.FAILED);
		assertThat(booking.notificationStatus()).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	void sagaInterruptedByACrashIsRolledBack() {
		stubs.allHealthy();
		Booking stuck = store.insert(Booking.start(user, null, DownstreamStubs.EVENT_ID, DownstreamStubs.SECTOR_ID, 2,
				Instant.now()));
		// as if the service died right after creating it, ten minutes ago
		jdbc.update("UPDATE bookings SET updated_at = ? WHERE id = ?",
				java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(10))), stuck.getId());

		RecoveryJob.Report report = recoveryJob.run();

		assertThat(report.interrupted()).isEqualTo(1);
		BookingView booking = reload(stuck.getId());
		assertThat(booking.status()).isEqualTo(BookingStatus.FAILED);
		assertThat(booking.failureCode()).isEqualTo(FailureCode.SAGA_INTERRUPTED);
		downstream.verify(1, postRequestedFor(urlPathMatching("/internal/reservations/booking/.+/release")));
	}

	@Test
	void notificationDownKeepsTheBookingConfirmedAndTheJobSendsItLater() throws Exception {
		stubs.allHealthy();
		stubs.down(DownstreamStubs.NOTIFY);

		UUID id = idOf(book().andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.notificationStatus").value("PENDING")));

		stubs.allHealthy();
		recoveryJob.run();

		assertThat(reload(id).notificationStatus()).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	void sameIdempotencyKeyBooksOnce() throws Exception {
		stubs.allHealthy();
		String body = """
				{"eventId": "%s", "sectorId": "%s", "quantity": 2, "cardToken": "tok_visa"}
				""".formatted(DownstreamStubs.EVENT_ID, DownstreamStubs.SECTOR_ID);

		UUID first = idOf(mvc.perform(post("/api/bookings").with(token(user)).header("Idempotency-Key", "k-1")
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()));
		UUID again = idOf(mvc.perform(post("/api/bookings").with(token(user)).header("Idempotency-Key", "k-1")
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()));

		assertThat(again).isEqualTo(first);
		downstream.verify(1, postRequestedFor(urlPathEqualTo("/internal/payments")));
	}

	@Test
	void cancellationRefundsAndReleases() throws Exception {
		stubs.allHealthy();
		UUID id = idOf(book().andExpect(status().isCreated()));

		mvc.perform(post("/api/bookings/{id}/cancel", id).with(token(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		downstream.verify(1, postRequestedFor(urlPathMatching("/internal/payments/booking/.+/refund")));
		downstream.verify(1, postRequestedFor(urlPathMatching("/internal/reservations/booking/.+/release"))
				.withQueryParam("reason", com.github.tomakehurst.wiremock.client.WireMock.equalTo("CANCELLED")));
	}
}
