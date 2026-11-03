package com.ticketflow.notification.application;

import com.ticketflow.notification.TestData;
import com.ticketflow.notification.application.EmailSender.DeliveryResult;
import com.ticketflow.notification.application.NotificationService.SendCommand;
import com.ticketflow.notification.application.NotificationService.SendResult;
import com.ticketflow.notification.application.template.BookingCancelledTemplate;
import com.ticketflow.notification.application.template.BookingConfirmedTemplate;
import com.ticketflow.notification.application.template.BookingFailedTemplate;
import com.ticketflow.notification.application.template.NotificationTemplate.MissingTemplateDataException;
import com.ticketflow.notification.application.template.TemplateRegistry;
import com.ticketflow.notification.domain.Notification;
import com.ticketflow.notification.domain.NotificationRepository;
import com.ticketflow.notification.domain.NotificationStatus;
import com.ticketflow.notification.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final UUID BOOKING = UUID.randomUUID();
	private static final UUID USER = UUID.randomUUID();

	@Mock
	NotificationRepository notifications;

	@Mock
	EmailSender sender;

	NotificationService service;

	@BeforeEach
	void setUp() {
		TemplateRegistry templates = new TemplateRegistry(List.of(
				new BookingConfirmedTemplate(), new BookingFailedTemplate(), new BookingCancelledTemplate()));
		service = new NotificationService(notifications, templates, sender, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static SendCommand confirmed() {
		return new SendCommand(BOOKING, USER, NotificationType.BOOKING_CONFIRMED, TestData.ANA, TestData.confirmedData());
	}

	@Test
	void newNotificationIsClaimedThenSentThenMarkedSent() {
		when(notifications.findByBookingIdAndType(BOOKING, NotificationType.BOOKING_CONFIRMED)).thenReturn(Optional.empty());
		when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(sender.send(anyString(), anyString(), anyString())).thenReturn(DeliveryResult.ok());

		SendResult result = service.send(confirmed());

		assertThat(result.created()).isTrue();
		assertThat(result.notification().getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(result.notification().getSentAt()).isEqualTo(NOW);
		assertThat(result.notification().getUserId()).isEqualTo(USER);
		verify(sender).send("ana@mail.com", "Your tickets for Hamlet are confirmed", result.notification().getBody());
	}

	@Test
	void bounceIsStoredAsFailedNotThrown() {
		when(notifications.findByBookingIdAndType(any(), any())).thenReturn(Optional.empty());
		when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(sender.send(anyString(), anyString(), anyString())).thenReturn(DeliveryResult.rejected("Mailbox does not exist"));

		Notification notification = service.send(confirmed()).notification();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(notification.getFailureReason()).isEqualTo("Mailbox does not exist");
		assertThat(notification.getSentAt()).isNull();
	}

	@Test
	void repeatedRequestReturnsTheStoredNotificationWithoutSending() {
		Notification existing = Notification.sending(BOOKING, USER, NotificationType.BOOKING_CONFIRMED, TestData.ANA,
				TestData.confirmedData(), "s", "b", NOW);
		when(notifications.findByBookingIdAndType(BOOKING, NotificationType.BOOKING_CONFIRMED)).thenReturn(Optional.of(existing));

		SendResult result = service.send(confirmed());

		assertThat(result.created()).isFalse();
		assertThat(result.notification()).isSameAs(existing);
		verify(sender, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void concurrentDuplicateThatLosesTheInsertNeverSends() {
		Notification winner = Notification.sending(BOOKING, USER, NotificationType.BOOKING_CONFIRMED, TestData.ANA,
				TestData.confirmedData(), "s", "b", NOW);
		when(notifications.findByBookingIdAndType(BOOKING, NotificationType.BOOKING_CONFIRMED))
				.thenReturn(Optional.empty(), Optional.of(winner));
		when(notifications.save(any(Notification.class))).thenThrow(new DuplicateKeyException("booking_type_unique"));

		SendResult result = service.send(confirmed());

		assertThat(result.created()).isFalse();
		assertThat(result.notification()).isSameAs(winner);
		verify(sender, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void missingTemplateDataIsRejectedBeforeAnythingIsStored() {
		when(notifications.findByBookingIdAndType(any(), any())).thenReturn(Optional.empty());
		SendCommand incomplete = new SendCommand(BOOKING, USER, NotificationType.BOOKING_FAILED, TestData.ANA,
				Map.of("eventName", "Hamlet"));

		assertThatThrownBy(() -> service.send(incomplete)).isInstanceOf(MissingTemplateDataException.class);
		verify(notifications, never()).save(any());
	}
}
