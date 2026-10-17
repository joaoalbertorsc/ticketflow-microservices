package com.ticketflow.inventory.domain;

/** Why seats were given back. EXPIRED is only used by the expiry job. */
public enum ReleaseReason {
	/** The saga failed after the hold (e.g. payment declined). */
	COMPENSATION,
	/** The hold was never confirmed in time. */
	EXPIRED,
	/** A confirmed booking was cancelled. */
	CANCELLED
}
