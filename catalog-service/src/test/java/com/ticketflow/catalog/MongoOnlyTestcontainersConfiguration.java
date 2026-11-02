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
		// MongoDB 8.0 exits on Linux 6.19+ unless glibc owns rseq (SERVER-121912).
		return new MongoDBContainer(TestcontainersConfiguration.MONGO_IMAGE).withEnv("GLIBC_TUNABLES", "glibc.pthread.rseq=1");
	}
}
