package com.example.issuetracker.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class RedisConfig {

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
}

