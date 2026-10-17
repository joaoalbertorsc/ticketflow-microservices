package com.ticketflow.auth.api;

import com.ticketflow.auth.api.dto.UpdateRolesRequest;
import com.ticketflow.auth.api.dto.UserResponse;
import com.ticketflow.auth.application.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PutMapping("/{id}/roles")
	@PreAuthorize("hasRole('ADMIN')")
	public UserResponse replaceRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest request) {
		return UserResponse.from(userService.replaceRoles(id, request.roles()));
	}
}
