package com.example.issuetracker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@Slf4j
public class RedisConfig implements CachingConfigurer {

    @Bean
    RedisCacheManagerBuilderCustomizer cacheCustomizer() {
        // The no-arg serializer enables type metadata. Passing the application's
        // ObjectMapper here would deserialize cached DTOs as LinkedHashMap values.
        var serializer = new GenericJackson2JsonRedisSerializer();
        var pair = RedisSerializationContext.SerializationPair.fromSerializer(serializer);
        return builder -> builder
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10))
                        .serializeValuesWith(pair)
                        .disableCachingNullValues())
                .withCacheConfiguration("ticket-detail",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(5))
                                .serializeValuesWith(pair));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn(
                        "Ignoring unreadable cache entry cache={} key={}: {}",
                        cache.getName(),
                        key,
                        exception.getMessage()
                );
                try {
                    cache.evict(key);
                } catch (RuntimeException evictionException) {
                    log.warn(
                            "Failed to evict unreadable cache entry cache={} key={}",
                            cache.getName(),
                            key,
                            evictionException
                    );
                }
            }
        };
    }
}

