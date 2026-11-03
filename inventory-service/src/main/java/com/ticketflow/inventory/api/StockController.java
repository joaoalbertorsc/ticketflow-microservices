package com.ticketflow.inventory.api;

import com.ticketflow.inventory.api.dto.Dtos.CapacityRequest;
import com.ticketflow.inventory.api.dto.Dtos.StockResponse;
import com.ticketflow.inventory.application.StockService;
import com.ticketflow.inventory.application.StockService.StockResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/events/{eventId}/sectors")
public class StockController {

	private final StockService stocks;

	public StockController(StockService stocks) {
		this.stocks = stocks;
	}

	/** Creates the stock ({@code 201}) or resizes it ({@code 200}). */
	@PutMapping("/{sectorId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<StockResponse> setCapacity(@PathVariable String eventId, @PathVariable String sectorId,
			@Valid @RequestBody CapacityRequest request) {
		StockResult result = stocks.setCapacity(eventId, sectorId, request.totalSeats());
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(StockResponse.from(result.stock()));
	}

	@GetMapping
	public List<StockResponse> list(@PathVariable String eventId) {
		return stocks.listForEvent(eventId).stream().map(StockResponse::from).toList();
	}

	@GetMapping("/{sectorId}")
	public StockResponse get(@PathVariable String eventId, @PathVariable String sectorId) {
		return StockResponse.from(stocks.get(eventId, sectorId));
	}
}
