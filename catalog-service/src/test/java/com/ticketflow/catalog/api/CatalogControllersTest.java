package com.ticketflow.catalog.api;

import com.ticketflow.catalog.TestData;
import com.ticketflow.catalog.application.EventCommand;
import com.ticketflow.catalog.application.EventCommandService;
import com.ticketflow.catalog.application.EventQueryService;
import com.ticketflow.catalog.application.EventView;
import com.ticketflow.catalog.application.NotFoundExceptions.EventNotFoundException;
import com.ticketflow.catalog.application.PageResponse;
import com.ticketflow.catalog.application.SaleSnapshot;
import com.ticketflow.catalog.application.VenueService;
import com.ticketflow.catalog.domain.ConcertDetails;
import com.ticketflow.catalog.domain.EventStateException;
import com.ticketflow.catalog.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({SecurityConfig.class, CatalogControllersTest.FixedClock.class})
@ActiveProfiles("test")
class CatalogControllersTest {

	private static final String CONCERT_BODY = """
			{
			  "name": "Rock Night",
			  "startsAt": "2099-01-01T20:00:00Z",
			  "venueId": "venue-1",
			  "sectors": [{"id": "pista", "name": "Pista", "price": 450.00}],
			  "details": {"type": "CONCERT", "lineup": ["Band A"], "genre": "Rock"}
			}
			""";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	EventQueryService queries;

	@MockitoBean
	EventCommandService commands;

	@MockitoBean
	VenueService venues;

	@TestConfiguration
	static class FixedClock {

		@Bean
		Clock clock() {
			return Clock.fixed(TestData.NOW, ZoneOffset.UTC);
		}
	}

	private static RequestPostProcessor admin() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	private static RequestPostProcessor user() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Test
	void anyoneCanBrowseEventsWithoutAToken() throws Exception {
		EventView view = EventView.from(TestData.publishedConcert());
		when(queries.searchPublished(any(), any(), any(), any(), any()))
				.thenReturn(new PageResponse<>(List.of(view), 0, 20, 1, 1));

		mvc.perform(get("/api/events").param("city", "Rio de Janeiro"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].name").value("Rock Night"))
				.andExpect(jsonPath("$.content[0].details.type").value("CONCERT"))
				.andExpect(jsonPath("$.content[0].details.lineup[0]").value("Band A"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void pageSizeAbove100IsRejected() throws Exception {
		mvc.perform(get("/api/events").param("size", "500")).andExpect(status().isBadRequest());
	}

	@Test
	void unknownEventReturns404() throws Exception {
		when(queries.getPublishedEvent("nope")).thenThrow(new EventNotFoundException("nope"));

		mvc.perform(get("/api/events/nope"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Event not found: nope"));
	}

	@Test
	void creatingAnEventRequiresAToken() throws Exception {
		mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(CONCERT_BODY))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(commands);
	}

	@Test
	void regularUserCannotCreateEvents() throws Exception {
		mvc.perform(post("/api/events").with(user()).contentType(MediaType.APPLICATION_JSON).content(CONCERT_BODY))
				.andExpect(status().isForbidden());
		verifyNoInteractions(commands);
	}

	@Test
	void regularUserGets403EvenWithAnInvalidBody() throws Exception {
		mvc.perform(post("/api/events").with(user()).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/venues").with(user()).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/events").with(user())).andExpect(status().isForbidden());
	}

	@Test
	void adminCreatesEventAndDetailsAreReadAsTheRightType() throws Exception {
		when(commands.create(any())).thenReturn(EventView.from(TestData.draftConcert()));

		mvc.perform(post("/api/events").with(admin()).contentType(MediaType.APPLICATION_JSON).content(CONCERT_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DRAFT"));

		ArgumentCaptor<EventCommand> command = ArgumentCaptor.forClass(EventCommand.class);
		verify(commands).create(command.capture());
		assertThat(command.getValue().details()).isInstanceOf(ConcertDetails.class);
		assertThat(command.getValue().sectors().getFirst().price()).isEqualByComparingTo("450.00");
	}

	@Test
	void unknownDetailsTypeReturns400() throws Exception {
		String body = CONCERT_BODY.replace("\"CONCERT\"", "\"FESTIVAL\"");

		mvc.perform(post("/api/events").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(commands);
	}

	@Test
	void invalidFieldsInsideSectorsAndDetailsReturn400() throws Exception {
		String body = """
				{
				  "name": "",
				  "startsAt": "2000-01-01T20:00:00Z",
				  "venueId": "venue-1",
				  "sectors": [{"id": "Not A Slug", "name": "Pista", "price": -1}],
				  "details": {"type": "SPORTS", "homeTeam": "Flamengo", "awayTeam": "", "league": "Brasileirao"}
				}
				""";

		mvc.perform(post("/api/events").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name").exists())
				.andExpect(jsonPath("$.errors.startsAt").exists())
				.andExpect(jsonPath("$.errors['sectors[0].id']").exists())
				.andExpect(jsonPath("$.errors['sectors[0].price']").exists())
				.andExpect(jsonPath("$.errors['details.awayTeam']").exists());
	}

	@Test
	void publishingInTheWrongStatusReturns409() throws Exception {
		when(commands.publish("event-1")).thenThrow(new EventStateException("Only draft events can be published"));

		mvc.perform(post("/api/events/event-1/publish").with(admin()))
				.andExpect(status().isConflict());
	}

	@Test
	void internalSaleSnapshotRequiresAToken() throws Exception {
		mvc.perform(get("/internal/events/event-1/sectors/pista")).andExpect(status().isUnauthorized());
	}

	@Test
	void internalSaleSnapshotComputesOnSaleAtRequestTime() throws Exception {
		SaleSnapshot snapshot = SaleSnapshot.of(TestData.publishedConcert(), TestData.pista());
		when(queries.getSaleSnapshot(eq("event-1"), eq("pista"))).thenReturn(snapshot);

		mvc.perform(get("/internal/events/event-1/sectors/pista").with(user()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unitPrice").value(450.00))
				.andExpect(jsonPath("$.currency").value("BRL"))
				.andExpect(jsonPath("$.onSale").value(true))
				.andExpect(jsonPath("$.status").doesNotExist());
	}
}
