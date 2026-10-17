package com.ticketflow.inventory.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Gives back the seats of holds that were never confirmed, so a crashed or slow orchestrator
 * can't lock seats forever. Each batch is its own transaction, so a large backlog doesn't hold
 * locks for long.
 */
@Component
public class ExpiredHoldsJob {

	private static final Logger log = LoggerFactory.getLogger(ExpiredHoldsJob.class);

	private final ReservationService reservations;
	private final InventoryProperties properties;

	public ExpiredHoldsJob(ReservationService reservations, InventoryProperties properties) {
		this.reservations = reservations;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${ticketflow.inventory.expiry-job-interval:30s}")
	public int releaseExpiredHolds() {
		int total = 0;
		int released;
		do {
			released = reservations.releaseExpiredHolds();
			total += released;
		} while (released == properties.expiryBatchSize());
		if (total > 0) {
			log.info("Released {} expired hold(s)", total);
		}
		return total;
	}
}
