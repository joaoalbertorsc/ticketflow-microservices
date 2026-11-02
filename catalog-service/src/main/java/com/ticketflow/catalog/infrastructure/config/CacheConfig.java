package com.ticketflow.catalog.infrastructure.config;

import com.ticketflow.catalog.application.CacheNames;
import com.ticketflow.catalog.application.EventView;
import com.ticketflow.catalog.application.SaleSnapshot;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/**
 * Redis cache for catalog reads. Each cache stores one known type as JSON (readable with
 * redis-cli, and no Java serialization).
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

	@Bean
	RedisCacheManagerBuilderCustomizer catalogCaches(JsonMapper jsonMapper, @Value("${ticketflow.cache.ttl:10m}") Duration ttl) {
		return builder -> builder
				.withCacheConfiguration(CacheNames.EVENTS,
						cacheOf(ttl, new JacksonJsonRedisSerializer<>(jsonMapper, EventView.class)))
				.withCacheConfiguration(CacheNames.EVENT_SECTORS,
						cacheOf(ttl, new JacksonJsonRedisSerializer<>(jsonMapper, SaleSnapshot.class)));
	}

	private static RedisCacheConfiguration cacheOf(Duration ttl, JacksonJsonRedisSerializer<?> serializer) {
		return RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(ttl)
				.disableCachingNullValues()
				.serializeValuesWith(SerializationPair.fromSerializer(serializer));
	}

	/**
	 * Redis down must not mean catalog down: cache errors are logged and the call goes on to
	 * MongoDB as if it were a cache miss.
	 */
	@Override
	public CacheErrorHandler errorHandler() {
		return new LoggingCacheErrorHandler(LogFactory.getLog(CacheConfig.class), false);
	}
}
