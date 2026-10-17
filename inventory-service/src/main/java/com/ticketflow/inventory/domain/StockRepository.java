package com.ticketflow.inventory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

	SectorStock save(SectorStock stock);

	Optional<SectorStock> findByEventIdAndSectorId(String eventId, String sectorId);

	List<SectorStock> findByEventIdOrderBySectorId(String eventId);

	/**
	 * Takes seats only if enough are available, in one atomic statement: the check and the write
	 * can't be separated by a concurrent request.
	 *
	 * @return 1 if the seats were taken, 0 if there weren't enough
	 */
	int takeSeats(UUID stockId, int quantity);

	/** Gives seats back (never above total capacity). */
	int returnSeats(UUID stockId, int quantity);
}
