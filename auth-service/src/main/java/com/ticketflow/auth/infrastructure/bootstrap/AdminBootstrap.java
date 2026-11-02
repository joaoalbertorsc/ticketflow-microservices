package com.ticketflow.auth.infrastructure.bootstrap;

import com.ticketflow.auth.domain.Role;
import com.ticketflow.auth.domain.User;
import com.ticketflow.auth.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumSet;

/**
 * Creates the first admin from {@code ticketflow.admin.*} when no admin exists yet, so admin
 * credentials never live in migrations or source code. Does nothing if they are not configured.
 */
@Component
@EnableConfigurationProperties(AdminBootstrap.AdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final AdminProperties properties;

	public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, AdminProperties properties) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
			log.info("No initial admin configured (ticketflow.admin.email/password); skipping");
			return;
		}
		if (users.existsByRole(Role.ADMIN)) {
			return;
		}
		String email = User.normalizeEmail(properties.email());
		User admin = users.findByEmail(email)
				.map(existing -> {
					existing.grant(Role.ADMIN);
					return existing;
				})
				.orElseGet(() -> User.withRoles("Administrator", email,
						passwordEncoder.encode(properties.password()), EnumSet.of(Role.USER, Role.ADMIN)));
		users.save(admin);
		log.info("Initial admin ready: {}", email);
	}

	@ConfigurationProperties("ticketflow.admin")
	public record AdminProperties(String email, String password) {
	}
}
