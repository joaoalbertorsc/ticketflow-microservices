package com.ticketflow.booking.infrastructure.client;

import com.ticketflow.booking.application.port.Ports.Catalog;
import com.ticketflow.booking.application.port.Ports.SaleLookup;
import com.ticketflow.booking.domain.Snapshots.SaleSnapshot;
import com.ticketflow.booking.infrastructure.security.ServiceTokenIssuer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** catalog-service: what is being bought, and at what price (kept as a snapshot). */
@Component
public class CatalogClient extends DownstreamClient implements Catalog {

	/** Local copy of catalog-service's response (ADR 0005). */
	record SaleSnapshotResponse(String eventId, String eventName, Instant startsAt, String venueName, String sectorId,
			String sectorName, BigDecimal unitPrice, String currency, boolean onSale) {
	}

	public CatalogClient(RestClient.Builder builder, DownstreamProperties properties, ServiceTokenIssuer tokens) {
		super(builder, properties.catalogUrl(), properties, tokens, "catalog-service");
	}

	@Override
	@CircuitBreaker(name = "catalogService", fallbackMethod = "findSaleFallback")
	public SaleLookup findSale(UUID onBehalfOf, String eventId, String sectorId) {
		return http.get().uri("/internal/events/{eventId}/sectors/{sectorId}", eventId, sectorId)
				.headers(onBehalfOf(onBehalfOf))
				.exchange((request, response) -> {
					if (response.getStatusCode().is2xxSuccessful()) {
						SaleSnapshotResponse sale = response.bodyTo(SaleSnapshotResponse.class);
						return SaleLookup.found(new SaleSnapshot(sale.eventName(), sale.startsAt(), sale.venueName(),
								sale.sectorName(), sale.unitPrice(), sale.currency(), sale.onSale()));
					}
					if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
						return SaleLookup.notFound("Event or sector not found");
					}
					throw unexpected(response.getStatusCode());
				});
	}

	/** Fallback: never sell at a price we couldn't check; nothing was done yet. */
	private SaleLookup findSaleFallback(UUID onBehalfOf, String eventId, String sectorId, Throwable cause) {
		return SaleLookup.unavailable(unavailable("event lookup", cause));
	}
}
