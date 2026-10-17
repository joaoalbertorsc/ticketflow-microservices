package com.ticketflow.auth.api.dto;

import com.ticketflow.auth.domain.Role;
import com.ticketflow.auth.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, Set<Role> roles, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail(),
				new TreeSet<>(user.getRoles()), user.getCreatedAt());
	}
}
