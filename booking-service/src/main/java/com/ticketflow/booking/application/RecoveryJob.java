package com.ticketflow.booking.application;

import com.ticketflow.booking.domain.Booking;
import com.ticketflow.booking.domain.BookingRepository;
import com.ticketflow.booking.domain.FailureCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Finishes what the live saga couldn't:
 * <ol>
 *   <li>undo steps that couldn't run (bookings left COMPENSATING / CANCELLING);</li>
 *   <li>sagas interrupted by a crash (left PENDING / SEATS_RESERVED / PAID too long): undone;</li>
 *   <li>notifications that couldn't be sent (PENDING).</li>
 * </ol>
 * Each booking is handled under a row lock taken with SKIP LOCKED, so several instances can run
 * the job without ever working on the same booking. Every call it makes is idempotent downstream.
 */
@Component
public class RecoveryJob {

	private static final Logger log = LoggerFactory.getLogger(RecoveryJob.class);

	private final BookingRepository bookings;
	private final BookingStore store;
	private final Compensations compensations;
	private final TransactionTemplate transaction;
	private final Clock clock;
	private final Duration stuckAfter;
	private final int batchSize;

	public RecoveryJob(BookingRepository bookings, BookingStore store, Compensations compensations,
			PlatformTransactionManager transactionManager, Clock clock,
			@Value("${ticketflow.recovery.stuck-after:5m}") Duration stuckAfter,
			@Value("${ticketflow.recovery.batch-size:20}") int batchSize) {
		this.bookings = bookings;
		this.store = store;
		this.compensations = compensations;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
		this.stuckAfter = stuckAfter;
		this.batchSize = batchSize;
	}

	/** How many bookings one run finished (attempts that must be retried later aren't counted). */
	public record Report(int undone, int interrupted, int notified) {
	}

	@Scheduled(fixedDelayString = "${ticketflow.recovery.interval:30s}",
			initialDelayString = "${ticketflow.recovery.interval:30s}")
	public Report run() {
		int undone = 0;
		for (UUID id : bookings.findUnfinishedUndo(batchSize)) {
			if (handle(id, booking -> compensations.undo(id))) {
				undone++;
			}
		}

		int interrupted = 0;
		for (UUID id : bookings.findStuckSagas(clock.instant().minus(stuckAfter), batchSize)) {
			if (handle(id, this::undoInterrupted)) {
				interrupted++;
			}
		}

		int notified = 0;
		for (UUID id : bookings.findPendingNotifications(batchSize)) {
			if (handle(id, booking -> compensations.notifyOutcome(id))) {
				notified++;
			}
		}

		Report report = new Report(undone, interrupted, notified);
		if (undone + interrupted + notified > 0) {
			log.info("Recovery run: {}", report);
		}
		return report;
	}

	private boolean undoInterrupted(Booking booking) {
		if (!booking.getStatus().isInProgress()) {
			return false; // finished between the query and the lock
		}
		store.update(booking.getId(), b -> b.startCompensation(FailureCode.SAGA_INTERRUPTED,
				"Interrupted in " + b.getStatus() + " and rolled back by the recovery job", clock.instant()));
		return compensations.undo(booking.getId());
	}

	/**
	 * Runs {@code work} on one booking inside a transaction holding its row lock.
	 *
	 * @return {@code true} only if the work finished; {@code false} if another transaction holds the
	 *         booking, or the work couldn't finish yet (it will be retried next run)
	 */
	private boolean handle(UUID id, Predicate<Booking> work) {
		try {
			Boolean done = transaction.execute(status -> bookings.lockIfFree(id)
					.map(work::test)
					.orElse(false));
			return Boolean.TRUE.equals(done);
		} catch (RuntimeException e) {
			log.warn("Recovery of booking {} failed, will retry: {}", id, e.toString());
			return false;
		}
	}
}
