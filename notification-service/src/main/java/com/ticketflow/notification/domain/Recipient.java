package com.ticketflow.notification.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Recipient(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Email @Size(max = 255) String email) {
}
