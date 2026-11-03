package com.ticketflow.catalog.application;

import com.ticketflow.catalog.TestData;
import com.ticketflow.catalog.application.NotFoundExceptions.EventNotFoundException;
import com.ticketflow.catalog.application.NotFoundExceptions.SectorNotFoundException;
import com.ticketflow.catalog.application.NotFoundExceptions.VenueNotFoundException;
import com.ticketflow.catalog.domain.Event;
import com.ticketflow.catalog.domain.EventRepository;
import com.ticketflow.catalog.domain.EventSearchCriteria;
import com.ticketflow.catalog.domain.EventStatus;
import com.ticketflow.catalog.domain.Venue;
import com.ticketflow.catalog.domain.VenueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.ticketflow.catalog.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServicesTest {

	@Mock
	EventRepository events;

	@Mock
	VenueRepository venues;

	Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	@Test
	void createCopiesTheVenueIntoTheEvent() {
		Venue venue = Venue.create("Parque Olimpico", "Rio de Janeiro", "Av. Embaixador Abelardo Bueno");
		ReflectionTestUtils.setField(venue, "id", "venue-1");
		when(venues.findById("venue-1")).thenReturn(Optional.of(venue));
		when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
		EventCommandService commands = new EventCommandService(events, venues, clock);
		Event.EventData data = TestData.concertData(List.of(TestData.pista()));

		EventView view = commands.create(new EventCommand(data.name(), data.description(), data.startsAt(),
				"venue-1", data.sectors(), data.details()));

		assertThat(view.status()).isEqualTo(EventStatus.DRAFT);
		assertThat(view.venue().name()).isEqualTo("Parque Olimpico");
		assertThat(view.venue().city()).isEqualTo("Rio de Janeiro");
	}

	@Test
	void createWithUnknownVenueFails() {
		when(venues.findById("nope")).thenReturn(Optional.empty());
		EventCommandService commands = new EventCommandService(events, venues, clock);
		Event.EventData data = TestData.concertData(List.of(TestData.pista()));

		assertThatThrownBy(() -> commands.create(new EventCommand(data.name(), null, data.startsAt(), "nope",
				data.sectors(), data.details()))).isInstanceOf(VenueNotFoundException.class);
		verify(events, never()).save(any());
	}

	@Test
	void publishSavesThePublishedEvent() {
		when(events.findById("event-1")).thenReturn(Optional.of(TestData.draftConcert()));
		when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
		EventCommandService commands = new EventCommandService(events, venues, clock);

		assertThat(commands.publish("event-1").status()).isEqualTo(EventStatus.PUBLISHED);
	}

	@Test
	void draftIsHiddenFromThePublicView() {
		when(events.findById("event-1")).thenReturn(Optional.of(TestData.draftConcert()));
		EventQueryService queries = new EventQueryService(events, clock);

		assertThatThrownBy(() -> queries.getPublishedEvent("event-1")).isInstanceOf(EventNotFoundException.class);
	}

	@Test
	void publicSearchDefaultsToPublishedAndUpcoming() {
		when(events.search(any(), any())).thenReturn(Page.empty());
		EventQueryService queries = new EventQueryService(events, clock);

		queries.searchPublished(null, "Rio de Janeiro", null, null, PageRequest.of(0, 20));

		ArgumentCaptor<EventSearchCriteria> criteria = ArgumentCaptor.forClass(EventSearchCriteria.class);
		verify(events).search(criteria.capture(), eq(PageRequest.of(0, 20)));
		assertThat(criteria.getValue().status()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(criteria.getValue().from()).isEqualTo(NOW);
		assertThat(criteria.getValue().city()).isEqualTo("Rio de Janeiro");
	}

	@Test
	void saleSnapshotCarriesPriceAndStatus() {
		when(events.findById("event-1")).thenReturn(Optional.of(TestData.publishedConcert()));
		EventQueryService queries = new EventQueryService(events, clock);

		SaleSnapshot snapshot = queries.getSaleSnapshot("event-1", "pista");

		assertThat(snapshot.unitPrice()).isEqualByComparingTo("450.00");
		assertThat(snapshot.currency()).isEqualTo("BRL");
		assertThat(snapshot.onSale(NOW)).isTrue();
		assertThat(snapshot.onSale(TestData.NEXT_MONTH)).isFalse();
	}

	@Test
	void saleSnapshotOfUnknownSectorFails() {
		when(events.findById("event-1")).thenReturn(Optional.of(TestData.publishedConcert()));
		EventQueryService queries = new EventQueryService(events, clock);

		assertThatThrownBy(() -> queries.getSaleSnapshot("event-1", "vip"))
				.isInstanceOf(SectorNotFoundException.class);
	}
}
