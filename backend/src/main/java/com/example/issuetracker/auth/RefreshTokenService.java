package com.example.issuetracker.auth;

import com.example.issuetracker.common.BusinessException;
import com.example.issuetracker.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String PREFIX = "auth:refresh:";
    private final StringRedisTemplate redisTemplate;
    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(Long userId) {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(
                PREFIX + token,
                userId.toString(),
                properties.jwt().refreshTokenTtl()
        );
        return token;
    }

    public Long consume(String token) {
        String key = PREFIX + token;
        String userId = redisTemplate.opsForValue().getAndDelete(key);
        if (userId == null) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "刷新令牌无效或已过期", HttpStatus.UNAUTHORIZED);
        }
        return Long.valueOf(userId);
    }

    public void revoke(String token) {
        redisTemplate.delete(PREFIX + token);
    }
}

