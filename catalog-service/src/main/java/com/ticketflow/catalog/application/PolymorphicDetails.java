package com.ticketflow.catalog.application;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ticketflow.catalog.domain.ConcertDetails;
import com.ticketflow.catalog.domain.SportsDetails;
import com.ticketflow.catalog.domain.TheaterDetails;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JSON shape of {@code EventDetails}: a {@code "type"} field inside the details object picks the
 * record, e.g. {@code {"type": "CONCERT", "lineup": [...], "genre": "Rock"}}. Kept out of the
 * domain so the domain doesn't depend on Jackson; used by requests, responses and the Redis cache.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = ConcertDetails.class, name = "CONCERT"),
		@JsonSubTypes.Type(value = SportsDetails.class, name = "SPORTS"),
		@JsonSubTypes.Type(value = TheaterDetails.class, name = "THEATER")
})
public @interface PolymorphicDetails {
}
