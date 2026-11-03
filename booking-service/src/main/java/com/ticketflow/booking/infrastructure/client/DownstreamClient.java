package com.ticketflow.booking.infrastructure.client;

import com.ticketflow.booking.infrastructure.security.ServiceTokenIssuer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base of every client of another service. Rules shared by all of them:
 * <ul>
 *   <li>a 4xx the service documents is a business answer: returned, never thrown, so it never
 *       counts against the circuit breaker;</li>
 *   <li>5xx, connection errors, timeouts and unexpected answers are thrown: Resilience4j counts them,
 *       and the {@code fallbackMethod} turns them into an UNAVAILABLE result.</li>
 * </ul>
 */
abstract class DownstreamClient {

	private final Logger log = LoggerFactory.getLogger(getClass());

	protected final RestClient http;
	private final ServiceTokenIssuer tokens;
	private final String service;

	protected DownstreamClient(RestClient.Builder builder, String baseUrl, DownstreamProperties properties,
			ServiceTokenIssuer tokens, String service) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		this.http = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
		this.tokens = tokens;
		this.service = service;
	}

	protected Consumer<HttpHeaders> onBehalfOf(UUID userId) {
		return headers -> headers.setBearerAuth(tokens.onBehalfOf(userId));
	}

	/** For any answer the caller didn't map: counted as a failure, like a 5xx. */
	protected UnexpectedResponseException unexpected(HttpStatusCode status) {
		return new UnexpectedResponseException(service + " answered HTTP " + status.value());
	}

	/** Shared by all fallback methods: a short, human reason for the saga log, and a log line. */
	protected String unavailable(String operation, Throwable cause) {
		String reason;
		if (cause instanceof CallNotPermittedException) {
			reason = "circuit open, call not attempted";
		} else if (cause instanceof ResourceAccessException) {
			reason = "no answer in time / connection failed";
		} else {
			reason = cause.getMessage();
		}
		String detail = service + " unavailable for " + operation + " (" + reason + ")";
		log.warn("Fallback: {}", detail);
		return detail;
	}

	/** A response the client doesn't know how to interpret. Counts as a failure. */
	static class UnexpectedResponseException extends RuntimeException {

		UnexpectedResponseException(String message) {
			super(message);
		}
	}
}
