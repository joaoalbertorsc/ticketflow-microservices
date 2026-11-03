package com.ticketflow.booking.application;

import com.ticketflow.booking.application.BookingExceptions.BookingNotFoundException;
import com.ticketflow.booking.domain.Booking;
import com.ticketflow.booking.domain.BookingRepository;
import com.ticketflow.booking.domain.BookingStatus;
import com.ticketflow.booking.domain.FailureCode;
import com.ticketflow.booking.domain.NotificationStatus;
import com.ticketflow.booking.domain.SagaStep;
import com.ticketflow.booking.domain.SagaStepName;
import com.ticketflow.booking.domain.StepOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read side. Views are built inside the transaction, so the saga log can be loaded lazily. */
@Service
public class BookingQueries {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

	private final BookingRepository bookings;

	public BookingQueries(BookingRepository bookings) {
		this.bookings = bookings;
	}

	public record StepView(SagaStepName step, StepOutcome outcome, String detail, Instant at) {

		static StepView from(SagaStep step) {
			return new StepView(step.getStep(), step.getOutcome(), step.getDetail(), step.getAt());
		}
	}

	public record BookingView(
			UUID id,
			UUID userId,
			BookingStatus status,
			FailureCode failureCode,
			String failureReason,
			String eventId,
			String eventName,
			Instant startsAt,
			String venueName,
			String sectorId,
			String sectorName,
			int quantity,
			BigDecimal unitPrice,
			BigDecimal totalAmount,
			String currency,
			UUID reservationId,
			UUID paymentId,
			NotificationStatus notificationStatus,
			Instant createdAt,
			Instant updatedAt,
			List<StepView> steps) {

		static BookingView from(Booking booking, boolean withSteps) {
			return new BookingView(booking.getId(), booking.getUserId(), booking.getStatus(), booking.getFailureCode(),
					booking.getFailureReason(), booking.getEventId(), booking.getEventName(), booking.getStartsAt(),
					booking.getVenueName(), booking.getSectorId(), booking.getSectorName(), booking.getQuantity(),
					booking.getUnitPrice(), booking.getTotalAmount(), booking.getCurrency(), booking.getReservationId(),
					booking.getPaymentId(), booking.getNotificationStatus(), booking.getCreatedAt(),
					booking.getUpdatedAt(), withSteps ? booking.getSteps().stream().map(StepView::from).toList() : null);
		}
	}

	/** Owner or admin; for anyone else the booking doesn't exist. */
	@Transactional(readOnly = true)
	public BookingView get(UUID bookingId, UUID callerId, boolean callerIsAdmin) {
		Booking booking = bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
		if (!callerIsAdmin && !booking.isOwnedBy(callerId)) {
			throw new BookingNotFoundException(bookingId);
		}
		return BookingView.from(booking, true);
	}

	@Transactional(readOnly = true)
	public Page<BookingView> mine(UUID userId, int page, int size) {
		return bookings.findByUserId(userId, PageRequest.of(page, size, NEWEST_FIRST))
				.map(booking -> BookingView.from(booking, false));
	}

	@Transactional(readOnly = true)
	public Page<BookingView> search(BookingStatus status, int page, int size) {
		PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
		Page<Booking> result = status == null ? bookings.findAll(pageable) : bookings.findByStatus(status, pageable);
		return result.map(booking -> BookingView.from(booking, false));
	}
}
