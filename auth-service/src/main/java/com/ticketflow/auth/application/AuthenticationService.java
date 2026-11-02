package com.ticketflow.auth.application;

import com.ticketflow.auth.domain.User;
import com.ticketflow.auth.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthenticationService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final TokenIssuer tokenIssuer;

	/**
	 * Hash checked when the e-mail is unknown, so a login attempt takes about the same time
	 * whether or not the account exists (prevents discovering e-mails by timing).
	 */
	private final String dummyHash;

	public AuthenticationService(UserRepository users, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokenIssuer = tokenIssuer;
		this.dummyHash = passwordEncoder.encode("timing-attack-guard");
	}

	@Transactional(readOnly = true)
	public IssuedToken authenticate(String email, String rawPassword) {
		Optional<User> user = users.findByEmail(User.normalizeEmail(email));
		String hash = user.map(User::getPasswordHash).orElse(dummyHash);
		boolean passwordMatches = passwordEncoder.matches(rawPassword, hash);
		if (user.isEmpty() || !passwordMatches) {
			throw new InvalidCredentialsException();
		}
		return tokenIssuer.issue(user.get());
	}
}
