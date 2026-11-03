package com.ticketflow.payment.infrastructure.persistence;

import com.ticketflow.payment.domain.Payment;
import com.ticketflow.payment.domain.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Plain JPA: three small queries don't need a Spring Data proxy, and the lock needs native SQL anyway. */
@Repository
class JpaPaymentRepository implements PaymentRepository {

	private final EntityManager entityManager;

	JpaPaymentRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Payment save(Payment payment) {
		if (payment.getVersion() == null) {
			entityManager.persist(payment);
			return payment;
		}
		return entityManager.merge(payment);
	}

	/**
	 * Reads the result eagerly ({@code getResultList}, not {@code getResultStream}): outside a
	 * transaction the shared EntityManager closes the connection right after the query, so a lazy
	 * stream would read from a closed ResultSet.
	 */
	@Override
	public Optional<Payment> findByBookingId(UUID bookingId) {
		return entityManager.createQuery("select p from Payment p where p.bookingId = :bookingId", Payment.class)
				.setParameter("bookingId", bookingId)
				.getResultList()
				.stream()
				.findFirst();
	}

	/**
	 * PostgreSQL transaction-level advisory lock on a number derived from the booking id. Held
	 * until commit/rollback; a second transaction for the same booking waits here.
	 */
	@Override
	public void lockBooking(UUID bookingId) {
		long key = bookingId.getMostSignificantBits() ^ bookingId.getLeastSignificantBits();
		entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(?1)")
				.setParameter(1, key)
				.getSingleResult();
	}
}
