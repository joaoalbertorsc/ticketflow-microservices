package com.ticketflow.payment.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;

/**
 * Injects latency and failures into charges on demand, to demonstrate the circuit breaker in
 * booking-service. Held in memory: per instance, and off again after a restart.
 */
@Component
public class ChaosMonkey {

	private static final Logger log = LoggerFactory.getLogger(ChaosMonkey.class);

	private final AtomicReference<ChaosSettings> settings = new AtomicReference<>(ChaosSettings.OFF);

	/**
	 * Returns a number in [0, 1). Uses ThreadLocalRandom, not RandomGenerator.getDefault(): the
	 * slim container JRE has no jdk.random module.
	 */
	private final DoubleSupplier random;

	public ChaosMonkey() {
		this(() -> ThreadLocalRandom.current().nextDouble());
	}

	ChaosMonkey(DoubleSupplier random) {
		this.random = random;
	}

	/**
	 * @param failureRate share of charges that fail, 0 to 1
	 * @param latencyMs   delay added to every charge
	 */
	public record ChaosSettings(double failureRate, long latencyMs) {

		public static final ChaosSettings OFF = new ChaosSettings(0, 0);

		public boolean enabled() {
			return failureRate > 0 || latencyMs > 0;
		}
	}

	public ChaosSettings current() {
		return settings.get();
	}

	public void set(ChaosSettings newSettings) {
		settings.set(newSettings);
		log.warn("Chaos mode {}: failureRate={}, latencyMs={}", newSettings.enabled() ? "ON" : "OFF",
				newSettings.failureRate(), newSettings.latencyMs());
	}

	public void reset() {
		set(ChaosSettings.OFF);
	}

	/** Called at the start of every charge, before anything is written. */
	public void beforeCharge() {
		ChaosSettings chaos = settings.get();
		if (chaos.latencyMs() > 0) {
			try {
				Thread.sleep(chaos.latencyMs());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		if (chaos.failureRate() > 0 && random.getAsDouble() < chaos.failureRate()) {
			throw new PaymentExceptions.ChaosFailureException();
		}
	}
}
