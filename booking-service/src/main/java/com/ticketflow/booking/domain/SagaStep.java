package com.ticketflow.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** One line of a booking's saga log. Created only through {@link Booking}. */
@Entity
@Table(name = "booking_saga_steps")
public class SagaStep {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id")
	private Booking booking;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SagaStepName step;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private StepOutcome outcome;

	@Column(length = 500)
	private String detail;

	@Column(nullable = false)
	private Instant at;

	protected SagaStep() {
		// required by JPA
	}

	SagaStep(Booking booking, SagaStepName step, StepOutcome outcome, String detail, Instant at) {
		this.booking = booking;
		this.step = step;
		this.outcome = outcome;
		this.detail = detail == null || detail.length() <= 500 ? detail : detail.substring(0, 497) + "...";
		this.at = at;
	}

	public SagaStepName getStep() {
		return step;
	}

	public StepOutcome getOutcome() {
		return outcome;
	}

	public String getDetail() {
		return detail;
	}

	public Instant getAt() {
		return at;
	}
}
