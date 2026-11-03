package com.ticketflow.notification;

import com.ticketflow.notification.domain.Recipient;

import java.util.Map;

public final class TestData {

	public static final Recipient ANA = new Recipient("Ana Souza", "ana@mail.com");

	private TestData() {
	}

	public static Map<String, String> confirmedData() {
		return Map.of(
				"eventName", "Hamlet",
				"startsAt", "2026-12-01T20:00:00Z",
				"venueName", "Theatro Municipal",
				"sectorName", "Plateia",
				"quantity", "2",
				"totalAmount", "240.00",
				"currency", "BRL");
	}
}
