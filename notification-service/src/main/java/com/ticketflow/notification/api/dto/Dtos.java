package com.ticketflow.notification.api.dto;

import com.ticketflow.notification.application.NotificationService.SendCommand;
import com.ticketflow.notification.domain.Notification;
import com.ticketflow.notification.domain.NotificationStatus;
import com.ticketflow.notification.domain.NotificationType;
import com.ticketflow.notification.domain.Recipient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Request and response bodies of the notification API. */
public final class Dtos {

	private Dtos() {
	}

	/** {@code data} values may be sent as JSON strings or numbers; they are stored as text. */
	public record SendRequest(
			@NotNull UUID bookingId,
			@NotNull NotificationType type,
			@NotNull @Valid Recipient recipient,
			@NotNull @Size(max = 20) Map<@NotBlank @Size(max = 40) String, @Size(max = 500) String> data) {

		public SendCommand toCommand(UUID userId) {
			return new SendCommand(bookingId, userId, type, recipient, data);
		}
	}

	public record NotificationResponse(
			String id,
			UUID bookingId,
			NotificationType type,
			String channel,
			Recipient recipient,
			String subject,
			String body,
			NotificationStatus status,
			String failureReason,
			Instant createdAt,
			Instant sentAt) {

		public static NotificationResponse from(Notification notification) {
			return new NotificationResponse(notification.getId(), notification.getBookingId(), notification.getType(),
					notification.getChannel(), notification.getRecipient(), notification.getSubject(),
					notification.getBody(), notification.getStatus(), notification.getFailureReason(),
					notification.getCreatedAt(), notification.getSentAt());
		}
	}

	/** Stable JSON shape for paged results (Spring's Page implementation is not meant to be serialized). */
	public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

		public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
			return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
					page.getSize(), page.getTotalElements(), page.getTotalPages());
		}
	}
}
