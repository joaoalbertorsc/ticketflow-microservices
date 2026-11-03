package com.ticketflow.booking.api;

import com.ticketflow.booking.application.BookingExceptions.BookingNotFoundException;
import com.ticketflow.booking.domain.BookingStateException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Turns exceptions into RFC 9457 problem responses. A failed booking is not here: it is a booking
 * resource with status FAILED, returned by the controller with 409 or 503.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BookingNotFoundException.class)
	ProblemDetail notFound(BookingNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler(BookingStateException.class)
	ProblemDetail state(BookingStateException ex) {
		return problem(HttpStatus.CONFLICT, "Operation not allowed in current status", ex.getMessage());
	}

	/** The recovery job (or another request) changed the booking at the same moment. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail concurrentModification(OptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "Concurrent modification", "The booking changed meanwhile; try again");
	}

	@ExceptionHandler(CurrentUser.InvalidSubjectException.class)
	ProblemDetail invalidSubject(CurrentUser.InvalidSubjectException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "Invalid token", ex.getMessage());
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

	/**
	 * When a controller method has constraints on plain parameters too (e.g. the Idempotency-Key
	 * header), Spring validates the whole method and reports body errors here instead. Same shape.
	 */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new TreeMap<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			if (result instanceof ParameterErrors bodyErrors) {
				bodyErrors.getFieldErrors().forEach(error ->
						errors.merge(error.getField(), error.getDefaultMessage(), (a, b) -> a + "; " + b));
			} else {
				String name = result.getMethodParameter().getParameterName();
				result.getResolvableErrors().forEach(error ->
						errors.merge(name, error.getDefaultMessage(), (a, b) -> a + "; " + b));
			}
		}
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
