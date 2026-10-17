package com.ticketflow.inventory.application;

import com.ticketflow.inventory.application.InventoryExceptions.StockNotFoundException;
import com.ticketflow.inventory.domain.SectorStock;
import com.ticketflow.inventory.domain.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Seat capacity per sector, set by an admin. The catalog isn't consulted (leaf services never call
 * each other); the orchestrator checks the event and sector in the catalog before reserving.
 */
@Service
public class StockService {

	private final StockRepository stocks;
	private final Clock clock;

	public StockService(StockRepository stocks, Clock clock) {
		this.stocks = stocks;
		this.clock = clock;
	}

	/** @param created {@code false} when an existing stock was resized */
	public record StockResult(SectorStock stock, boolean created) {
	}

	@Transactional
	public StockResult setCapacity(String eventId, String sectorId, int totalSeats) {
		return stocks.findByEventIdAndSectorId(eventId, sectorId)
				.map(existing -> {
					existing.resize(totalSeats, clock.instant());
					return new StockResult(stocks.save(existing), false);
				})
				.orElseGet(() -> new StockResult(
						stocks.save(SectorStock.create(eventId, sectorId, totalSeats, clock.instant())), true));
	}

	@Transactional(readOnly = true)
	public List<SectorStock> listForEvent(String eventId) {
		return stocks.findByEventIdOrderBySectorId(eventId);
	}

	@Transactional(readOnly = true)
	public SectorStock get(String eventId, String sectorId) {
		return stocks.findByEventIdAndSectorId(eventId, sectorId)
				.orElseThrow(() -> new StockNotFoundException(eventId, sectorId));
	}
}
