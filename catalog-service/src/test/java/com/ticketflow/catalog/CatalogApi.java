package com.ticketflow.catalog;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the catalog API like a real client in integration tests, with real signed JWTs
 * (same secret and issuer that auth-service uses in the test profile).
 */
public class CatalogApi {

	private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

	private final MockMvc mvc;

	public CatalogApi(MockMvc mvc) {
		this.mvc = mvc;
	}

	public static String bearer(String... roles) {
		SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("ticketflow-auth")
				.subject(UUID.randomUUID().toString())
				.issuedAt(now)
				.expiresAt(now.plus(Duration.ofHours(1)))
				.claim("roles", List.of(roles))
				.build();
		String token = NimbusJwtEncoder.withSecretKey(key).build()
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return "Bearer " + token;
	}

	public String createVenue(String city) throws Exception {
		String body = """
				{"name": "Arena %s", "city": "%s", "address": "Main street 1"}
				""".formatted(city, city);
		return idOf(adminPost("/api/venues", body));
	}

	public String createConcert(String venueId) throws Exception {
		String body = """
				{
				  "name": "Rock Night",
				  "startsAt": "%s",
				  "venueId": "%s",
				  "sectors": [
				    {"id": "pista", "name": "Pista", "price": 450.00},
				    {"id": "vip", "name": "VIP", "price": 890.50}
				  ],
				  "details": {"type": "CONCERT", "lineup": ["Band A", "Band B"], "genre": "Rock"}
				}
				""".formatted(Instant.now().plus(Duration.ofDays(30)), venueId);
		return idOf(adminPost("/api/events", body));
	}

	public void publish(String eventId) throws Exception {
		mvc.perform(post("/api/events/{id}/publish", eventId).header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
				.andExpect(status().isOk());
	}

	public void cancel(String eventId) throws Exception {
		mvc.perform(post("/api/events/{id}/cancel", eventId).header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
				.andExpect(status().isOk());
	}

	private String adminPost(String path, String body) throws Exception {
		return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer("ADMIN"))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	private static String idOf(String json) {
		return JsonPath.read(json, "$.id");
	}
}
