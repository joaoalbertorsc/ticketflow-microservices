package com.ticketflow.auth;

import com.ticketflow.auth.domain.Role;
import com.ticketflow.auth.domain.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Builds users as if they had been loaded from the database (id and timestamps set). */
public final class TestUsers {

	private TestUsers() {
	}

	public static User persisted(String name, String email, String passwordHash, Set<Role> roles) {
		User user = User.withRoles(name, email, passwordHash, roles);
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-01-01T10:00:00Z"));
		ReflectionTestUtils.setField(user, "updatedAt", Instant.parse("2026-01-01T10:00:00Z"));
		return user;
	}

	public static User persistedUser() {
		return persisted("Ana Souza", "ana@mail.com", "hash", Set.of(Role.USER));
	}
}
