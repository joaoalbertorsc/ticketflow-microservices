package com.ticketflow.booking.domain;

public enum SagaStepName {
	FETCH_USER,
	FETCH_EVENT,
	RESERVE_SEATS,
	CHARGE,
	CONFIRM_SEATS,
	NOTIFY,
	REFUND,
	RELEASE_SEATS
}
