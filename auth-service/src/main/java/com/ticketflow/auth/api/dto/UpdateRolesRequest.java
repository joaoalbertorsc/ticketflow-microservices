package com.ticketflow.auth.api.dto;

import com.ticketflow.auth.domain.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<@NotNull Role> roles) {
}
