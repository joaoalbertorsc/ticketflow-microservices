package com.ticketflow.notification.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** A single clock bean so time-dependent code (timestamps) can be tested with a fixed clock. */
@Configuration
public class ClockConfig {

	/**
	 * Ticks in milliseconds, the precision MongoDB stores dates with: a timestamp returned right
	 * after a write is then identical to the one read back later.
	 */
	@Bean
	Clock clock() {
		return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
	}
}
