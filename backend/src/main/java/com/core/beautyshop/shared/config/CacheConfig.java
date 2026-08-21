package com.core.beautyshop.shared.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("products_page");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                // Cache tối đa 200 entries (mỗi entry = 1 trang phân trang)
                .maximumSize(200)
                // Tự động hết hạn sau 5 phút kể từ lần ghi cuối
                .expireAfterWrite(5, TimeUnit.MINUTES)
                // Ghi đè số liệu thống kê cache cho monitoring
                .recordStats()
        );
        return cacheManager;
    }
}
