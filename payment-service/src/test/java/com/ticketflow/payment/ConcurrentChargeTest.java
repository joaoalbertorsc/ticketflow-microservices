package com.ticketflow.payment;

import com.ticketflow.payment.application.PaymentGateway;
import com.ticketflow.payment.application.PaymentService;
import com.ticketflow.payment.application.PaymentService.ChargeCommand;
import com.ticketflow.payment.application.PaymentService.ChargeResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The orchestrator retries aggressively after timeouts: 20 identical charges for one booking
 * arrive at the same instant. The card must be charged exactly once.
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=25")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ConcurrentChargeTest {

	private static final int CALLS = 20;

	@Autowired
	PaymentService payments;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean
	PaymentGateway gateway;

	@Test
	void parallelIdenticalChargesHitTheGatewayOnce() throws Exception {
		UUID booking = UUID.randomUUID();
		ChargeCommand command = new ChargeCommand(booking, UUID.randomUUID(), new BigDecimal("690.00"), "BRL", "tok_visa");

		CountDownLatch start = new CountDownLatch(1);
		List<Future<ChargeResult>> calls = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(CALLS)) {
			for (int i = 0; i < CALLS; i++) {
				calls.add(pool.submit(() -> {
					start.await();
					return payments.charge(command);
				}));
			}
			start.countDown();

			List<ChargeResult> results = new ArrayList<>();
			for (Future<ChargeResult> call : calls) {
				results.add(call.get(30, TimeUnit.SECONDS));
			}

			assertThat(results).filteredOn(ChargeResult::created).hasSize(1);
			assertThat(results).extracting(result -> result.payment().getId()).containsOnly(results.getFirst().payment().getId());
		}
		verify(gateway, times(1)).charge(any(), any(), any());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE booking_id = ?", Integer.class, booking))
				.isEqualTo(1);
	}
}
