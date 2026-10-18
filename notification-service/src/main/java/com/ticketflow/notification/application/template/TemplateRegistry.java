package com.ticketflow.notification.application.template;

import com.ticketflow.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Finds the template of each type. Fails at startup if a type has none, not at the first e-mail. */
@Component
public class TemplateRegistry {

	private final Map<NotificationType, NotificationTemplate> templates = new EnumMap<>(NotificationType.class);

	public TemplateRegistry(List<NotificationTemplate> all) {
		for (NotificationTemplate template : all) {
			if (templates.put(template.type(), template) != null) {
				throw new IllegalStateException("Two templates for " + template.type());
			}
		}
		List<NotificationType> missing = Arrays.stream(NotificationType.values())
				.filter(type -> !templates.containsKey(type))
				.toList();
		if (!missing.isEmpty()) {
			throw new IllegalStateException("No template for " + missing);
		}
	}

	public NotificationTemplate get(NotificationType type) {
		return templates.get(type);
	}
}
