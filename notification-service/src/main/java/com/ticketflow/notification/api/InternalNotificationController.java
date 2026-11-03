package com.ticketflow.notification.api;

import com.ticketflow.notification.api.dto.Dtos.NotificationResponse;
import com.ticketflow.notification.api.dto.Dtos.SendRequest;
import com.ticketflow.notification.application.NotificationService;
import com.ticketflow.notification.application.NotificationService.SendResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Called by booking-service, which forwards the user's token: the notification is recorded as
 * belonging to that user. Not routed by the gateway.
 */
@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

	private final NotificationService notifications;

	public InternalNotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	/**
	 * {@code 201} for a new notification, SENT or FAILED (a bounce is a delivery result, not an
	 * error); {@code 200} when this booking already had it.
	 */
	@PostMapping
	public ResponseEntity<NotificationResponse> send(@Valid @RequestBody SendRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		SendResult result = notifications.send(request.toCommand(CurrentUser.id(jwt)));
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(NotificationResponse.from(result.notification()));
	}

	@GetMapping("/booking/{bookingId}")
	public List<NotificationResponse> forBooking(@PathVariable UUID bookingId) {
		return notifications.forBooking(bookingId).stream().map(NotificationResponse::from).toList();
	}
}
