package com.ticketflow.booking.infrastructure.client;

import com.ticketflow.booking.application.port.Ports.UserDirectory;
import com.ticketflow.booking.application.port.Ports.UserLookup;
import com.ticketflow.booking.domain.Snapshots.UserSnapshot;
import com.ticketflow.booking.infrastructure.security.ServiceTokenIssuer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/** auth-service: who is booking (name and e-mail, kept as a snapshot). */
@Component
public class AuthClient extends DownstreamClient implements UserDirectory {

	/** Local copy of auth-service's response: no shared code between services (ADR 0005). */
	record UserSnapshotResponse(UUID id, String name, String email) {
	}

	public AuthClient(RestClient.Builder builder, DownstreamProperties properties, ServiceTokenIssuer tokens) {
		super(builder, properties.authUrl(), properties, tokens, "auth-service");
	}

	@Override
	@CircuitBreaker(name = "authService", fallbackMethod = "findUserFallback")
	public UserLookup findUser(UUID userId) {
		return http.get().uri("/internal/users/{id}", userId).headers(onBehalfOf(userId))
				.exchange((request, response) -> {
					if (response.getStatusCode().is2xxSuccessful()) {
						UserSnapshotResponse user = response.bodyTo(UserSnapshotResponse.class);
						return UserLookup.found(new UserSnapshot(user.name(), user.email()));
					}
					if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
						return UserLookup.notFound();
					}
					throw unexpected(response.getStatusCode());
				});
	}

	/** Fallback: without the user's e-mail the booking can't go on, and nothing was done yet. */
	private UserLookup findUserFallback(UUID userId, Throwable cause) {
		return UserLookup.unavailable(unavailable("user lookup", cause));
	}
}
