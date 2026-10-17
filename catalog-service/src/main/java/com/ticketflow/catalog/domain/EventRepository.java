package com.ticketflow.catalog.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface EventRepository {

	Event save(Event event);

	Optional<Event> findById(String id);

	Page<Event> search(EventSearchCriteria criteria, Pageable pageable);
}
