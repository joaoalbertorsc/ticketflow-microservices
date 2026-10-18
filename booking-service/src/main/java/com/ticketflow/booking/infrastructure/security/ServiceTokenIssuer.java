package com.ticketflow.booking.infrastructure.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Signs short-lived "on behalf of" tokens for downstream calls: subject = the booking's user, so
 * leaf services record the right owner; role SERVICE; valid 60 s. Used for every downstream call,
 * including the recovery job's, which has no user request (and so no user token) to forward.
 *
 * Possible only because tokens are HS256 with a shared secret; see future-features.md for the
 * asymmetric-keys / client-credentials alternative.
 */
@Component
public class ServiceTokenIssuer {

	private static final Duration LIFETIME = Duration.ofSeconds(60);

	private final JwtEncoder encoder;
	private final JwtProperties properties;
	private final Clock clock;

	public ServiceTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	public String onBehalfOf(UUID userId) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(userId.toString())
				.issuedAt(now)
				.expiresAt(now.plus(LIFETIME))
				.claim("roles", List.of("SERVICE"))
				.claim("act", "booking-service") // who is acting on the user's behalf
				.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims))
				.getTokenValue();
	}
}
