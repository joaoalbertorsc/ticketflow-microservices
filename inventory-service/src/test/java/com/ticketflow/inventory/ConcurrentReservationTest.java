package com.ticketflow.inventory;

import com.ticketflow.inventory.application.InventoryExceptions.NotEnoughSeatsException;
import com.ticketflow.inventory.application.ReservationService;
import com.ticketflow.inventory.application.ReservationService.ReserveCommand;
import com.ticketflow.inventory.application.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The core promise of this service: 50 people try to buy the last 10 seats at the same instant,
 * and exactly 10 succeed.
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=50")
@Import(IntegrationTestConfig.class)
@ActiveProfiles("test")
class ConcurrentReservationTest {

	private static final int SEATS = 10;
	private static final int BUYERS = 50;

	@Autowired
	StockService stocks;

	@Autowired
	ReservationService reservations;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void parallelReservationsNeverOversell() throws Exception {
		String eventId = UUID.randomUUID().toString().substring(0, 24);
		stocks.setCapacity(eventId, "pista", SEATS);

		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> attempts = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(BUYERS)) {
			for (int i = 0; i < BUYERS; i++) {
				attempts.add(pool.submit(() -> {
					start.await(); // release every thread at once
					try {
						reservations.reserve(new ReserveCommand(UUID.randomUUID(), eventId, "pista", 1));
						return true;
					} catch (NotEnoughSeatsException soldOut) {
						return false;
					}
				}));
			}
			start.countDown();

			int succeeded = 0;
			for (Future<Boolean> attempt : attempts) {
				if (attempt.get(30, TimeUnit.SECONDS)) {
					succeeded++;
				}
			}

			assertThat(succeeded).isEqualTo(SEATS);
		}
		assertThat(stocks.get(eventId, "pista").getAvailableSeats()).isZero();
		Integer held = jdbc.queryForObject(
				"SELECT COALESCE(SUM(quantity), 0) FROM reservations WHERE event_id = ? AND status = 'HELD'",
				Integer.class, eventId);
		assertThat(held).isEqualTo(SEATS);
	}
}
