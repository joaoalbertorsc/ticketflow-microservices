package com.ticketflow.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

/** MongoDB only, for tests that run without Redis. */
@TestConfiguration(proxyBeanMethods = false)
public class MongoOnlyTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MongoDBContainer mongoDbContainer() {
		return new MongoDBContainer(TestcontainersConfiguration.MONGO_IMAGE);
	}
}
