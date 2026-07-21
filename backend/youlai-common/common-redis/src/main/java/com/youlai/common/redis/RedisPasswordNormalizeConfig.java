package com.youlai.common.redis;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 将 Redis 空密码规范为 null，避免 Redisson 对无密码实例发送 AUTH。
 */
@Configuration
public class RedisPasswordNormalizeConfig {

    @Bean
    public static org.springframework.beans.factory.config.BeanPostProcessor redisPasswordNormalizer() {
        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof RedisProperties props) {
                    if (!StringUtils.hasText(props.getPassword())) {
                        props.setPassword(null);
                    }
                    if (!StringUtils.hasText(props.getUsername())) {
                        props.setUsername(null);
                    }
                }
                return bean;
            }
        };
    }
}
