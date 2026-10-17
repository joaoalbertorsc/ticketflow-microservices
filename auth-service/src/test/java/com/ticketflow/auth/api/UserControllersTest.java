package com.ticketflow.auth.api;

import com.ticketflow.auth.TestUsers;
import com.ticketflow.auth.application.UserNotFoundException;
import com.ticketflow.auth.application.UserService;
import com.ticketflow.auth.domain.Role;
import com.ticketflow.auth.domain.User;
import com.ticketflow.auth.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UserController.class, InternalUserController.class})
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class UserControllersTest {

	private static final String ROLES_BODY = """
			{"roles": ["USER", "ADMIN"]}
			""";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	UserService userService;

	@Test
	void adminCanReplaceRoles() throws Exception {
		User user = TestUsers.persisted("Ana", "ana@mail.com", "hash", Set.of(Role.USER, Role.ADMIN));
		when(userService.replaceRoles(user.getId(), Set.of(Role.USER, Role.ADMIN))).thenReturn(user);

		mvc.perform(put("/api/users/{id}/roles", user.getId())
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
						.contentType(MediaType.APPLICATION_JSON).content(ROLES_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles.length()").value(2));
	}

	@Test
	void regularUserCannotReplaceRoles() throws Exception {
		mvc.perform(put("/api/users/{id}/roles", UUID.randomUUID())
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
						.contentType(MediaType.APPLICATION_JSON).content(ROLES_BODY))
				.andExpect(status().isForbidden());
		verifyNoInteractions(userService);
	}

	@Test
	void regularUserGets403EvenWithAnInvalidBody() throws Exception {
		mvc.perform(put("/api/users/{id}/roles", UUID.randomUUID())
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void unknownRoleReturns400() throws Exception {
		mvc.perform(put("/api/users/{id}/roles", UUID.randomUUID())
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"roles": ["SUPERUSER"]}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void internalSnapshotRequiresAToken() throws Exception {
		mvc.perform(get("/internal/users/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

	@Test
	void internalSnapshotReturnsOnlyIdNameAndEmail() throws Exception {
		User user = TestUsers.persistedUser();
		when(userService.getById(user.getId())).thenReturn(user);

		mvc.perform(get("/internal/users/{id}", user.getId()).with(jwt()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId().toString()))
				.andExpect(jsonPath("$.name").value("Ana Souza"))
				.andExpect(jsonPath("$.email").value("ana@mail.com"))
				.andExpect(jsonPath("$.roles").doesNotExist());
	}

	@Test
	void internalSnapshotOfUnknownUserReturns404() throws Exception {
		UUID id = UUID.randomUUID();
		when(userService.getById(id)).thenThrow(new UserNotFoundException(id));

		mvc.perform(get("/internal/users/{id}", id).with(jwt()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("User not found"));
	}
}
