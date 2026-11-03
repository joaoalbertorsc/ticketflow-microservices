package com.ticketflow.notification.application.template;

import com.ticketflow.notification.domain.NotificationType;
import com.ticketflow.notification.domain.Recipient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders one notification type. Subject and body use {@code {placeholder}} names; {@code name}
 * is always available (the recipient's name), everything else must come in the request data.
 * Adding a type = one new subclass.
 */
public abstract class NotificationTemplate {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

	public record RenderedMessage(String subject, String body) {
	}

	public abstract NotificationType type();

	protected abstract String subject();

	protected abstract String body();

	/** Data fields this template needs, read from its placeholders (so they can't drift apart). */
	public Set<String> requiredFields() {
		Set<String> fields = new TreeSet<>();
		for (String text : List.of(subject(), body())) {
			Matcher matcher = PLACEHOLDER.matcher(text);
			while (matcher.find()) {
				fields.add(matcher.group(1));
			}
		}
		fields.remove("name");
		return fields;
	}

	public Set<String> missingFields(Map<String, String> data) {
		Set<String> missing = new TreeSet<>(requiredFields());
		data.forEach((key, value) -> {
			if (value != null && !value.isBlank()) {
				missing.remove(key);
			}
		});
		return missing;
	}

	public RenderedMessage render(Recipient recipient, Map<String, String> data) {
		Set<String> missing = missingFields(data);
		if (!missing.isEmpty()) {
			throw new MissingTemplateDataException(type(), missing);
		}
		return new RenderedMessage(fill(subject(), recipient, data), fill(body(), recipient, data));
	}

	private static String fill(String text, Recipient recipient, Map<String, String> data) {
		Matcher matcher = PLACEHOLDER.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String key = matcher.group(1);
			String value = "name".equals(key) ? recipient.name() : data.get(key);
			matcher.appendReplacement(result, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/** 400: the request lacks data this notification type needs. */
	public static class MissingTemplateDataException extends RuntimeException {

		private final Set<String> missing;

		public MissingTemplateDataException(NotificationType type, Set<String> missing) {
			super(type + " needs data fields: " + String.join(", ", missing));
			this.missing = missing;
		}

		public Set<String> getMissing() {
			return missing;
		}
	}
}
