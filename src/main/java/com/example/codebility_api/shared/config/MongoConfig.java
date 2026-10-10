package com.example.codebility_api.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer connectionPoolCustomizer(
            @Value("${app.mongodb.max-pool-size:100}") int maxPoolSize,
            @Value("${app.mongodb.min-pool-size:0}") int minPoolSize,
            @Value("${app.mongodb.max-connection-idle-ms:60000}") long maxIdleMs) {
        return builder -> builder.applyToConnectionPoolSettings(pool -> pool
                .maxSize(maxPoolSize)
                .minSize(minPoolSize)
                .maxConnectionIdleTime(maxIdleMs, TimeUnit.MILLISECONDS));
    }
}
