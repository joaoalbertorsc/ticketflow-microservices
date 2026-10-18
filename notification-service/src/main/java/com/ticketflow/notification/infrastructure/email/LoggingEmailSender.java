package com.ticketflow.notification.infrastructure.email;

import com.ticketflow.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Simulated e-mail: writes the message to the log instead of sending it. Addresses at
 * {@code @bounce.test} are rejected, to show the FAILED path deterministically.
 */
@Component
public class LoggingEmailSender implements EmailSender {

	public static final String BOUNCE_DOMAIN = "@bounce.test";

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public DeliveryResult send(String to, String subject, String body) {
		if (to.toLowerCase(Locale.ROOT).endsWith(BOUNCE_DOMAIN)) {
			log.warn("E-mail to {} bounced: mailbox does not exist", to);
			return DeliveryResult.rejected("Mailbox does not exist");
		}
		log.info("""
				E-mail sent
				  To:      {}
				  Subject: {}
				{}""", to, subject, body.indent(2).stripTrailing());
		return DeliveryResult.ok();
	}
}
