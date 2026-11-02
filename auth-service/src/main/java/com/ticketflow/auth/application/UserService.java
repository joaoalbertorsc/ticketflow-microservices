package com.ticketflow.auth.application;

import com.ticketflow.auth.domain.Role;
import com.ticketflow.auth.domain.User;
import com.ticketflow.auth.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public User register(String name, String email, String rawPassword) {
		String normalizedEmail = User.normalizeEmail(email);
		if (users.existsByEmail(normalizedEmail)) {
			throw new EmailAlreadyRegisteredException(normalizedEmail);
		}
		return users.save(User.register(name.trim(), normalizedEmail, passwordEncoder.encode(rawPassword)));
	}

	@Transactional(readOnly = true)
	public User getById(UUID id) {
		return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
	}

	@Transactional
	public User replaceRoles(UUID id, Set<Role> roles) {
		User user = getById(id);
		user.replaceRoles(roles);
		return users.save(user);
	}
}
