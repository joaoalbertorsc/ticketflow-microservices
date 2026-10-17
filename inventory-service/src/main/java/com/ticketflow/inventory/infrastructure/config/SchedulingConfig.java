package com.ticketflow.inventory.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on scheduled jobs. Tests switch it off and run the jobs by hand, for determinism. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ticketflow.inventory.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
