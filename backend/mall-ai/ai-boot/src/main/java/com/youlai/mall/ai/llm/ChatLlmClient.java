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
 * OpenAI 兼容 Chat Completions 客户端（通义/DeepSeek/自定义）。
 * Phase1 仅做润色：Tool 结果已由规则路由拿到，再可选地让模型生成自然语言。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatLlmClient {

    private final WebClient.Builder webClientBuilder;

    public boolean isAvailable(AiModelConfig config) {
        return config != null
                && (config.getMockEnabled() == null || config.getMockEnabled() == 0)
                && StringUtils.hasText(config.getChatApiKey())
                && StringUtils.hasText(config.getChatModel());
    }

    /**
     * @return 模型回复文本；失败返回 null（调用方保留规则 reply）
     */
    @SuppressWarnings("unchecked")
    public String polishReply(AiModelConfig config, String userMessage, String toolReply, String intent) {
        if (!isAvailable(config)) {
            return null;
        }
        try {
            String baseUrl = trimSlash(StringUtils.hasText(config.getChatBaseUrl())
                    ? config.getChatBaseUrl()
                    : "https://dashscope.aliyuncs.com/compatible-mode/v1");
            String system = "你是电商运营后台的智能助手。根据工具查询结果，用简洁中文回答运营人员。"
                    + "不要编造订单号或库存数字；工具结果里没有的数据不要虚构。"
                    + "保持专业、简短，可分点。";
            String user = "用户问题：" + userMessage
                    + "\n识别意图：" + intent
                    + "\n工具原始结果：\n" + toolReply
                    + "\n请基于工具结果生成回复。";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.getChatModel());
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", system));
            messages.add(Map.of("role", "user", "content", user));
            body.put("messages", messages);
            body.put("temperature", config.getChatTemperature() != null
                    ? config.getChatTemperature().doubleValue() : 0.5);
            body.put("max_tokens", 512);

            Map<?, ?> resp = webClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getChatApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(45));

            if (resp == null) {
                return null;
            }
            Object choices = resp.get("choices");
            if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                Object message = first.get("message");
                if (message instanceof Map<?, ?> msg) {
                    Object content = msg.get("content");
                    if (content != null && StringUtils.hasText(content.toString())) {
                        return content.toString().trim();
                    }
                }
            }
            return null;
        } catch (Exception ex) {
            log.warn("LLM polish failed: {}", ex.getMessage());
            return null;
        }
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
