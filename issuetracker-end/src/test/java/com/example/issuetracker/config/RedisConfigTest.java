package com.example.issuetracker.config;

import com.example.issuetracker.admin.AdminDtos.RoleView;
import com.example.issuetracker.auth.AuthDtos.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisConfigTest {

    @Test
    void cachedRoleViewsRetainTheirRuntimeType() {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        List<RoleView> roles = List.of(
                new RoleView(1L, "TESTER", "测试人员", List.of("ticket:verify"))
        );

        Object restored = serializer.deserialize(serializer.serialize(roles));

        assertThat(restored).isInstanceOf(List.class);
        assertThat((List<?>) restored)
                .singleElement()
                .isInstanceOf(RoleView.class);
    }

    @Test
    void cachedUserProfileRetainsItsRuntimeType() {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        UserProfile profile = new UserProfile(
                7L,
                "developer",
                "developer@example.com",
                "Developer",
                List.of("DEVELOPER"),
                List.of("ticket:process")
        );

        Object restored = serializer.deserialize(serializer.serialize(profile));

        assertThat(restored)
                .isInstanceOf(UserProfile.class)
                .isEqualTo(profile);
    }

    @Test
    void unreadableCacheEntriesAreEvictedAndTreatedAsMisses() {
        RedisConfig config = new RedisConfig();
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("roles");

        assertThatCode(() -> config.errorHandler().handleCacheGetError(
                new IllegalStateException("legacy serialization format"),
                cache,
                "v2:all"
        )).doesNotThrowAnyException();

        verify(cache).evict("v2:all");
    }
}
