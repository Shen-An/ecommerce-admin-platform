package com.youlai.mall.ai.llm;

import com.youlai.mall.ai.model.entity.AiModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Embeddings（NVIDIA / DashScope / 自定义）。
 * Key 全部来自模型配置页，无需 LightRAG .env。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private final WebClient.Builder webClientBuilder;

    public boolean isAvailable(AiModelConfig config) {
        return config != null
                && StringUtils.hasText(config.getEmbeddingApiKey())
                && StringUtils.hasText(config.getEmbeddingModel());
    }

    /**
     * @return 向量；失败返回 null
     */
    @SuppressWarnings("unchecked")
    public float[] embed(AiModelConfig config, String text) {
        if (!isAvailable(config) || !StringUtils.hasText(text)) {
            return null;
        }
        try {
            String baseUrl = trimSlash(StringUtils.hasText(config.getEmbeddingBaseUrl())
                    ? config.getEmbeddingBaseUrl()
                    : "https://integrate.api.nvidia.com/v1");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.getEmbeddingModel());
            body.put("input", text.length() > 6000 ? text.substring(0, 6000) : text);
            // 部分 NVIDIA 模型需要
            body.put("encoding_format", "float");
            if (config.getEmbeddingDim() != null && config.getEmbeddingDim() > 0) {
                body.put("dimensions", config.getEmbeddingDim());
            }

            Map<?, ?> resp = webClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getEmbeddingApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(60));

            if (resp == null) {
                return null;
            }
            Object data = resp.get("data");
            if (!(data instanceof List<?> list) || list.isEmpty()) {
                return null;
            }
            Object first = list.get(0);
            if (!(first instanceof Map<?, ?> item)) {
                return null;
            }
            Object emb = item.get("embedding");
            if (!(emb instanceof List<?> nums)) {
                return null;
            }
            float[] vec = new float[nums.size()];
            for (int i = 0; i < nums.size(); i++) {
                Object n = nums.get(i);
                if (n instanceof Number num) {
                    vec[i] = num.floatValue();
                } else {
                    vec[i] = Float.parseFloat(String.valueOf(n));
                }
            }
            return vec;
        } catch (Exception ex) {
            log.warn("embedding failed: {}", ex.getMessage());
            return null;
        }
    }

    public List<float[]> embedBatch(AiModelConfig config, List<String> texts) {
        List<float[]> out = new ArrayList<>();
        if (texts == null) {
            return out;
        }
        for (String t : texts) {
            out.add(embed(config, t));
        }
        return out;
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return -1;
        }
        int n = Math.min(a.length, b.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return -1;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static String toJson(float[] vec) {
        if (vec == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(vec.length * 8);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public static float[] fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        String s = json.trim();
        if (s.startsWith("[")) {
            s = s.substring(1);
        }
        if (s.endsWith("]")) {
            s = s.substring(0, s.length() - 1);
        }
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String[] parts = s.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i].trim());
        }
        return vec;
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
