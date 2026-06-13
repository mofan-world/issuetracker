package com.example.issuetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Elasticsearch elasticsearch,
        Bootstrap bootstrap,
        Storage storage
) {
    public record Jwt(String issuer, String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Elasticsearch(boolean enabled) {
    }

    public record Bootstrap(String adminUsername, String adminPassword, String adminEmail) {
    }

    public record Storage(
            String root,
            DataSize maxFileSize,
            int maxFilesPerTicket,
            List<String> allowedExtensions
    ) {
    }
}

