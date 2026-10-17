package com.ticketflow.auth.api;

import com.ticketflow.auth.TestUsers;
import com.ticketflow.auth.application.AuthenticationService;
import com.ticketflow.auth.application.EmailAlreadyRegisteredException;
import com.ticketflow.auth.application.InvalidCredentialsException;
import com.ticketflow.auth.application.IssuedToken;
import com.ticketflow.auth.application.UserService;
import com.ticketflow.auth.domain.User;
import com.ticketflow.auth.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	UserService userService;

	@MockitoBean
	AuthenticationService authenticationService;

	@Test
	void registerReturns201WithoutExposingThePassword() throws Exception {
		User user = TestUsers.persistedUser();
		when(userService.register("Ana Souza", "ana@mail.com", "Str0ngPass")).thenReturn(user);

		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "Ana Souza", "email": "ana@mail.com", "password": "Str0ngPass"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(user.getId().toString()))
				.andExpect(jsonPath("$.email").value("ana@mail.com"))
				.andExpect(jsonPath("$.roles[0]").value("USER"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registerWithInvalidFieldsReturns400WithFieldErrors() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "", "email": "not-an-email", "password": "short"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors.name").exists())
				.andExpect(jsonPath("$.errors.email").exists())
				.andExpect(jsonPath("$.errors.password").exists());
		verifyNoInteractions(userService);
	}

	@Test
	void registerWithPasswordWithoutDigitReturns400() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "Ana", "email": "ana@mail.com", "password": "onlyletters"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.password").value("must contain at least one letter and one digit"));
	}

	@Test
	void registerWithTakenEmailReturns409() throws Exception {
		when(userService.register(any(), any(), any())).thenThrow(new EmailAlreadyRegisteredException("ana@mail.com"));

		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "Ana", "email": "ana@mail.com", "password": "Str0ngPass"}
						"""))
				.andExpect(status().isConflict());
	}

	@Test
	void loginReturnsBearerToken() throws Exception {
		when(authenticationService.authenticate("ana@mail.com", "Str0ngPass"))
				.thenReturn(new IssuedToken("token-value", Duration.ofHours(1)));

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
						{"email": "ana@mail.com", "password": "Str0ngPass"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("token-value"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600));
	}

	@Test
	void loginWithBadCredentialsReturns401() throws Exception {
		when(authenticationService.authenticate(any(), any())).thenThrow(new InvalidCredentialsException());

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
						{"email": "ana@mail.com", "password": "wrong"}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Invalid e-mail or password"));
	}

	@Test
	void meWithoutTokenReturns401() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void meReturnsTheUserFromTheTokenSubject() throws Exception {
		User user = TestUsers.persistedUser();
		when(userService.getById(user.getId())).thenReturn(user);

		mvc.perform(get("/api/auth/me").with(jwt().jwt(token -> token.subject(user.getId().toString()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ana Souza"));
	}
}
