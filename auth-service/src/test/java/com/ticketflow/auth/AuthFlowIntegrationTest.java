package com.ticketflow.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application against a real PostgreSQL (Testcontainers): Flyway migrations, JPA mapping,
 * BCrypt, real JWT signing and validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Test
	void registerLoginAndReadOwnProfile() throws Exception {
		String email = uniqueEmail();
		register("Ana Souza", email, "Str0ngPass");

		String token = login(email.toUpperCase(), "Str0ngPass");

		mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.roles[0]").value("USER"));
	}

	@Test
	void duplicateEmailIsRejected() throws Exception {
		String email = uniqueEmail();
		register("Ana", email, "Str0ngPass");

		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content(registerBody("Other", email.toUpperCase(), "Str0ngPass")))
				.andExpect(status().isConflict());
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		String email = uniqueEmail();
		register("Ana", email, "Str0ngPass");

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, "WrongPass1")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tamperedTokenIsRejected() throws Exception {
		String email = uniqueEmail();
		register("Ana", email, "Str0ngPass");
		String token = login(email, "Str0ngPass");
		String tampered = token.substring(0, token.length() - 4) + "AAAA";

		mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void bootstrappedAdminCanPromoteAUser() throws Exception {
		String userId = register("Bruno", uniqueEmail(), "Str0ngPass");
		String adminToken = login("admin@test.local", "Admin12345");

		mvc.perform(put("/api/users/{id}/roles", userId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roles": ["USER", "ADMIN"]}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles.length()").value(2));
	}

	@Test
	void internalSnapshotReturnsUserData() throws Exception {
		String email = uniqueEmail();
		String userId = register("Carla", email, "Str0ngPass");
		String token = login(email, "Str0ngPass");

		mvc.perform(get("/internal/users/{id}", userId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Carla"))
				.andExpect(jsonPath("$.email").value(email));
	}

	private String register(String name, String email, String password) throws Exception {
		String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content(registerBody(name, email, password)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String login(String email, String password) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private static String registerBody(String name, String email, String password) {
		return """
				{"name": "%s", "email": "%s", "password": "%s"}
				""".formatted(name, email, password);
	}

	private static String loginBody(String email, String password) {
		return """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@mail.com";
	}
}
