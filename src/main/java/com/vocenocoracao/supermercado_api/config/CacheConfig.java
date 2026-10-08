package com.vocenocoracao.supermercado_api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocenocoracao.supermercado_api.product.dto.ProductPageDTO;
import com.vocenocoracao.supermercado_api.product.dto.ProductResponseDTO;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String PRODUCT_LIST = "productList";
    public static final String PRODUCT_DETAIL = "productDetail";

    private static final String KEY_PREFIX = "supermercado:";

    @Bean
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            @Value("${cache.products.ttl:60s}") Duration ttl
    ) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith(KEY_PREFIX)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));

        RedisCacheConfiguration productList = base.serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, ProductPageDTO.class)));
        RedisCacheConfiguration productDetail = base.serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, ProductResponseDTO.class)));

        return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(
                        connectionFactory, BatchStrategies.scan(1000)))
                .cacheDefaults(base)
                .withInitialCacheConfigurations(Map.of(
                        PRODUCT_LIST, productList,
                        PRODUCT_DETAIL, productDetail))
                .transactionAware()
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(false);
    }
}
