package com.ticketflow.notification.api;

import com.ticketflow.notification.application.template.NotificationTemplate.MissingTemplateDataException;
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
 * Turns exceptions into RFC 9457 problem responses. A bounced e-mail is not here on purpose: it is
 * a delivery result (201, status FAILED), not an error.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(MissingTemplateDataException.class)
	ProblemDetail missingTemplateData(MissingTemplateDataException ex) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Missing template data", ex.getMessage());
		problem.setProperty("missing", ex.getMissing());
		return problem;
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
