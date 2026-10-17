package com.ticketflow.auth.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * What the application needs from user storage. Implemented by Spring Data JPA in the
 * infrastructure layer, so use cases never depend on JPA directly.
 */
public interface UserRepository {

	User save(User user);

	Optional<User> findById(UUID id);

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByRole(Role role);
}
