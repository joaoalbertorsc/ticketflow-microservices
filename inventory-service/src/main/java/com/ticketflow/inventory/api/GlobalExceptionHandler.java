package com.ticketflow.inventory.api;

import com.ticketflow.inventory.application.InventoryExceptions.HoldExpiredException;
import com.ticketflow.inventory.application.InventoryExceptions.NotEnoughSeatsException;
import com.ticketflow.inventory.application.InventoryExceptions.NotFoundException;
import com.ticketflow.inventory.application.InventoryExceptions.TooManySeatsException;
import com.ticketflow.inventory.domain.InventoryConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Turns exceptions into RFC 9457 problem responses. Spring's base class already covers
 * framework errors (malformed JSON, bad UUIDs, unknown release reasons...).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler(NotEnoughSeatsException.class)
	ProblemDetail notEnoughSeats(NotEnoughSeatsException ex) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "Not enough seats", ex.getMessage());
		problem.setProperty("available", ex.getAvailable());
		return problem;
	}

	@ExceptionHandler(HoldExpiredException.class)
	ProblemDetail holdExpired(HoldExpiredException ex) {
		return problem(HttpStatus.CONFLICT, "Hold expired", ex.getMessage());
	}

	@ExceptionHandler(InventoryConflictException.class)
	ProblemDetail conflict(InventoryConflictException ex) {
		return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
	}

	@ExceptionHandler(TooManySeatsException.class)
	ProblemDetail tooManySeats(TooManySeatsException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Too many seats", ex.getMessage());
	}

	/** Seats changed (a reservation, or another admin) between reading the stock and saving it. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail concurrentModification(OptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "Concurrent modification",
				"The stock changed while the request was processed; try again");
	}

	/** Two identical first-time requests raced (same booking id, or same event/sector stock); the other one won. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail dataIntegrityViolation(DataIntegrityViolationException ex) {
		return problem(HttpStatus.CONFLICT, "Conflict", "A concurrent request already created this; retry to read it");
	}

	/** Adds the list of invalid fields to the standard 400 response. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new TreeMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> errors.merge(error.getField(), error.getDefaultMessage(), (a, b) -> a + "; " + b));
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid");
		body.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(body);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
