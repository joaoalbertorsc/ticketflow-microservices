package com.ticketflow.payment.api;

import com.ticketflow.payment.application.ChaosMonkey;
import com.ticketflow.payment.application.ChaosMonkey.ChaosSettings;
import com.ticketflow.payment.application.PaymentExceptions.ChaosFailureException;
import com.ticketflow.payment.application.PaymentExceptions.PaymentNotFoundException;
import com.ticketflow.payment.application.PaymentService;
import com.ticketflow.payment.application.PaymentService.ChargeCommand;
import com.ticketflow.payment.application.PaymentService.ChargeResult;
import com.ticketflow.payment.domain.DeclineReason;
import com.ticketflow.payment.domain.Payment;
import com.ticketflow.payment.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class PaymentControllersTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final UUID BOOKING = UUID.randomUUID();
	private static final UUID OWNER = UUID.randomUUID();

	@Autowired
	MockMvc mvc;

	@MockitoBean
	PaymentService payments;

	@MockitoBean
	ChaosMonkey chaos;

	private static RequestPostProcessor userToken(UUID userId) {
		return jwt().jwt(token -> token.subject(userId.toString()).claim("roles", List.of("USER")))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private static RequestPostProcessor adminToken() {
		return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("roles", List.of("ADMIN")))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	private static String chargeBody(String amount, String currency) {
		return """
				{"bookingId": "%s", "amount": %s, "currency": "%s", "cardToken": "tok_visa"}
				""".formatted(BOOKING, amount, currency);
	}

	private static Payment approved() {
		return Payment.approved(BOOKING, OWNER, new BigDecimal("690.00"), "BRL", "tok_visa", "sim_1", NOW);
	}

	@Test
	void chargeRecordsTheCallerAsOwnerAndHidesTheCardToken() throws Exception {
		when(payments.charge(any())).thenReturn(new ChargeResult(approved(), true));

		mvc.perform(post("/internal/payments").with(userToken(OWNER))
						.contentType(MediaType.APPLICATION_JSON).content(chargeBody("690.00", "BRL")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.amount").value(690.00))
				.andExpect(jsonPath("$.cardToken").doesNotExist());

		ArgumentCaptor<ChargeCommand> command = ArgumentCaptor.forClass(ChargeCommand.class);
		verify(payments).charge(command.capture());
		assertThat(command.getValue().userId()).isEqualTo(OWNER);
	}

	@Test
	void declinedCardIsA201NotAnError() throws Exception {
		Payment declined = Payment.declined(BOOKING, OWNER, BigDecimal.TEN, "BRL", "tok_declined",
				DeclineReason.INSUFFICIENT_FUNDS, NOW);
		when(payments.charge(any())).thenReturn(new ChargeResult(declined, true));

		mvc.perform(post("/internal/payments").with(userToken(OWNER))
						.contentType(MediaType.APPLICATION_JSON).content(chargeBody("10.00", "BRL")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DECLINED"))
				.andExpect(jsonPath("$.declineReason").value("INSUFFICIENT_FUNDS"));
	}

	@Test
	void repeatedChargeIs200() throws Exception {
		when(payments.charge(any())).thenReturn(new ChargeResult(approved(), false));

		mvc.perform(post("/internal/payments").with(userToken(OWNER))
						.contentType(MediaType.APPLICATION_JSON).content(chargeBody("690.00", "BRL")))
				.andExpect(status().isOk());
	}

	@Test
	void invalidAmountsAndCurrenciesAreRejected() throws Exception {
		mvc.perform(post("/internal/payments").with(userToken(OWNER))
						.contentType(MediaType.APPLICATION_JSON).content(chargeBody("10.999", "USD")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.amount").exists())
				.andExpect(jsonPath("$.errors.currency").value("only BRL is supported"));
		mvc.perform(post("/internal/payments").with(userToken(OWNER))
						.contentType(MediaType.APPLICATION_JSON).content(chargeBody("0", "BRL")))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(payments);
	}

	@Test
	void chaosFailureIs503() throws Exception {
		when(payments.charge(any())).thenThrow(new ChaosFailureException());

		mvc.perform(post("/internal/payments").with(userToken(OWNER))
						.contentType(MediaType.APPLICATION_JSON).content(chargeBody("690.00", "BRL")))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void chargeRequiresAToken() throws Exception {
		mvc.perform(post("/internal/payments").contentType(MediaType.APPLICATION_JSON).content(chargeBody("1.00", "BRL")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenWhoseSubjectIsNotAUserIdIsRejected() throws Exception {
		mvc.perform(post("/internal/payments").with(jwt().jwt(token -> token.subject("not-a-uuid")))
						.contentType(MediaType.APPLICATION_JSON).content(chargeBody("1.00", "BRL")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void ownerAndAdminSeeThePaymentOthersGet404() throws Exception {
		when(payments.get(BOOKING)).thenReturn(approved());

		mvc.perform(get("/api/payments/booking/{id}", BOOKING).with(userToken(OWNER))).andExpect(status().isOk());
		mvc.perform(get("/api/payments/booking/{id}", BOOKING).with(adminToken())).andExpect(status().isOk());
		mvc.perform(get("/api/payments/booking/{id}", BOOKING).with(userToken(UUID.randomUUID())))
				.andExpect(status().isNotFound());
	}

	@Test
	void unknownPaymentIs404() throws Exception {
		when(payments.get(BOOKING)).thenThrow(new PaymentNotFoundException(BOOKING));

		mvc.perform(get("/api/payments/booking/{id}", BOOKING).with(adminToken())).andExpect(status().isNotFound());
	}

	@Test
	void chaosIsAdminOnlyEvenWithAnInvalidBody() throws Exception {
		mvc.perform(put("/api/payments/admin/chaos").with(userToken(OWNER))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(chaos);
	}

	@Test
	void adminTurnsChaosOnAndOff() throws Exception {
		when(chaos.current()).thenReturn(new ChaosSettings(0.5, 2000), ChaosSettings.OFF);

		mvc.perform(put("/api/payments/admin/chaos").with(adminToken())
						.contentType(MediaType.APPLICATION_JSON).content("{\"failureRate\": 0.5, \"latencyMs\": 2000}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(true));
		verify(chaos).set(new ChaosSettings(0.5, 2000));

		mvc.perform(delete("/api/payments/admin/chaos").with(adminToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false));
	}

	@Test
	void chaosValuesOutOfRangeAreRejected() throws Exception {
		mvc.perform(put("/api/payments/admin/chaos").with(adminToken())
						.contentType(MediaType.APPLICATION_JSON).content("{\"failureRate\": 1.5, \"latencyMs\": 20000}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.failureRate").exists())
				.andExpect(jsonPath("$.errors.latencyMs").exists());
	}
}
