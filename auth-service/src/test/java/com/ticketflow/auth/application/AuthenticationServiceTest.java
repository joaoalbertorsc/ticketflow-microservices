package com.ticketflow.auth.application;

import com.ticketflow.auth.TestUsers;
import com.ticketflow.auth.domain.User;
import com.ticketflow.auth.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

	@Mock
	UserRepository users;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	TokenIssuer tokenIssuer;

	AuthenticationService authenticationService;

	@BeforeEach
	void setUp() {
		when(passwordEncoder.encode(any())).thenReturn("dummy-hash");
		authenticationService = new AuthenticationService(users, passwordEncoder, tokenIssuer);
	}

	@Test
	void validCredentialsIssueAToken() {
		User user = TestUsers.persistedUser();
		IssuedToken token = new IssuedToken("jwt", Duration.ofHours(1));
		when(users.findByEmail("ana@mail.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Str0ngPass", "hash")).thenReturn(true);
		when(tokenIssuer.issue(user)).thenReturn(token);

		assertThat(authenticationService.authenticate("Ana@Mail.com", "Str0ngPass")).isEqualTo(token);
	}

	@Test
	void wrongPasswordIsRejected() {
		when(users.findByEmail("ana@mail.com")).thenReturn(Optional.of(TestUsers.persistedUser()));
		when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

		assertThatThrownBy(() -> authenticationService.authenticate("ana@mail.com", "wrong"))
				.isInstanceOf(InvalidCredentialsException.class);
		verify(tokenIssuer, never()).issue(any());
	}

	@Test
	void unknownEmailIsRejectedButStillChecksAPassword() {
		when(users.findByEmail("ghost@mail.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authenticationService.authenticate("ghost@mail.com", "whatever"))
				.isInstanceOf(InvalidCredentialsException.class);
		// The dummy hash is checked so the response time doesn't reveal that the e-mail is unknown.
		verify(passwordEncoder).matches("whatever", "dummy-hash");
	}
}
