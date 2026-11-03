package com.ticketflow.booking.application;

import com.ticketflow.booking.application.port.Ports.Catalog;
import com.ticketflow.booking.application.port.Ports.Charge;
import com.ticketflow.booking.application.port.Ports.Confirmation;
import com.ticketflow.booking.application.port.Ports.Inventory;
import com.ticketflow.booking.application.port.Ports.NotificationRequest;
import com.ticketflow.booking.application.port.Ports.Notifier;
import com.ticketflow.booking.application.port.Ports.Payments;
import com.ticketflow.booking.application.port.Ports.Reservation;
import com.ticketflow.booking.application.port.Ports.SaleLookup;
import com.ticketflow.booking.application.port.Ports.Undo;
import com.ticketflow.booking.application.port.Ports.UserDirectory;
import com.ticketflow.booking.application.port.Ports.UserLookup;
import com.ticketflow.booking.domain.Snapshots.SaleSnapshot;
import com.ticketflow.booking.domain.Snapshots.UserSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * All five downstream services as one fake. Each answer is a field the test can change; every
 * call is recorded, so tests can check which compensations ran.
 */
class FakeServices implements UserDirectory, Catalog, Inventory, Payments, Notifier {

	static final Instant STARTS_AT = Instant.parse("2026-12-01T20:00:00Z");

	UserLookup user = UserLookup.found(new UserSnapshot("Ana Souza", "ana@mail.com"));
	SaleLookup sale = SaleLookup.found(new SaleSnapshot("Hamlet", STARTS_AT, "Theatro Municipal", "Plateia",
			new BigDecimal("120.00"), "BRL", true));
	Reservation reservation = Reservation.reserved(UUID.randomUUID());
	Charge charge = Charge.approved(UUID.randomUUID());
	Confirmation confirmation = Confirmation.confirmed();
	Undo release = Undo.ok();
	Undo refund = Undo.ok();
	Undo notification = Undo.ok();

	final List<String> calls = new ArrayList<>();
	final List<NotificationRequest> notifications = new ArrayList<>();
	BigDecimal chargedAmount;

	@Override
	public UserLookup findUser(UUID userId) {
		calls.add("findUser");
		return user;
	}

	@Override
	public SaleLookup findSale(UUID onBehalfOf, String eventId, String sectorId) {
		calls.add("findSale");
		return sale;
	}

	@Override
	public Reservation reserve(UUID onBehalfOf, UUID bookingId, String eventId, String sectorId, int quantity) {
		calls.add("reserve");
		return reservation;
	}

	@Override
	public Confirmation confirm(UUID onBehalfOf, UUID bookingId) {
		calls.add("confirm");
		return confirmation;
	}

	@Override
	public Undo release(UUID onBehalfOf, UUID bookingId, boolean cancellation) {
		calls.add(cancellation ? "release(cancelled)" : "release");
		return release;
	}

	@Override
	public Charge charge(UUID onBehalfOf, UUID bookingId, BigDecimal amount, String currency, String cardToken) {
		calls.add("charge");
		chargedAmount = amount;
		return charge;
	}

	@Override
	public Undo refund(UUID onBehalfOf, UUID bookingId) {
		calls.add("refund");
		return refund;
	}

	@Override
	public Undo notify(UUID onBehalfOf, NotificationRequest request) {
		calls.add("notify(" + request.type() + ")");
		notifications.add(request);
		return notification;
	}
}
