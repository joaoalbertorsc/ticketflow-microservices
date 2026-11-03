package com.ticketflow.notification.application;

import com.ticketflow.notification.application.EmailSender.DeliveryResult;
import com.ticketflow.notification.application.template.NotificationTemplate;
import com.ticketflow.notification.application.template.NotificationTemplate.RenderedMessage;
import com.ticketflow.notification.application.template.TemplateRegistry;
import com.ticketflow.notification.domain.Notification;
import com.ticketflow.notification.domain.NotificationRepository;
import com.ticketflow.notification.domain.NotificationType;
import com.ticketflow.notification.domain.Recipient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends at most one notification per (booking, type). The notification is stored as SENDING
 * before delivery: the unique index then guarantees that of two identical concurrent requests
 * only one ever reaches the e-mail sender.
 */
@Service
public class NotificationService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

	private final NotificationRepository notifications;
	private final TemplateRegistry templates;
	private final EmailSender sender;
	private final Clock clock;

	public NotificationService(NotificationRepository notifications, TemplateRegistry templates, EmailSender sender,
			Clock clock) {
		this.notifications = notifications;
		this.templates = templates;
		this.sender = sender;
		this.clock = clock;
	}

	public record SendCommand(UUID bookingId, UUID userId, NotificationType type, Recipient recipient,
			Map<String, String> data) {
	}

	/** @param created {@code false} when this notification already existed (a repeated call) */
	public record SendResult(Notification notification, boolean created) {
	}

	public SendResult send(SendCommand command) {
		Optional<Notification> existing = notifications.findByBookingIdAndType(command.bookingId(), command.type());
		if (existing.isPresent()) {
			return new SendResult(existing.get(), false);
		}

		NotificationTemplate template = templates.get(command.type());
		RenderedMessage message = template.render(command.recipient(), command.data()); // 400 if data is missing

		Notification claimed;
		try {
			claimed = notifications.save(Notification.sending(command.bookingId(), command.userId(), command.type(),
					command.recipient(), command.data(), message.subject(), message.body(), clock.instant()));
		} catch (DuplicateKeyException concurrentDuplicate) {
			// Another request for the same (booking, type) got there first; it does the sending.
			return new SendResult(notifications.findByBookingIdAndType(command.bookingId(), command.type()).orElseThrow(),
					false);
		}

		DeliveryResult delivery = sender.send(command.recipient().email(), message.subject(), message.body());
		if (delivery.delivered()) {
			claimed.markSent(clock.instant());
		} else {
			claimed.markFailed(delivery.failureReason());
		}
		return new SendResult(notifications.save(claimed), true);
	}

	public List<Notification> forBooking(UUID bookingId) {
		return notifications.findByBookingIdOrderByCreatedAt(bookingId);
	}

	public Page<Notification> forUser(UUID userId, int page, int size) {
		return notifications.findByUserId(userId, PageRequest.of(page, size, NEWEST_FIRST));
	}

	/** Admin search; {@code bookingId} is optional. */
	public Page<Notification> search(UUID bookingId, int page, int size) {
		PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
		return bookingId == null ? notifications.findAll(pageable) : notifications.findByBookingId(bookingId, pageable);
	}
}
