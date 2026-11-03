package com.ticketflow.catalog.infrastructure.persistence;

import com.ticketflow.catalog.domain.Event;
import com.ticketflow.catalog.domain.EventRepository;
import com.ticketflow.catalog.domain.EventSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Uses {@link MongoTemplate} because the search has optional filters, which derived queries can't express. */
@Repository
class MongoEventRepository implements EventRepository {

	private final MongoTemplate mongo;

	MongoEventRepository(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public Event save(Event event) {
		return mongo.save(event);
	}

	@Override
	public Optional<Event> findById(String id) {
		return Optional.ofNullable(mongo.findById(id, Event.class));
	}

	@Override
	public Page<Event> search(EventSearchCriteria criteria, Pageable pageable) {
		Query query = new Query(toCriteria(criteria));
		long total = mongo.count(query, Event.class);
		query.with(pageable).with(Sort.by("startsAt").ascending());
		List<Event> content = mongo.find(query, Event.class);
		return new PageImpl<>(content, pageable, total);
	}

	private static Criteria toCriteria(EventSearchCriteria criteria) {
		List<Criteria> filters = new ArrayList<>();
		if (criteria.status() != null) {
			filters.add(Criteria.where("status").is(criteria.status()));
		}
		if (criteria.type() != null) {
			filters.add(Criteria.where("type").is(criteria.type()));
		}
		if (criteria.city() != null) {
			// exact city, ignoring case; the input is quoted so it can't inject regex syntax
			filters.add(Criteria.where("venue.city").regex("^" + Pattern.quote(criteria.city()) + "$", "i"));
		}
		if (criteria.from() != null || criteria.to() != null) {
			Criteria startsAt = Criteria.where("startsAt");
			if (criteria.from() != null) {
				startsAt = startsAt.gte(criteria.from());
			}
			if (criteria.to() != null) {
				startsAt = startsAt.lte(criteria.to());
			}
			filters.add(startsAt);
		}
		return filters.isEmpty() ? new Criteria() : new Criteria().andOperator(filters);
	}
}
