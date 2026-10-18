package com.ticketflow.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real gateway on a random port, in front of six WireMock servers — one per service — each
 * answering with its own name, so every test can see which service got the request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayTest {

	private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";
	private static final Map<String, WireMockServer> SERVICES = new LinkedHashMap<>();

	static {
		for (String name : List.of("auth", "catalog", "inventory", "payment", "notification", "booking")) {
			WireMockServer server = new WireMockServer(options().dynamicPort());
			server.start();
			SERVICES.put(name, server);
		}
	}

	@DynamicPropertySource
	static void serviceUrls(DynamicPropertyRegistry registry) {
		SERVICES.forEach((name, server) -> registry.add(name.toUpperCase() + "_URL", server::baseUrl));
	}

	@LocalServerPort
	int port;

	private final HttpClient http = HttpClient.newHttpClient();

	@BeforeEach
	void everyServiceAnswersWithItsName() {
		SERVICES.forEach((name, server) -> {
			server.resetAll();
			server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.any(anyUrl())
					.willReturn(aResponse().withStatus(200).withBody(name)));
		});
	}

	// ---- helpers -----------------------------------------------------------------------------

	private static String token(String issuer, Instant expiresAt, String secret) {
		SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(UUID.randomUUID().toString())
				.issuedAt(expiresAt.minus(Duration.ofHours(1))).expiresAt(expiresAt).claim("roles", List.of("USER")).build();
		return NimbusJwtEncoder.withSecretKey(key).build()
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private static String validToken() {
		return token("ticketflow-auth", Instant.now().plus(Duration.ofHours(1)), SECRET);
	}

	private HttpResponse<String> send(String method, String path, String token, String body, String... headers)
			throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		if (body != null) {
			request.header("Content-Type", "application/json");
		}
		if (headers.length > 0) {
			request.headers(headers);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private int requestsReceivedBy(String service) {
		return SERVICES.get(service).countRequestsMatching(anyRequestedFor(anyUrl()).build()).getCount();
	}

	// ---- routing -----------------------------------------------------------------------------

	@ParameterizedTest(name = "{0} {1} -> {2}")
	@CsvSource({
			"POST, /api/auth/login,                     auth",
			"PUT,  /api/users/42/roles,                 auth",
			"GET,  /api/events,                         catalog",
			"GET,  /api/events/abc,                     catalog",
			"POST, /api/events/abc/publish,             catalog",
			"GET,  /api/venues,                         catalog",
			"GET,  /api/admin/events,                   catalog",
			"GET,  /api/inventory/events/e1/sectors,    inventory",
			"PUT,  /api/payments/admin/chaos,           payment",
			"GET,  /api/payments/booking/b1,            payment",
			"GET,  /api/notifications/me,               notification",
			"GET,  /api/admin/notifications,            notification",
			"POST, /api/bookings,                       booking",
			"GET,  /api/bookings/me,                    booking",
			"POST, /api/bookings/b1/cancel,             booking",
			"GET,  /api/admin/bookings,                 booking"
	})
	void everyPublicPathReachesItsService(String method, String path, String service) throws Exception {
		HttpResponse<String> response = send(method, path, validToken(), method.equals("GET") ? null : "{}");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo(service);
		SERVICES.get(service).verify(anyRequestedFor(urlEqualTo(path)));
	}

	@Test
	void pathQueryBodyAndHeadersArriveUnchangedAndTheAnswerComesBackUnchanged() throws Exception {
		WireMockServer booking = SERVICES.get("booking");
		booking.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/api/bookings?source=test"))
				.willReturn(aResponse().withStatus(409).withHeader("Content-Type", "application/json")
						.withBody("{\"status\":\"FAILED\",\"failureCode\":\"SOLD_OUT\"}")));
		String token = validToken();

		HttpResponse<String> response = send("POST", "/api/bookings?source=test", token,
				"{\"eventId\":\"e1\",\"quantity\":2}", "Idempotency-Key", "k-1");

		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(response.body()).contains("SOLD_OUT");
		booking.verify(postRequestedFor(urlEqualTo("/api/bookings?source=test"))
				.withHeader("Authorization", equalTo("Bearer " + token))
				.withHeader("Idempotency-Key", equalTo("k-1"))
				.withRequestBody(equalToJson("{\"eventId\":\"e1\",\"quantity\":2}")));
	}

	// ---- security ----------------------------------------------------------------------------

	@ParameterizedTest(name = "{0} {1} is public")
	@CsvSource({
			"POST, /api/auth/register",
			"POST, /api/auth/login",
			"GET,  /api/events",
			"GET,  /api/events/abc",
			"GET,  /api/venues",
			"GET,  /api/inventory/events/e1/sectors/s1"
	})
	void publicRoutesNeedNoToken(String method, String path) throws Exception {
		assertThat(send(method, path, null, method.equals("GET") ? null : "{}").statusCode()).isEqualTo(200);
	}

	@Test
	void protectedRouteWithoutTokenIsRejectedAtTheEdge() throws Exception {
		HttpResponse<String> response = send("POST", "/api/bookings", null, "{}");

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(requestsReceivedBy("booking")).isZero();
	}

	@Test
	void forgedExpiredOrForeignTokensAreRejected() throws Exception {
		String forged = token("ticketflow-auth", Instant.now().plus(Duration.ofHours(1)), "another-secret-0123456789abcdef01234567");
		String expired = token("ticketflow-auth", Instant.now().minus(Duration.ofMinutes(5)), SECRET);
		String foreign = token("someone-else", Instant.now().plus(Duration.ofHours(1)), SECRET);

		for (String token : List.of(forged, expired, foreign)) {
			assertThat(send("GET", "/api/bookings/me", token, null).statusCode()).isEqualTo(401);
		}
		assertThat(requestsReceivedBy("booking")).isZero();
	}

	@Test
	void internalEndpointsAreNeverExposedEvenWithAValidToken() throws Exception {
		for (String path : List.of("/internal/users/42", "/internal/reservations", "/internal/payments/booking/b1/refund")) {
			assertThat(send("POST", path, validToken(), "{}").statusCode()).isEqualTo(404);
			assertThat(send("GET", path, null, null).statusCode()).isEqualTo(404);
		}
		SERVICES.keySet().forEach(service -> assertThat(requestsReceivedBy(service)).isZero());
	}

	// ---- request id --------------------------------------------------------------------------

	@Test
	void requestIdIsGeneratedForwardedAndReturned() throws Exception {
		HttpResponse<String> response = send("GET", "/api/events", null, null);

		String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
		assertThat(requestId).matches("[0-9a-f-]{36}");
		SERVICES.get("catalog").verify(anyRequestedFor(urlEqualTo("/api/events")).withHeader("X-Request-Id", equalTo(requestId)));
	}

	@Test
	void clientRequestIdIsKeptWhenSaneAndReplacedWhenNot() throws Exception {
		HttpResponse<String> kept = send("GET", "/api/events", null, null, "X-Request-Id", "client-abc.123");
		HttpResponse<String> replaced = send("GET", "/api/events", null, null, "X-Request-Id", "bad id with spaces <script>");

		assertThat(kept.headers().firstValue("X-Request-Id")).hasValue("client-abc.123");
		assertThat(replaced.headers().firstValue("X-Request-Id").orElseThrow()).matches("[0-9a-f-]{36}");
		SERVICES.get("catalog").verify(anyRequestedFor(urlEqualTo("/api/events"))
				.withHeader("X-Request-Id", matching("[0-9a-f-]{36}")));
	}

	@Test
	void rejectedRequestsGetARequestIdToo() throws Exception {
		HttpResponse<String> response = send("POST", "/api/bookings", null, "{}");

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
	}
}
