package com.ticketflow.catalog.infrastructure.persistence;

import com.ticketflow.catalog.domain.Venue;
import com.ticketflow.catalog.domain.VenueRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataVenueRepository extends MongoRepository<Venue, String>, VenueRepository {
}
