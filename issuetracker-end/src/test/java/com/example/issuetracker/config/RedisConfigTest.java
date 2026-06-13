package com.example.issuetracker.config;

import com.example.issuetracker.admin.AdminDtos.RoleView;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
