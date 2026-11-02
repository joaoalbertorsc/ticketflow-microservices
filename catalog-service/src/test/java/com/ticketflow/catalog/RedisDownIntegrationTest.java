package com.ticketflow.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis is unreachable (nothing listens on the configured port). The catalog must keep working,
 * reading straight from MongoDB: the cache is an optimisation, not a dependency.
 */
@SpringBootTest(properties = {"spring.data.redis.host=localhost", "spring.data.redis.port=1"})
@AutoConfigureMockMvc
@Import(MongoOnlyTestcontainersConfiguration.class)
@ActiveProfiles("test")
class RedisDownIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Test
	void eventsAreStillServedFromMongoDb() throws Exception {
		CatalogApi api = new CatalogApi(mvc);
		String eventId = api.createConcert(api.createVenue("City-" + UUID.randomUUID()));
		api.publish(eventId);

		for (int i = 0; i < 2; i++) {
			mvc.perform(get("/api/events/{id}", eventId))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.name").value("Rock Night"));
		}
		api.cancel(eventId);
		mvc.perform(get("/api/events/{id}", eventId)).andExpect(status().isNotFound());
	}

	@Test
	void readinessIgnoresRedis() throws Exception {
		mvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}
}
