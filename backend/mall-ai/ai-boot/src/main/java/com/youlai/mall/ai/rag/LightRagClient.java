package com.youlai.mall.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LightRAG Server HTTP 客户端。
 * API 参考：/health、/query、/documents/text、/documents/upload
 * @see <a href="https://github.com/HKUDS/LightRAG">HKUDS/LightRAG</a>
 */
@Slf4j
@RequiredArgsConstructor
public class LightRagClient {

    private final WebClient.Builder webClientBuilder;
    private final LightRagProperties properties;

    public String resolveBaseUrl(String override) {
        if (StringUtils.hasText(override)) {
            return trimSlash(override);
        }
        return trimSlash(properties.getBaseUrl());
    }

    public boolean health(String baseUrlOverride) {
        String base = resolveBaseUrl(baseUrlOverride);
        try {
            webClientBuilder.build()
                    .get()
                    .uri(base + "/health")
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(Math.min(properties.getTimeoutMs(), 5000)));
            return true;
        } catch (Exception ex) {
            log.debug("LightRAG health failed {}: {}", base, ex.getMessage());
            return false;
        }
    }

    public boolean health() {
        return health(null);
    }

    /**
     * 混合检索问答。服务不可用时返回 degraded 结果。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> query(String question, String mode, String baseUrlOverride) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", question);
        body.put("mode", StringUtils.hasText(mode) ? mode : "mix");
        body.put("include_references", true);
        String base = resolveBaseUrl(baseUrlOverride);
        try {
            Map<String, Object> resp = webClientBuilder.build()
                    .post()
                    .uri(base + "/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(properties.getTimeoutMs()));
            if (resp == null) {
                return fallback(question, "empty response");
            }
            resp.putIfAbsent("degraded", false);
            return resp;
        } catch (WebClientResponseException ex) {
            log.warn("LightRAG query HTTP error: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return fallback(question, "HTTP " + ex.getStatusCode().value());
        } catch (Exception ex) {
            log.warn("LightRAG query failed: {}", ex.getMessage());
            return fallback(question, ex.getMessage());
        }
    }

    public Map<String, Object> query(String question, String mode) {
        return query(question, mode, null);
    }

    /**
     * 插入纯文本文档。
     * @return track_id 或 message；失败抛异常由调用方处理
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> insertText(String text, String fileSource, String baseUrlOverride) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        if (StringUtils.hasText(fileSource)) {
            body.put("file_source", fileSource);
        }
        String base = resolveBaseUrl(baseUrlOverride);
        return webClientBuilder.build()
                .post()
                .uri(base + "/documents/text")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofMillis(properties.getTimeoutMs()));
    }

    /**
     * 上传文件到 LightRAG。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> uploadFile(byte[] bytes, String filename, String baseUrlOverride) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).filename(filename);

        String base = resolveBaseUrl(baseUrlOverride);
        return webClientBuilder.build()
                .post()
                .uri(base + "/documents/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofMillis(properties.getTimeoutMs()));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> listDocuments(String baseUrlOverride) {
        String base = resolveBaseUrl(baseUrlOverride);
        try {
            // 新版分页接口
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("page", 1);
            body.put("page_size", 50);
            return webClientBuilder.build()
                    .post()
                    .uri(base + "/documents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(Math.min(properties.getTimeoutMs(), 15000)));
        } catch (Exception ex) {
            log.debug("LightRAG list documents failed: {}", ex.getMessage());
            try {
                return webClientBuilder.build()
                        .get()
                        .uri(base + "/documents")
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofMillis(8000));
            } catch (Exception ex2) {
                return Collections.emptyMap();
            }
        }
    }

    public static String extractAnswer(Map<String, Object> resp) {
        if (resp == null) {
            return "";
        }
        Object response = resp.get("response");
        if (response != null && StringUtils.hasText(response.toString())) {
            return response.toString();
        }
        Object answer = resp.get("answer");
        if (answer != null) {
            return answer.toString();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> extractReferences(Map<String, Object> resp) {
        if (resp == null) {
            return List.of();
        }
        Object refs = resp.get("references");
        if (refs instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private Map<String, Object> fallback(String question, String reason) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("response", "LightRAG 暂不可用（" + reason + "）。");
        map.put("degraded", true);
        map.put("question", question);
        return map;
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
