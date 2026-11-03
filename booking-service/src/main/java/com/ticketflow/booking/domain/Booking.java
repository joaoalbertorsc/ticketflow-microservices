package com.ticketflow.booking.domain;

import com.ticketflow.booking.domain.Snapshots.SaleSnapshot;
import com.ticketflow.booking.domain.Snapshots.UserSnapshot;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A booking and its saga state. Every transition of the saga is a method here, and each one checks
 * the current status, so an impossible sequence (e.g. confirming before paying) can't be written.
 */
@Entity
@Table(name = "bookings")
public class Booking {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "idempotency_key", length = 100)
	private String idempotencyKey;

	@Column(name = "event_id", nullable = false, length = 24)
	private String eventId;

	@Column(name = "sector_id", nullable = false, length = 40)
	private String sectorId;

	@Column(nullable = false)
	private int quantity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BookingStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "failure_code", length = 30)
	private FailureCode failureCode;

	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	@Column(name = "user_name", length = 100)
	private String userName;

	@Column(name = "user_email")
	private String userEmail;

	@Column(name = "event_name", length = 150)
	private String eventName;

	@Column(name = "starts_at")
	private Instant startsAt;

	@Column(name = "venue_name", length = 100)
	private String venueName;

	@Column(name = "sector_name", length = 60)
	private String sectorName;

	@Column(name = "unit_price", precision = 12, scale = 2)
	private BigDecimal unitPrice;

	@Column(name = "total_amount", precision = 12, scale = 2)
	private BigDecimal totalAmount;

	@Column(length = 3)
	private String currency;

	@Column(name = "reservation_id")
	private UUID reservationId;

	@Column(name = "payment_id")
	private UUID paymentId;

	/** Set before the charge call: from then on, compensation must include a refund. */
	@Column(name = "charge_attempted", nullable = false)
	private boolean chargeAttempted;

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_status", nullable = false, length = 10)
	private NotificationStatus notificationStatus;

	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<SagaStep> steps = new ArrayList<>();

	@Version
	private Long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Booking() {
		// required by JPA
	}

	public static Booking start(UUID userId, String idempotencyKey, String eventId, String sectorId, int quantity,
			Instant now) {
		Booking booking = new Booking();
		booking.id = UUID.randomUUID();
		booking.userId = userId;
		booking.idempotencyKey = idempotencyKey;
		booking.eventId = eventId;
		booking.sectorId = sectorId;
		booking.quantity = quantity;
		booking.status = BookingStatus.PENDING;
		booking.notificationStatus = NotificationStatus.NONE;
		booking.createdAt = now;
		booking.updatedAt = now;
		return booking;
	}

	// ---- saga log ----------------------------------------------------------------------------

	public void log(SagaStepName step, StepOutcome outcome, String detail, Instant now) {
		steps.add(new SagaStep(this, step, outcome, detail, now));
		updatedAt = now;
	}

	// ---- create saga -------------------------------------------------------------------------

	public void takeSnapshots(UserSnapshot user, SaleSnapshot sale, Instant now) {
		require(Set.of(BookingStatus.PENDING), "take snapshots");
		userName = user.name();
		userEmail = user.email();
		eventName = sale.eventName();
		startsAt = sale.startsAt();
		venueName = sale.venueName();
		sectorName = sale.sectorName();
		unitPrice = sale.unitPrice();
		currency = sale.currency();
		totalAmount = sale.unitPrice().multiply(BigDecimal.valueOf(quantity));
		updatedAt = now;
	}

	public void seatsReserved(UUID reservationId, Instant now) {
		move(Set.of(BookingStatus.PENDING), BookingStatus.SEATS_RESERVED, now);
		this.reservationId = reservationId;
	}

	public void chargeAttempted(Instant now) {
		require(Set.of(BookingStatus.SEATS_RESERVED), "charge");
		chargeAttempted = true;
		updatedAt = now;
	}

	public void paid(UUID paymentId, Instant now) {
		move(Set.of(BookingStatus.SEATS_RESERVED), BookingStatus.PAID, now);
		this.paymentId = paymentId;
	}

	public void confirmed(Instant now) {
		move(Set.of(BookingStatus.PAID), BookingStatus.CONFIRMED, now);
	}

	/** Fails a booking that holds nothing yet (nothing to undo). */
	public void failed(FailureCode code, String reason, Instant now) {
		move(Set.of(BookingStatus.PENDING), BookingStatus.FAILED, now);
		setFailure(code, reason);
	}

	/** Seats may be held or money taken: undo before the booking can be FAILED. */
	public void startCompensation(FailureCode code, String reason, Instant now) {
		move(Set.of(BookingStatus.PENDING, BookingStatus.SEATS_RESERVED, BookingStatus.PAID),
				BookingStatus.COMPENSATING, now);
		setFailure(code, reason);
	}

	public void compensated(Instant now) {
		move(Set.of(BookingStatus.COMPENSATING), BookingStatus.FAILED, now);
	}

	// ---- cancellation ------------------------------------------------------------------------

	public void startCancellation(Instant now) {
		require(Set.of(BookingStatus.CONFIRMED), "cancel");
		if (!startsAt.isAfter(now)) {
			throw new BookingStateException("The event has already started; the booking can't be cancelled");
		}
		move(Set.of(BookingStatus.CONFIRMED), BookingStatus.CANCELLING, now);
	}

	public void cancelled(Instant now) {
		move(Set.of(BookingStatus.CANCELLING), BookingStatus.CANCELLED, now);
	}

	// ---- notification ------------------------------------------------------------------------

	public void notificationSent(Instant now) {
		notificationStatus = NotificationStatus.SENT;
		updatedAt = now;
	}

	public void notificationPending(Instant now) {
		notificationStatus = NotificationStatus.PENDING;
		updatedAt = now;
	}

	/** Whether this booking has reached a state the user should be told about. */
	public boolean isNotifiable() {
		return status == BookingStatus.CONFIRMED || status == BookingStatus.FAILED || status == BookingStatus.CANCELLED;
	}

	// ---- helpers -----------------------------------------------------------------------------

	public boolean isOwnedBy(UUID candidate) {
		return userId.equals(candidate);
	}

	public boolean hasSnapshot() {
		return eventName != null;
	}

	private void setFailure(FailureCode code, String reason) {
		failureCode = code;
		failureReason = reason == null || reason.length() <= 500 ? reason : reason.substring(0, 497) + "...";
	}

	private void move(Set<BookingStatus> from, BookingStatus to, Instant now) {
		require(from, "move to " + to);
		status = to;
		updatedAt = now;
	}

	private void require(Set<BookingStatus> allowed, String action) {
		if (!allowed.contains(status)) {
			throw new BookingStateException("Cannot " + action + " a booking that is " + status);
		}
	}

	// ---- getters -----------------------------------------------------------------------------

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getEventId() {
		return eventId;
	}

	public String getSectorId() {
		return sectorId;
	}

	public int getQuantity() {
		return quantity;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public FailureCode getFailureCode() {
		return failureCode;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public String getUserName() {
		return userName;
	}

	public String getUserEmail() {
		return userEmail;
	}

	public String getEventName() {
		return eventName;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public String getVenueName() {
		return venueName;
	}

	public String getSectorName() {
		return sectorName;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public String getCurrency() {
		return currency;
	}

	public UUID getReservationId() {
		return reservationId;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public boolean isChargeAttempted() {
		return chargeAttempted;
	}

	public NotificationStatus getNotificationStatus() {
		return notificationStatus;
	}

	public List<SagaStep> getSteps() {
		return Collections.unmodifiableList(steps);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
