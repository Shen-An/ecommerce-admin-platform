package com.youlai.mall.ai.config;

import com.youlai.mall.ai.rag.LightRagClient;
import com.youlai.mall.ai.rag.LightRagProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(LightRagProperties.class)
public class AiConfiguration {

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    @Bean
    public LightRagClient lightRagClient(WebClient.Builder builder, LightRagProperties properties) {
        return new LightRagClient(builder, properties);
    }
}
