package com.youlai.common.redis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 本机 Redis 无密码时，YAML {@code password:} / 空串会触发 Redisson AUTH 失败。
 * 在环境准备阶段把空密码覆盖为 null。
 */
public class EmptyRedisPasswordEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String password = firstNonNull(
                environment.getProperty("spring.data.redis.password"),
                environment.getProperty("spring.redis.password")
        );
        // 属性不存在：无需处理；有非空密码：保持
        if (password == null || StringUtils.hasText(password)) {
            return;
        }
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("spring.data.redis.password", null);
        overrides.put("spring.redis.password", null);
        environment.getPropertySources().addFirst(new MapPropertySource("emptyRedisPasswordOverride", overrides));
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
