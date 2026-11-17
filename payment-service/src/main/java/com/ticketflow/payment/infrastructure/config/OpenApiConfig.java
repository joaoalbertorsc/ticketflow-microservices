package com.ticketflow.payment.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description served at {@code /v3/api-docs}, with Swagger UI at {@code /swagger-ui.html}.
 * Declares the JWT bearer scheme so protected endpoints can be tried from the UI with a token.
 */
@Configuration
class OpenApiConfig {

	static final String BEARER = "bearer-jwt";

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info().title("payment-service").version("1.0.0").description("TicketFlow payment-service: simulated charges and refunds, chaos mode."))
				.components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}
}
