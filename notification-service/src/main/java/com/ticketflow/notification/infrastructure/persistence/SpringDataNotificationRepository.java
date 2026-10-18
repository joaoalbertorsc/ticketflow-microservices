package com.ticketflow.notification.infrastructure.persistence;

import com.ticketflow.notification.domain.Notification;
import com.ticketflow.notification.domain.NotificationRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Every query is a derived Spring Data query; the unique index comes from {@link Notification}. */
interface SpringDataNotificationRepository extends MongoRepository<Notification, String>, NotificationRepository {
}
