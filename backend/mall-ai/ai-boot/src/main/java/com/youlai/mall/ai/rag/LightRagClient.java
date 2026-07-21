package com.youlai.mall.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LightRAG Server HTTP 客户端（Phase2 完善文档上传/删除）。
 */
@Slf4j
@RequiredArgsConstructor
public class LightRagClient {

    private final WebClient.Builder webClientBuilder;
    private final LightRagProperties properties;

    public boolean health() {
        try {
            webClientBuilder.build()
                    .get()
                    .uri(properties.getBaseUrl() + "/health")
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(Math.min(properties.getTimeoutMs(), 5000)));
            return true;
        } catch (Exception ex) {
            log.debug("LightRAG health check failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 混合检索问答（mode=mix）。服务不可用时返回降级结果，不抛异常打断主流程。
     */
    public Map<String, Object> query(String question, String mode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", question);
        body.put("mode", mode == null ? "mix" : mode);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = webClientBuilder.build()
                    .post()
                    .uri(properties.getBaseUrl() + "/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(properties.getTimeoutMs()));
            return resp == null ? Collections.emptyMap() : resp;
        } catch (WebClientResponseException ex) {
            log.warn("LightRAG query HTTP error: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return fallback(question, "HTTP " + ex.getStatusCode().value());
        } catch (Exception ex) {
            log.warn("LightRAG query failed: {}", ex.getMessage());
            return fallback(question, ex.getMessage());
        }
    }

    private Map<String, Object> fallback(String question, String reason) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("response", "LightRAG 暂不可用（" + reason + "）。问题已记录：" + question);
        map.put("degraded", true);
        return map;
    }
}
