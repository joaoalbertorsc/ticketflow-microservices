package com.ticketflow.notification.application.template;

import com.ticketflow.notification.TestData;
import com.ticketflow.notification.application.template.NotificationTemplate.MissingTemplateDataException;
import com.ticketflow.notification.application.template.NotificationTemplate.RenderedMessage;
import com.ticketflow.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplatesTest {

	@Test
	void confirmationRendersEveryDetail() {
		RenderedMessage message = new BookingConfirmedTemplate().render(TestData.ANA, TestData.confirmedData());

		assertThat(message.subject()).isEqualTo("Your tickets for Hamlet are confirmed");
		assertThat(message.body())
				.contains("Hi Ana Souza,")
				.contains("2 x Plateia")
				.contains("BRL 240.00")
				.contains("Theatro Municipal")
				.doesNotContain("{");
	}

	@Test
	void requiredFieldsComeFromThePlaceholders() {
		assertThat(new BookingConfirmedTemplate().requiredFields())
				.containsExactly("currency", "eventName", "quantity", "sectorName", "startsAt", "totalAmount", "venueName");
		assertThat(new BookingFailedTemplate().requiredFields()).containsExactly("eventName", "reason");
		assertThat(new BookingCancelledTemplate().requiredFields()).containsExactly("currency", "eventName", "totalAmount");
	}

	@Test
	void missingOrBlankFieldsAreReported() {
		Map<String, String> data = Map.of("eventName", "Hamlet", "reason", " ");

		assertThatThrownBy(() -> new BookingFailedTemplate().render(TestData.ANA, data))
				.isInstanceOfSatisfying(MissingTemplateDataException.class,
						ex -> assertThat(ex.getMissing()).containsExactly("reason"));
	}

	@Test
	void valuesWithRegexCharactersAreInsertedLiterally() {
		RenderedMessage message = new BookingFailedTemplate()
				.render(TestData.ANA, Map.of("eventName", "Show $1 \\o/", "reason", "Card declined"));

		assertThat(message.subject()).isEqualTo("We couldn't complete your booking for Show $1 \\o/");
	}

	@Test
	void registryHasATemplateForEveryTypeAndRejectsGaps() {
		TemplateRegistry registry = new TemplateRegistry(List.of(
				new BookingConfirmedTemplate(), new BookingFailedTemplate(), new BookingCancelledTemplate()));
		assertThat(registry.get(NotificationType.BOOKING_FAILED)).isInstanceOf(BookingFailedTemplate.class);

		assertThatThrownBy(() -> new TemplateRegistry(List.of(new BookingConfirmedTemplate())))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("BOOKING_FAILED");
	}
}
