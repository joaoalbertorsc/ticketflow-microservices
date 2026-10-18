package com.ticketflow.notification.api;

import com.ticketflow.notification.TestData;
import com.ticketflow.notification.application.NotificationService;
import com.ticketflow.notification.application.NotificationService.SendCommand;
import com.ticketflow.notification.application.NotificationService.SendResult;
import com.ticketflow.notification.application.template.NotificationTemplate.MissingTemplateDataException;
import com.ticketflow.notification.domain.Notification;
import com.ticketflow.notification.domain.NotificationType;
import com.ticketflow.notification.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class NotificationControllersTest {

	private static final UUID BOOKING = UUID.randomUUID();
	private static final UUID USER = UUID.randomUUID();
	private static final String BODY = """
			{
			  "bookingId": "%s",
			  "type": "BOOKING_CONFIRMED",
			  "recipient": {"name": "Ana Souza", "email": "ana@mail.com"},
			  "data": {"eventName": "Hamlet", "quantity": 2, "totalAmount": 240.00}
			}
			""".formatted(BOOKING);

	@Autowired
	MockMvc mvc;

	@MockitoBean
	NotificationService notifications;

	private static RequestPostProcessor user(UUID id) {
		return jwt().jwt(token -> token.subject(id.toString())).authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private static RequestPostProcessor admin() {
		return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	private static Notification sample() {
		return Notification.sending(BOOKING, USER, NotificationType.BOOKING_CONFIRMED, TestData.ANA,
				TestData.confirmedData(), "Your tickets for Hamlet are confirmed", "Hi Ana", Instant.now());
	}

	@Test
	void sendRecordsTheCallerAndAcceptsNumbersAsData() throws Exception {
		when(notifications.send(any())).thenReturn(new SendResult(sample(), true));

		mvc.perform(post("/internal/notifications").with(user(USER)).contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.subject").value("Your tickets for Hamlet are confirmed"))
				.andExpect(jsonPath("$.channel").value("EMAIL"));

		ArgumentCaptor<SendCommand> command = ArgumentCaptor.forClass(SendCommand.class);
		verify(notifications).send(command.capture());
		assertThat(command.getValue().userId()).isEqualTo(USER);
		assertThat(command.getValue().data()).containsEntry("quantity", "2").containsEntry("totalAmount", "240.00");
	}

	@Test
	void repeatedSendIs200() throws Exception {
		when(notifications.send(any())).thenReturn(new SendResult(sample(), false));

		mvc.perform(post("/internal/notifications").with(user(USER)).contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isOk());
	}

	@Test
	void invalidRecipientAndUnknownTypeAreRejected() throws Exception {
		mvc.perform(post("/internal/notifications").with(user(USER)).contentType(MediaType.APPLICATION_JSON)
						.content(BODY.replace("ana@mail.com", "not-an-email")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['recipient.email']").exists());
		mvc.perform(post("/internal/notifications").with(user(USER)).contentType(MediaType.APPLICATION_JSON)
						.content(BODY.replace("BOOKING_CONFIRMED", "BIRTHDAY")))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(notifications);
	}

	@Test
	void missingTemplateDataIs400WithTheFieldNames() throws Exception {
		when(notifications.send(any())).thenThrow(
				new MissingTemplateDataException(NotificationType.BOOKING_CONFIRMED, Set.of("venueName")));

		mvc.perform(post("/internal/notifications").with(user(USER)).contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.missing[0]").value("venueName"));
	}

	@Test
	void sendRequiresAToken() throws Exception {
		mvc.perform(post("/internal/notifications").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void myNotificationsAreLookedUpByTheTokenSubject() throws Exception {
		when(notifications.forUser(eq(USER), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(sample())));

		mvc.perform(get("/api/notifications/me").with(user(USER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].bookingId").value(BOOKING.toString()))
				.andExpect(jsonPath("$.totalElements").value(1));
		verify(notifications).forUser(USER, 0, 20);
	}

	@Test
	void adminSearchIsAdminOnly() throws Exception {
		mvc.perform(get("/api/admin/notifications").with(user(USER))).andExpect(status().isForbidden());

		when(notifications.search(BOOKING, 0, 20)).thenReturn(new PageImpl<>(List.of(sample())));
		mvc.perform(get("/api/admin/notifications").param("bookingId", BOOKING.toString()).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1));
	}
}
