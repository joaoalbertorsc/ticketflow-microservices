package com.ticketflow.notification.api;

import com.ticketflow.notification.api.dto.Dtos.NotificationResponse;
import com.ticketflow.notification.api.dto.Dtos.PageResponse;
import com.ticketflow.notification.application.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class NotificationController {

	private final NotificationService notifications;

	public NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	/** Only the caller's own notifications, newest first. */
	@GetMapping("/api/notifications/me")
	public PageResponse<NotificationResponse> mine(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(notifications.forUser(CurrentUser.id(jwt), page, size), NotificationResponse::from);
	}

	@GetMapping("/api/admin/notifications")
	@PreAuthorize("hasRole('ADMIN')")
	public PageResponse<NotificationResponse> search(@RequestParam(required = false) UUID bookingId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(notifications.search(bookingId, page, size), NotificationResponse::from);
	}
}
