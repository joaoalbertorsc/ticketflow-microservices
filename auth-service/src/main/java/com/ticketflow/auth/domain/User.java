package com.ticketflow.auth.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Set<Role> roles = EnumSet.noneOf(Role.class);

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
		// required by JPA
	}

	private User(String name, String email, String passwordHash, Set<Role> roles) {
		this.name = name;
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.roles = EnumSet.copyOf(roles);
	}

	/** A newly registered user always starts with the {@link Role#USER} role. */
	public static User register(String name, String email, String passwordHash) {
		return new User(name, email, passwordHash, Set.of(Role.USER));
	}

	public static User withRoles(String name, String email, String passwordHash, Set<Role> roles) {
		if (roles.isEmpty()) {
			throw new IllegalArgumentException("a user needs at least one role");
		}
		return new User(name, email, passwordHash, roles);
	}

	/** E-mails are compared case-insensitively, so they are always stored lower-case. */
	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	public void replaceRoles(Set<Role> newRoles) {
		if (newRoles.isEmpty()) {
			throw new IllegalArgumentException("a user needs at least one role");
		}
		roles.clear();
		roles.addAll(newRoles);
	}

	public void grant(Role role) {
		roles.add(role);
	}

	public boolean hasRole(Role role) {
		return roles.contains(role);
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Set<Role> getRoles() {
		return Collections.unmodifiableSet(roles);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
