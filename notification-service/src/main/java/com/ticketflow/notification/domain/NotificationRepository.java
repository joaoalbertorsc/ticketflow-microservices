package com.ticketflow.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

	/** Throws {@code DuplicateKeyException} if this (booking, type) already exists. */
	Notification save(Notification notification);

	Optional<Notification> findByBookingIdAndType(UUID bookingId, NotificationType type);

	List<Notification> findByBookingIdOrderByCreatedAt(UUID bookingId);

	Page<Notification> findByUserId(UUID userId, Pageable pageable);

	Page<Notification> findByBookingId(UUID bookingId, Pageable pageable);

	Page<Notification> findAll(Pageable pageable);
}
