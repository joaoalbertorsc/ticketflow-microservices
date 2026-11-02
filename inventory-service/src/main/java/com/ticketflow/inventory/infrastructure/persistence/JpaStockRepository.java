package com.ticketflow.inventory.infrastructure.persistence;

import com.ticketflow.inventory.domain.SectorStock;
import com.ticketflow.inventory.domain.StockRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface JpaStockRepository extends JpaRepository<SectorStock, UUID>, StockRepository {

	/**
	 * Check and write in one statement: PostgreSQL locks the row while updating it, so two
	 * concurrent requests for the last seat can't both succeed. Bumps the version so a concurrent
	 * resize (which goes through the entity) fails instead of overwriting this change.
	 * Clears the persistence context so a later read sees the new value.
	 */
	@Override
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE sector_stock
			   SET available_seats = available_seats - :quantity, version = version + 1, updated_at = now()
			 WHERE id = :stockId AND available_seats >= :quantity
			""", nativeQuery = true)
	int takeSeats(@Param("stockId") UUID stockId, @Param("quantity") int quantity);

	@Override
	@Modifying(flushAutomatically = true)
	@Query(value = """
			UPDATE sector_stock
			   SET available_seats = available_seats + :quantity, version = version + 1, updated_at = now()
			 WHERE id = :stockId AND available_seats + :quantity <= total_seats
			""", nativeQuery = true)
	int returnSeats(@Param("stockId") UUID stockId, @Param("quantity") int quantity);
}
