package com.ticketflow.auth.application;

import com.ticketflow.auth.TestUsers;
import com.ticketflow.auth.domain.Role;
import com.ticketflow.auth.domain.User;
import com.ticketflow.auth.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	UserRepository users;

	@Mock
	PasswordEncoder passwordEncoder;

	@InjectMocks
	UserService userService;

	@Test
	void registerStoresNormalizedEmailHashedPasswordAndUserRole() {
		when(users.existsByEmail("ana@mail.com")).thenReturn(false);
		when(passwordEncoder.encode("Str0ngPass")).thenReturn("hashed");
		when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User user = userService.register("  Ana Souza ", "  Ana@Mail.COM ", "Str0ngPass");

		assertThat(user.getName()).isEqualTo("Ana Souza");
		assertThat(user.getEmail()).isEqualTo("ana@mail.com");
		assertThat(user.getPasswordHash()).isEqualTo("hashed");
		assertThat(user.getRoles()).containsExactly(Role.USER);
	}

	@Test
	void registerRejectsEmailAlreadyInUseIgnoringCase() {
		when(users.existsByEmail("ana@mail.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.register("Ana", "ANA@mail.com", "Str0ngPass"))
				.isInstanceOf(EmailAlreadyRegisteredException.class);
		verify(users, never()).save(any());
	}

	@Test
	void getByIdFailsForUnknownUser() {
		UUID id = UUID.randomUUID();
		when(users.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.getById(id)).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void replaceRolesOverwritesExistingRoles() {
		User user = TestUsers.persistedUser();
		when(users.findById(user.getId())).thenReturn(Optional.of(user));
		when(users.save(user)).thenReturn(user);

		User updated = userService.replaceRoles(user.getId(), Set.of(Role.ADMIN));

		assertThat(updated.getRoles()).containsExactly(Role.ADMIN);
	}
}
