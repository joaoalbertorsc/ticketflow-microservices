package com.ticketflow.catalog.domain;

import com.ticketflow.catalog.TestData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static com.ticketflow.catalog.TestData.NEXT_MONTH;
import static com.ticketflow.catalog.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

	@Test
	void newEventIsADraftWithTypeTakenFromDetails() {
		Event event = TestData.draftConcert();

		assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
		assertThat(event.getType()).isEqualTo(EventType.CONCERT);
		assertThat(event.isOnSale(NOW)).isFalse();
	}

	@Test
	void eventInThePastIsRejected() {
		Event.EventData data = TestData.concertData(List.of(TestData.pista()));
		Event.EventData past = new Event.EventData(data.name(), data.description(), NOW.minusSeconds(1),
				data.venue(), data.sectors(), data.details());

		assertThatThrownBy(() -> Event.draft(past, NOW)).isInstanceOf(InvalidEventException.class);
	}

	@Test
	void duplicateSectorIdsAreRejected() {
		Sector again = new Sector("pista", "Pista 2", new BigDecimal("100.00"));

		assertThatThrownBy(() -> Event.draft(TestData.concertData(List.of(TestData.pista(), again)), NOW))
				.isInstanceOf(InvalidEventException.class)
				.hasMessageContaining("pista");
	}

	@Test
	void publishedEventIsOnSaleUntilItStarts() {
		Event event = TestData.publishedConcert();

		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.isOnSale(NOW)).isTrue();
		assertThat(event.isOnSale(NEXT_MONTH)).isFalse();
	}

	@Test
	void eventWithoutSectorsCannotBePublished() {
		Event event = Event.draft(TestData.concertData(List.of()), NOW);

		assertThatThrownBy(() -> event.publish(NOW)).isInstanceOf(EventStateException.class);
	}

	@Test
	void eventThatAlreadyStartedCannotBePublished() {
		Event event = TestData.draftConcert();

		assertThatThrownBy(() -> event.publish(NEXT_MONTH.plus(Duration.ofMinutes(1))))
				.isInstanceOf(EventStateException.class);
	}

	@Test
	void publishedEventCannotBeEditedOrPublishedAgain() {
		Event event = TestData.publishedConcert();

		assertThatThrownBy(() -> event.edit(TestData.concertData(List.of(TestData.pista())), NOW))
				.isInstanceOf(EventStateException.class);
		assertThatThrownBy(() -> event.publish(NOW)).isInstanceOf(EventStateException.class);
	}

	@Test
	void cancelledEventIsNotOnSaleAndCannotBeCancelledTwice() {
		Event event = TestData.publishedConcert();

		event.cancel(NOW);

		assertThat(event.isOnSale(NOW)).isFalse();
		assertThatThrownBy(() -> event.cancel(NOW)).isInstanceOf(EventStateException.class);
	}

	@Test
	void draftCanBeEdited() {
		Event event = TestData.draftConcert();
		Sector vip = new Sector("vip", "VIP", new BigDecimal("900.00"));

		event.edit(TestData.concertData(List.of(TestData.pista(), vip)), NOW);

		assertThat(event.findSector("vip")).isPresent();
	}
}
