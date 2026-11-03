package com.ticketflow.auth.api.dto;

import com.ticketflow.auth.domain.User;

import java.util.UUID;

/** The minimum another service needs to keep about a user (booking-service stores it as a snapshot). */
public record UserSnapshotResponse(UUID id, String name, String email) {

	public static UserSnapshotResponse from(User user) {
		return new UserSnapshotResponse(user.getId(), user.getName(), user.getEmail());
	}
}
