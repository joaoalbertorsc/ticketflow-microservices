package com.ticketflow.catalog;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.ticketflow.catalog.CatalogApi.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full application against real MongoDB and Redis (Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CatalogIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MongoTemplate mongo;

	@Autowired
	StringRedisTemplate redis;

	CatalogApi api;
	String city;

	@BeforeEach
	void setUp() {
		api = new CatalogApi(mvc);
		city = "City-" + UUID.randomUUID(); // isolates each test's events in the shared database
	}

	@Test
	void draftIsHiddenUntilPublishedThenFoundByCityIgnoringCase() throws Exception {
		String eventId = api.createConcert(api.createVenue(city));

		mvc.perform(get("/api/events/{id}", eventId)).andExpect(status().isNotFound());
		mvc.perform(get("/api/events").param("city", city)).andExpect(jsonPath("$.totalElements").value(0));

		api.publish(eventId);

		mvc.perform(get("/api/events/{id}", eventId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("CONCERT"))
				.andExpect(jsonPath("$.venue.city").value(city))
				.andExpect(jsonPath("$.details.type").value("CONCERT"))
				.andExpect(jsonPath("$.details.genre").value("Rock"));
		mvc.perform(get("/api/events").param("city", city.toUpperCase()))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(eventId));
	}

	@Test
	void eventIsStoredAsOneDocumentWithExactDecimalPrices() throws Exception {
		String eventId = api.createConcert(api.createVenue(city));

		Document raw = mongo.getCollection("events").find(new Document("_id", new ObjectId(eventId))).first();

		assertThat(raw).isNotNull();
		List<Document> sectors = raw.getList("sectors", Document.class);
		assertThat(sectors).hasSize(2);
		assertThat(sectors.get(1).get("price")).isEqualTo(Decimal128.parse("890.50"));
		Document details = raw.get("details", Document.class);
		assertThat(details.getString("genre")).isEqualTo("Rock");
		assertThat(details.getString("_class")).endsWith("ConcertDetails");
		assertThat(raw.get("venue", Document.class).getString("city")).isEqualTo(city);
	}

	@Test
	void publishedEventIsServedFromRedisAndEvictedWhenCancelled() throws Exception {
		String eventId = api.createConcert(api.createVenue(city));
		api.publish(eventId);

		mvc.perform(get("/api/events/{id}", eventId)).andExpect(jsonPath("$.name").value("Rock Night"));
		assertThat(redis.hasKey("events::" + eventId)).isTrue();

		// Change MongoDB behind the service's back: the next read still comes from the cache.
		mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(eventId))),
				Update.update("name", "Changed directly in MongoDB"), "events");
		mvc.perform(get("/api/events/{id}", eventId)).andExpect(jsonPath("$.name").value("Rock Night"));

		api.cancel(eventId);

		assertThat(redis.hasKey("events::" + eventId)).isFalse();
		mvc.perform(get("/api/events/{id}", eventId)).andExpect(status().isNotFound());
	}

	@Test
	void saleSnapshotIsCachedAndReflectsCancellation() throws Exception {
		String eventId = api.createConcert(api.createVenue(city));
		api.publish(eventId);

		mvc.perform(get("/internal/events/{id}/sectors/vip", eventId).header(HttpHeaders.AUTHORIZATION, bearer("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sectorName").value("VIP"))
				.andExpect(jsonPath("$.unitPrice").value(890.50))
				.andExpect(jsonPath("$.onSale").value(true));
		assertThat(redis.hasKey("event-sectors::" + eventId + ":vip")).isTrue();

		api.cancel(eventId);

		mvc.perform(get("/internal/events/{id}/sectors/vip", eventId).header(HttpHeaders.AUTHORIZATION, bearer("USER")))
				.andExpect(jsonPath("$.onSale").value(false));
	}

	@Test
	void unknownSectorReturns404() throws Exception {
		String eventId = api.createConcert(api.createVenue(city));

		mvc.perform(get("/internal/events/{id}/sectors/nope", eventId).header(HttpHeaders.AUTHORIZATION, bearer("USER")))
				.andExpect(status().isNotFound());
	}

	@Test
	void unknownVenueReturns404() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/events")
						.header(HttpHeaders.AUTHORIZATION, bearer("ADMIN"))
						.contentType("application/json")
						.content("""
								{"name": "X", "startsAt": "2099-01-01T20:00:00Z", "venueId": "missing",
								 "sectors": [], "details": {"type": "SPORTS", "homeTeam": "A", "awayTeam": "B", "league": "L"}}
								"""))
				.andExpect(status().isNotFound());
	}
}
