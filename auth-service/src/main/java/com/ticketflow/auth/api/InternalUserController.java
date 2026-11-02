package com.ticketflow.auth.api;

import com.ticketflow.auth.api.dto.UserSnapshotResponse;
import com.ticketflow.auth.application.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Service-to-service endpoints. The gateway does not route {@code /internal/**}. */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

	private final UserService userService;

	public InternalUserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/{id}")
	public UserSnapshotResponse snapshot(@PathVariable UUID id) {
		return UserSnapshotResponse.from(userService.getById(id));
	}
}
