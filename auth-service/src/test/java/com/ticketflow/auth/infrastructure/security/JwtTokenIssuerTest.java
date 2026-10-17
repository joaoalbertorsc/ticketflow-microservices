package com.ticketflow.auth.infrastructure.security;

import com.ticketflow.auth.TestUsers;
import com.ticketflow.auth.application.IssuedToken;
import com.ticketflow.auth.domain.Role;
import com.ticketflow.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenIssuerTest {

	private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

	private final SecurityConfig config = new SecurityConfig();
	private final JwtProperties properties = new JwtProperties(SECRET, "ticketflow-auth", Duration.ofHours(1));
	private final SecretKey key = config.jwtSigningKey(properties);
	private final JwtDecoder decoder = config.jwtDecoder(key, properties);

	@Test
	void tokenCarriesUserIdEmailAndRolesAndIsVerifiable() {
		Clock now = Clock.systemUTC();
		JwtTokenIssuer issuer = new JwtTokenIssuer(config.jwtEncoder(key), properties, now);
		User user = TestUsers.persisted("Ana", "ana@mail.com", "hash", Set.of(Role.USER, Role.ADMIN));

		IssuedToken token = issuer.issue(user);
		Jwt jwt = decoder.decode(token.value());

		assertThat(token.expiresIn()).isEqualTo(Duration.ofHours(1));
		assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("ticketflow-auth");
		assertThat(jwt.getClaimAsString("email")).isEqualTo("ana@mail.com");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "USER");
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void expiredTokenIsRejected() {
		Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
		JwtTokenIssuer issuer = new JwtTokenIssuer(config.jwtEncoder(key), properties, twoHoursAgo);

		String token = issuer.issue(TestUsers.persistedUser()).value();

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() {
		JwtProperties otherProperties = new JwtProperties(
				"another-secret-0123456789abcdef0123456789", "ticketflow-auth", Duration.ofHours(1));
		JwtTokenIssuer otherIssuer = new JwtTokenIssuer(
				config.jwtEncoder(config.jwtSigningKey(otherProperties)), otherProperties, Clock.systemUTC());

		String forged = otherIssuer.issue(TestUsers.persistedUser()).value();

		assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
	}

	@Test
	void tokenFromAnotherIssuerIsRejected() {
		JwtProperties otherIssuerProperties = new JwtProperties(SECRET, "someone-else", Duration.ofHours(1));
		JwtTokenIssuer otherIssuer = new JwtTokenIssuer(config.jwtEncoder(key), otherIssuerProperties, Clock.systemUTC());

		String token = otherIssuer.issue(TestUsers.persistedUser()).value();

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}
}
