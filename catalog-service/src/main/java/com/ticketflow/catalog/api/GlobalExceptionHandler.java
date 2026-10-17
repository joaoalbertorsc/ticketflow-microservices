package com.ticketflow.catalog.api;

import com.ticketflow.catalog.application.NotFoundExceptions.NotFoundException;
import com.ticketflow.catalog.domain.EventStateException;
import com.ticketflow.catalog.domain.InvalidEventException;
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
 * framework errors (malformed JSON, unknown details type, bad parameters...).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler(InvalidEventException.class)
	ProblemDetail invalidEvent(InvalidEventException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid event", ex.getMessage());
	}

	@ExceptionHandler(EventStateException.class)
	ProblemDetail eventState(EventStateException ex) {
		return problem(HttpStatus.CONFLICT, "Operation not allowed in current status", ex.getMessage());
	}

	/** Someone else saved the event between our read and our write. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail concurrentModification(OptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "Concurrent modification",
				"The event was changed by another request; reload it and try again");
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
