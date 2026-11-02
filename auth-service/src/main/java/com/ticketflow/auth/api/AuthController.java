package com.ticketflow.auth.api;

import com.ticketflow.auth.api.dto.LoginRequest;
import com.ticketflow.auth.api.dto.RegisterRequest;
import com.ticketflow.auth.api.dto.TokenResponse;
import com.ticketflow.auth.api.dto.UserResponse;
import com.ticketflow.auth.application.AuthenticationService;
import com.ticketflow.auth.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserService userService;
	private final AuthenticationService authenticationService;

	public AuthController(UserService userService, AuthenticationService authenticationService) {
		this.userService = userService;
		this.authenticationService = authenticationService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return UserResponse.from(userService.register(request.name(), request.email(), request.password()));
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return TokenResponse.from(authenticationService.authenticate(request.email(), request.password()));
	}

	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return UserResponse.from(userService.getById(UUID.fromString(jwt.getSubject())));
	}
}
