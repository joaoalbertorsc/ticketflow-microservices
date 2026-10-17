package com.ticketflow.inventory;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/** PostgreSQL container plus a clock the tests can move forward. */
@TestConfiguration(proxyBeanMethods = false)
@Import(TestcontainersConfiguration.class)
public class IntegrationTestConfig {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock(Instant.now());
	}
}
