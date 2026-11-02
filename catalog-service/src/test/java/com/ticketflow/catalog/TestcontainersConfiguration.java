package com.ticketflow.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/** Same images as docker-compose, so tests run against the versions we deploy. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:8.0");

	@Bean
	@ServiceConnection
	MongoDBContainer mongoDbContainer() {
		// MongoDB 8.0 exits on Linux 6.19+ unless glibc owns rseq (SERVER-121912).
		return new MongoDBContainer(MONGO_IMAGE).withEnv("GLIBC_TUNABLES", "glibc.pthread.rseq=1");
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);
	}
}
