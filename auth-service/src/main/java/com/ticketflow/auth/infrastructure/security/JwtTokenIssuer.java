package com.ticketflow.auth.infrastructure.security;

import com.ticketflow.auth.application.IssuedToken;
import com.ticketflow.auth.application.TokenIssuer;
import com.ticketflow.auth.domain.Role;
import com.ticketflow.auth.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class JwtTokenIssuer implements TokenIssuer {

	private final JwtEncoder encoder;
	private final JwtProperties properties;
	private final Clock clock;

	public JwtTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public IssuedToken issue(User user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(user.getId().toString())
				.issuedAt(now)
				.expiresAt(now.plus(properties.expiration()))
				.claim(SecurityConfig.EMAIL_CLAIM, user.getEmail())
				.claim(SecurityConfig.ROLES_CLAIM, user.getRoles().stream().map(Role::name).sorted().toList())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, properties.expiration());
	}
}
