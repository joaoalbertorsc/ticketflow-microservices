package com.ticketflow.booking;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import java.util.UUID;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * The downstream services' answers, written exactly as each service documents them (status codes
 * and JSON bodies), so the clients are tested against the real contracts.
 */
public class DownstreamStubs {

	public static final String EVENT_ID = "6650f1aa0000000000000001";
	public static final String SECTOR_ID = "plateia";

	public static final Supplier<MappingBuilder> USER = () -> get(urlPathMatching("/internal/users/.+"));
	public static final Supplier<MappingBuilder> SALE = () -> get(urlPathMatching("/internal/events/.+/sectors/.+"));
	public static final Supplier<MappingBuilder> RESERVE = () -> post(urlPathEqualTo("/internal/reservations"));
	public static final Supplier<MappingBuilder> CONFIRM = () -> post(urlPathMatching("/internal/reservations/booking/.+/confirm"));
	public static final Supplier<MappingBuilder> RELEASE = () -> post(urlPathMatching("/internal/reservations/booking/.+/release"));
	public static final Supplier<MappingBuilder> CHARGE = () -> post(urlPathEqualTo("/internal/payments"));
	public static final Supplier<MappingBuilder> REFUND = () -> post(urlPathMatching("/internal/payments/booking/.+/refund"));
	public static final Supplier<MappingBuilder> NOTIFY = () -> post(urlPathEqualTo("/internal/notifications"));

	private final WireMockServer server;

	DownstreamStubs(WireMockServer server) {
		this.server = server;
	}

	private static ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}

	private static ResponseDefinitionBuilder problem(int status, String title, String detail) {
		return json(status, """
				{"type":"about:blank","title":"%s","status":%d,"detail":"%s"}""".formatted(title, status, detail));
	}

	/** Endpoints are suppliers: a WireMock MappingBuilder keeps its stub id once built, so each stub needs a new one. */
	public void stub(Supplier<MappingBuilder> request, ResponseDefinitionBuilder response) {
		server.stubFor(request.get().willReturn(response));
	}

	/** Every service answers the happy path. */
	public DownstreamStubs allHealthy() {
		stub(USER, json(200, """
				{"id":"%s","name":"Ana Souza","email":"ana@mail.com"}""".formatted(UUID.randomUUID())));
		stub(SALE, json(200, """
				{"eventId":"%s","eventName":"Hamlet","startsAt":"2099-12-01T20:00:00Z","venueName":"Theatro Municipal",
				 "sectorId":"%s","sectorName":"Plateia","unitPrice":120.00,"currency":"BRL","onSale":true}"""
				.formatted(EVENT_ID, SECTOR_ID)));
		stub(RESERVE, json(201, """
				{"reservationId":"%s","bookingId":"%s","status":"HELD"}""".formatted(UUID.randomUUID(), UUID.randomUUID())));
		stub(CONFIRM, json(200, """
				{"reservationId":"%s","status":"CONFIRMED"}""".formatted(UUID.randomUUID())));
		stub(RELEASE, json(200, """
				{"reservationId":"%s","status":"RELEASED"}""".formatted(UUID.randomUUID())));
		chargeApproved();
		stub(REFUND, json(200, """
				{"paymentId":"%s","status":"REFUNDED"}""".formatted(UUID.randomUUID())));
		stub(NOTIFY, json(201, """
				{"id":"n1","status":"SENT"}"""));
		return this;
	}

	public void chargeApproved() {
		stub(CHARGE, json(201, """
				{"paymentId":"%s","status":"APPROVED","declineReason":null}""".formatted(UUID.randomUUID())));
	}

	public void chargeDeclined() {
		stub(CHARGE, json(201, """
				{"paymentId":"%s","status":"DECLINED","declineReason":"INSUFFICIENT_FUNDS"}""".formatted(UUID.randomUUID())));
	}

	public void soldOut() {
		stub(RESERVE, problem(409, "Not enough seats", "Requested 2 seats but only 1 are available"));
	}

	public void down(Supplier<MappingBuilder> request) {
		stub(request, serviceUnavailable());
	}

	public void slow(Supplier<MappingBuilder> request, int delayMillis) {
		stub(request, json(201, "{}").withFixedDelay(delayMillis));
	}
}
