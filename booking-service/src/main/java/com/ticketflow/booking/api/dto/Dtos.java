package com.ticketflow.booking.api.dto;

import com.ticketflow.booking.application.BookingSaga.BookCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public final class Dtos {

	private Dtos() {
	}

	public record BookRequest(
			@NotBlank @Size(max = 24) String eventId,
			@NotBlank @Size(max = 40) String sectorId,
			@NotNull @Min(1) @Max(10) Integer quantity,
			@NotBlank @Size(max = 64) String cardToken) {

		public BookCommand toCommand(UUID userId, String idempotencyKey) {
			return new BookCommand(userId, idempotencyKey, eventId, sectorId, quantity, cardToken);
		}
	}

	/** Stable JSON shape for paged results (Spring's Page implementation is not meant to be serialized). */
	public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

		public static <T> PageResponse<T> from(Page<T> page) {
			return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
					page.getTotalPages());
		}
	}
}
