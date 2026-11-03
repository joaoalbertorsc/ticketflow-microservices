package com.ticketflow.booking.infrastructure.config;

import com.ticketflow.booking.infrastructure.client.DownstreamProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(DownstreamProperties.class)
public class AppConfig {

	/** Runs the recovery job. Tests switch it off and run the job by hand, for determinism. */
	@Configuration
	@EnableScheduling
	@ConditionalOnProperty(name = "ticketflow.recovery.enabled", havingValue = "true", matchIfMissing = true)
	static class SchedulingConfig {
	}
}
