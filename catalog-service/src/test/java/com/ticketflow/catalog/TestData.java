package com.ticketflow.catalog;

import com.ticketflow.catalog.domain.ConcertDetails;
import com.ticketflow.catalog.domain.Event;
import com.ticketflow.catalog.domain.Sector;
import com.ticketflow.catalog.domain.VenueSnapshot;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class TestData {

	public static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	public static final Instant NEXT_MONTH = NOW.plus(Duration.ofDays(30));

	private TestData() {
	}

	public static Sector pista() {
		return new Sector("pista", "Pista", new BigDecimal("450.00"));
	}

	public static Event.EventData concertData(List<Sector> sectors) {
		return new Event.EventData("Rock Night", "Two bands", NEXT_MONTH,
				new VenueSnapshot("venue-1", "Parque Olimpico", "Rio de Janeiro"), sectors,
				new ConcertDetails(List.of("Band A", "Band B"), "Rock"));
	}

	/** A draft concert with one sector, with an id as if loaded from MongoDB. */
	public static Event draftConcert() {
		Event event = Event.draft(concertData(List.of(pista())), NOW);
		ReflectionTestUtils.setField(event, "id", "event-1");
		return event;
	}

	public static Event publishedConcert() {
		Event event = draftConcert();
		event.publish(NOW);
		return event;
	}
}
