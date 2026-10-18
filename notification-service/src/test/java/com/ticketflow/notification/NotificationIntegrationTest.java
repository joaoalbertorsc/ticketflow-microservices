package com.ticketflow.notification;

import com.ticketflow.notification.application.EmailSender;
import com.ticketflow.notification.application.NotificationService;
import com.ticketflow.notification.application.NotificationService.SendCommand;
import com.ticketflow.notification.application.NotificationService.SendResult;
import com.ticketflow.notification.domain.Notification;
import com.ticketflow.notification.domain.NotificationStatus;
import com.ticketflow.notification.domain.NotificationType;
import com.ticketflow.notification.domain.Recipient;
import org.bson.BsonBinarySubType;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Real MongoDB: the stored document, the unique index, and "never send twice" under concurrency. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NotificationIntegrationTest {

	@Autowired
	NotificationService notifications;

	@Autowired
	MongoTemplate mongo;

	@MockitoSpyBean
	EmailSender sender;

	private SendResult confirm(UUID bookingId, UUID userId, Recipient recipient) {
		return notifications.send(new SendCommand(bookingId, userId, NotificationType.BOOKING_CONFIRMED, recipient,
				TestData.confirmedData()));
	}

	@Test
	void storedAsOneSelfContainedDocument() {
		UUID booking = UUID.randomUUID();
		Notification sent = confirm(booking, UUID.randomUUID(), TestData.ANA).notification();

		Document raw = mongo.getCollection("notifications").find(new Document("_id", new ObjectId(sent.getId()))).first();

		assertThat(raw).isNotNull();
		assertThat(raw.getString("status")).isEqualTo("SENT");
		assertThat(raw.getString("subject")).isEqualTo("Your tickets for Hamlet are confirmed");
		assertThat(raw.getString("body")).contains("2 x Plateia");
		assertThat(raw.get("recipient", Document.class).getString("email")).isEqualTo("ana@mail.com");
		assertThat(raw.get("data", Document.class).getString("totalAmount")).isEqualTo("240.00");
		// UUIDs stored in the standard binary format (subtype 4), readable by any MongoDB client.
		// Read as raw BSON: a decoded Document would already turn it back into a java.util.UUID.
		BsonDocument bson = mongo.getCollection("notifications").withDocumentClass(BsonDocument.class)
				.find(new Document("_id", new ObjectId(sent.getId()))).first();
		assertThat(bson.getBinary("bookingId").getType()).isEqualTo(BsonBinarySubType.UUID_STANDARD.getValue());
		assertThat(raw.get("bookingId", UUID.class)).isEqualTo(booking);
	}

	@Test
	void uniqueBookingTypeIndexExists() {
		List<Document> indexes = mongo.getCollection("notifications").listIndexes().into(new ArrayList<>());

		assertThat(indexes).anySatisfy(index -> {
			assertThat(index.getString("name")).isEqualTo("booking_type_unique");
			assertThat(index.getBoolean("unique")).isTrue();
		});
	}

	@Test
	void sameBookingAndTypeIsSentOnceButOtherTypesAreSeparate() {
		UUID booking = UUID.randomUUID();
		UUID user = UUID.randomUUID();

		SendResult first = confirm(booking, user, TestData.ANA);
		SendResult again = confirm(booking, user, TestData.ANA);
		SendResult cancelled = notifications.send(new SendCommand(booking, user, NotificationType.BOOKING_CANCELLED,
				TestData.ANA, Map.of("eventName", "Hamlet", "totalAmount", "240.00", "currency", "BRL")));

		assertThat(first.created()).isTrue();
		assertThat(again.created()).isFalse();
		assertThat(again.notification().getId()).isEqualTo(first.notification().getId());
		assertThat(cancelled.created()).isTrue();
		assertThat(notifications.forBooking(booking)).extracting(Notification::getType)
				.containsExactly(NotificationType.BOOKING_CONFIRMED, NotificationType.BOOKING_CANCELLED);
	}

	@Test
	void bouncedAddressIsStoredAsFailed() {
		Notification notification = confirm(UUID.randomUUID(), UUID.randomUUID(),
				new Recipient("Ghost", "ghost@bounce.test")).notification();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(notification.getFailureReason()).isEqualTo("Mailbox does not exist");
	}

	@Test
	void concurrentIdenticalRequestsSendOneEmail() throws Exception {
		UUID booking = UUID.randomUUID();
		UUID user = UUID.randomUUID();
		Recipient recipient = new Recipient("Concurrent", "concurrent-" + booking + "@mail.com");

		CountDownLatch start = new CountDownLatch(1);
		List<Future<SendResult>> calls = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
			for (int i = 0; i < 10; i++) {
				calls.add(pool.submit(() -> {
					start.await();
					return confirm(booking, user, recipient);
				}));
			}
			start.countDown();
			int created = 0;
			for (Future<SendResult> call : calls) {
				if (call.get(30, TimeUnit.SECONDS).created()) {
					created++;
				}
			}
			assertThat(created).isEqualTo(1);
		}
		verify(sender, times(1)).send(eq(recipient.email()), anyString(), anyString());
		assertThat(notifications.forBooking(booking)).hasSize(1);
	}

	@Test
	void usersSeeOnlyTheirOwnNotificationsNewestFirst() {
		UUID ana = UUID.randomUUID();
		UUID bruno = UUID.randomUUID();
		UUID older = UUID.randomUUID();
		UUID newer = UUID.randomUUID();
		confirm(older, ana, TestData.ANA);
		confirm(UUID.randomUUID(), bruno, new Recipient("Bruno", "bruno@mail.com"));
		confirm(newer, ana, TestData.ANA);

		Page<Notification> page = notifications.forUser(ana, 0, 10);

		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getContent()).extracting(Notification::getBookingId).containsExactly(newer, older);
	}
}
