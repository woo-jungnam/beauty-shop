package com.core.beautyshop.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final Set<String> CACHE_NAMES = Set.of(
            "products_page",
            "product_detail",
            "categories",
            "brands",
            "spa_services",
            "token_versions",
            "processed_events"
    );

    @Bean
    @ConditionalOnProperty(name = "app.cache.provider", havingValue = "redis", matchIfMissing = true)
    public CacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            @Value("${jwt.expiration-ms:900000}") long accessTokenExpirationMs
    ) {
        GenericJackson2JsonRedisSerializer jsonSerializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(objectMapper.copy())
                .defaultTyping(true)
                .build();

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .computePrefixWith(cacheName -> "beautyshop:cache:v2:" + cacheName + "::");

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .initialCacheNames(CACHE_NAMES)
                .withInitialCacheConfigurations(Map.of(
                        "token_versions",
                        defaults.entryTtl(Duration.ofMillis(accessTokenExpirationMs))))
                .transactionAware()
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.cache.provider", havingValue = "local")
    public CacheManager localCacheManager() {
        var manager = new org.springframework.cache.caffeine.CaffeineCacheManager(CACHE_NAMES.toArray(String[]::new));
        manager.setCaffeine(com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .maximumSize(5000).expireAfterWrite(Duration.ofMinutes(5)));
        manager.setAllowNullValues(false);
        return new org.springframework.cache.transaction.TransactionAwareCacheManagerProxy(manager);
    }
}
