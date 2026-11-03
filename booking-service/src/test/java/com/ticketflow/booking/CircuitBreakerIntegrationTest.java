package com.ticketflow.booking;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.ticketflow.booking.application.port.Ports.Charge;
import com.ticketflow.booking.application.port.Ports.Inventory;
import com.ticketflow.booking.application.port.Ports.Payments;
import com.ticketflow.booking.application.port.Ports.Reservation;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The circuit breakers for real: Resilience4j annotations on the real clients, calling WireMock
 * over HTTP. Shows what counts as a failure, the OPEN → HALF_OPEN → CLOSED cycle, and fallbacks.
 */
class CircuitBreakerIntegrationTest extends IntegrationTestBase {

	private static final UUID USER = UUID.randomUUID();

	@Autowired
	Payments payments;

	@Autowired
	Inventory inventory;

	@Autowired
	JwtDecoder jwtDecoder;

	private Charge charge() {
		return payments.charge(USER, UUID.randomUUID(), new BigDecimal("240.00"), "BRL", "tok_visa");
	}

	private CircuitBreaker payment() {
		return circuitBreakers.circuitBreaker("paymentService");
	}

	@Test
	void declinedCardsAreAnswersNotFailuresAndNeverOpenTheCircuit() {
		stubs.chargeDeclined();

		for (int i = 0; i < 10; i++) {
			assertThat(charge().status()).isEqualTo(Charge.Status.DECLINED);
		}

		assertThat(payment().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
		assertThat(payment().getMetrics().getNumberOfFailedCalls()).isZero();
	}

	@Test
	void soldOutIsAnAnswerNotAFailure() {
		stubs.soldOut();

		for (int i = 0; i < 10; i++) {
			Reservation reservation = inventory.reserve(USER, UUID.randomUUID(), "e", "s", 2);
			assertThat(reservation.status()).isEqualTo(Reservation.Status.NOT_ENOUGH_SEATS);
			assertThat(reservation.detail()).contains("only 1 are available");
		}

		assertThat(circuitBreakers.circuitBreaker("inventoryService").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	void serverErrorsOpenTheCircuitAndThenCallsFailFastWithoutReachingTheService() {
		stubs.down(DownstreamStubs.CHARGE);

		for (int i = 0; i < 5; i++) {
			Charge result = charge(); // each failure goes through the fallback method
			assertThat(result.status()).isEqualTo(Charge.Status.UNAVAILABLE);
		}
		assertThat(payment().getState()).isEqualTo(CircuitBreaker.State.OPEN);

		long started = System.nanoTime();
		Charge rejected = charge();
		long tookMillis = (System.nanoTime() - started) / 1_000_000;

		assertThat(rejected.status()).isEqualTo(Charge.Status.UNAVAILABLE);
		assertThat(rejected.detail()).contains("circuit open");
		assertThat(tookMillis).isLessThan(100);
		downstream.verify(5, postRequestedFor(urlPathEqualTo("/internal/payments"))); // the 6th never left
	}

	@Test
	void openCircuitGoesHalfOpenAndClosesWhenTheServiceRecovers() {
		stubs.down(DownstreamStubs.CHARGE);
		for (int i = 0; i < 5; i++) {
			charge();
		}
		assertThat(payment().getState()).isEqualTo(CircuitBreaker.State.OPEN);

		stubs.chargeApproved(); // the service recovers
		await().atMost(Duration.ofSeconds(5))
				.until(() -> payment().getState() == CircuitBreaker.State.HALF_OPEN); // after 1 s in tests

		for (int i = 0; i < 3; i++) { // permitted trial calls
			assertThat(charge().status()).isEqualTo(Charge.Status.APPROVED);
		}
		assertThat(payment().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	/** Found by the end-to-end demo: with one shared breaker, failing charges blocked healthy refunds. */
	@Test
	void openChargeCircuitDoesNotBlockRefunds() {
		stubs.allHealthy();
		stubs.down(DownstreamStubs.CHARGE); // only charges fail, like payment-service's chaos mode
		for (int i = 0; i < 5; i++) {
			charge();
		}
		assertThat(payment().getState()).isEqualTo(CircuitBreaker.State.OPEN);

		assertThat(payments.refund(USER, UUID.randomUUID()).done()).isTrue();
		assertThat(circuitBreakers.circuitBreaker("paymentRefunds").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	void aServiceThatDoesNotAnswerInTimeCountsAsAFailure() {
		stubs.slow(DownstreamStubs.CHARGE, 1_500); // read timeout is 1 s in tests

		Charge result = charge();

		assertThat(result.status()).isEqualTo(Charge.Status.UNAVAILABLE);
		assertThat(result.detail()).contains("no answer in time");
		assertThat(payment().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
	}

	@Test
	void downstreamCallsCarryAnOnBehalfOfTokenForTheUser() {
		stubs.allHealthy();

		charge();

		List<LoggedRequest> requests = downstream.findAll(postRequestedFor(urlPathEqualTo("/internal/payments")));
		String header = requests.getFirst().getHeader("Authorization");
		assertThat(header).startsWith("Bearer ");
		Jwt token = jwtDecoder.decode(header.substring("Bearer ".length()));
		assertThat(token.getSubject()).isEqualTo(USER.toString());
		assertThat(token.getClaimAsStringList("roles")).containsExactly("SERVICE");
		assertThat(Duration.between(token.getIssuedAt(), token.getExpiresAt())).isEqualTo(Duration.ofSeconds(60));
	}
}
