package com.ticketflow.payment.api;

import com.ticketflow.payment.application.PaymentExceptions.ChaosFailureException;
import com.ticketflow.payment.application.PaymentExceptions.ConflictingChargeException;
import com.ticketflow.payment.application.PaymentExceptions.PaymentNotFoundException;
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
 * Turns exceptions into RFC 9457 problem responses. A declined card is not here on purpose: it is
 * a successful answer (201, status DECLINED), not an error.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(PaymentNotFoundException.class)
	ProblemDetail notFound(PaymentNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler(ConflictingChargeException.class)
	ProblemDetail conflictingCharge(ConflictingChargeException ex) {
		return problem(HttpStatus.CONFLICT, "Conflicting charge", ex.getMessage());
	}

	/** A technical failure: this is what the caller's circuit breaker should count. */
	@ExceptionHandler(ChaosFailureException.class)
	ProblemDetail unavailable(ChaosFailureException ex) {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Payment gateway unavailable", ex.getMessage());
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

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
