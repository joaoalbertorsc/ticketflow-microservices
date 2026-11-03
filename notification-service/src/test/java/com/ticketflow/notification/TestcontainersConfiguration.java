package com.ticketflow.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/** Same image as docker-compose, so tests run against the version we deploy. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MongoDBContainer mongoDbContainer() {
		// MongoDB 8.0 exits on Linux 6.19+ unless glibc owns rseq (SERVER-121912).
		return new MongoDBContainer(DockerImageName.parse("mongo:8.0")).withEnv("GLIBC_TUNABLES", "glibc.pthread.rseq=1");
	}
}
