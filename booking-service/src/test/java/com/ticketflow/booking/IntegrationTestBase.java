package com.ticketflow.booking;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Real application, real PostgreSQL, real Resilience4j — and one WireMock server standing in for
 * all five downstream services (their paths don't overlap). Shared by the integration tests so they
 * reuse one Spring context.
 */
@SpringBootTest(properties = {
		"ticketflow.downstream.read-timeout=1s",
		"resilience4j.circuitbreaker.instances.paymentService.wait-duration-in-open-state=1s"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

	/** Static and shared by every subclass (one Spring context); stopped when the JVM exits. */
	protected static final WireMockServer downstream = new WireMockServer(options().dynamicPort());

	static {
		downstream.start();
	}

	@DynamicPropertySource
	static void downstreamUrls(DynamicPropertyRegistry registry) {
		for (String service : new String[] {"auth", "catalog", "inventory", "payment", "notification"}) {
			registry.add("ticketflow.downstream." + service + "-url", downstream::baseUrl);
		}
	}

	@Autowired
	protected CircuitBreakerRegistry circuitBreakers;

	protected final DownstreamStubs stubs = new DownstreamStubs(downstream);

	@BeforeEach
	void resetDownstreamAndCircuits() {
		downstream.resetAll();
		circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
	}
}
