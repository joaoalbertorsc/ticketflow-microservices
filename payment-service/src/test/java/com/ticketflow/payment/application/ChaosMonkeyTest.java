package com.ticketflow.payment.application;

import com.ticketflow.payment.application.ChaosMonkey.ChaosSettings;
import com.ticketflow.payment.application.PaymentExceptions.ChaosFailureException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChaosMonkeyTest {

	/** A monkey whose "random" roll is always {@code roll}. */
	private static ChaosMonkey monkeyRolling(double roll) {
		return new ChaosMonkey(() -> roll);
	}

	@Test
	void offByDefault() {
		ChaosMonkey monkey = new ChaosMonkey();

		assertThat(monkey.current().enabled()).isFalse();
		assertThatCode(monkey::beforeCharge).doesNotThrowAnyException();
	}

	@Test
	void failsWhenTheRollIsBelowTheFailureRate() {
		ChaosMonkey monkey = monkeyRolling(0.3);
		monkey.set(new ChaosSettings(0.5, 0));

		assertThatThrownBy(monkey::beforeCharge).isInstanceOf(ChaosFailureException.class);
	}

	@Test
	void passesWhenTheRollIsAboveTheFailureRate() {
		ChaosMonkey monkey = monkeyRolling(0.7);
		monkey.set(new ChaosSettings(0.5, 0));

		assertThatCode(monkey::beforeCharge).doesNotThrowAnyException();
	}

	@Test
	void addsLatency() {
		ChaosMonkey monkey = monkeyRolling(0.9);
		monkey.set(new ChaosSettings(0, 200));

		long start = System.nanoTime();
		monkey.beforeCharge();

		assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(190);
	}

	@Test
	void resetTurnsItOff() {
		ChaosMonkey monkey = monkeyRolling(0.0);
		monkey.set(new ChaosSettings(1.0, 0));

		monkey.reset();

		assertThatCode(monkey::beforeCharge).doesNotThrowAnyException();
	}
}
