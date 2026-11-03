package com.ticketflow.gateway.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an id: keeps the client's {@code X-Request-Id} when it looks sane, otherwise
 * generates one. The id is forwarded to the service, returned to the client, and written on one
 * access-log line per request. Runs before security, so rejected requests are logged too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";

	private static final Logger access = LoggerFactory.getLogger("access");
	private static final Pattern SANE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String incoming = request.getHeader(HEADER);
		String requestId = incoming != null && SANE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
		long started = System.nanoTime();

		response.setHeader(HEADER, requestId);
		MDC.put("requestId", requestId);
		try {
			chain.doFilter(new WithRequestId(request, requestId), response);
		} finally {
			long millis = (System.nanoTime() - started) / 1_000_000;
			access.info("{} {} {} -> {} ({} ms)", requestId, request.getMethod(), request.getRequestURI(),
					response.getStatus(), millis);
			MDC.remove("requestId");
		}
	}

	/** The incoming request, with {@code X-Request-Id} set, so the gateway forwards it to the service. */
	private static final class WithRequestId extends HttpServletRequestWrapper {

		private final String requestId;

		WithRequestId(HttpServletRequest request, String requestId) {
			super(request);
			this.requestId = requestId;
		}

		@Override
		public String getHeader(String name) {
			return HEADER.equalsIgnoreCase(name) ? requestId : super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			return HEADER.equalsIgnoreCase(name) ? Collections.enumeration(Set.of(requestId)) : super.getHeaders(name);
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
			names.removeIf(HEADER::equalsIgnoreCase);
			names.add(HEADER);
			return Collections.enumeration(names);
		}
	}
}
