package com.youlai.mall.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.mall.ai.mapper.AiModelConfigMapper;
import com.youlai.mall.ai.model.entity.AiModelConfig;
import com.youlai.mall.ai.model.form.AiModelConfigForm;
import com.youlai.mall.ai.model.vo.AiModelConfigVO;
import com.youlai.mall.ai.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConfigServiceImpl implements AiModelConfigService {

    public static final String DEFAULT_KEY = "default";

    private final AiModelConfigMapper configMapper;
    private final WebClient.Builder webClientBuilder;

    @Override
    public AiModelConfigVO getConfig(String configKey) {
        AiModelConfig entity = getOrCreate(configKey);
        return toVo(entity);
    }

    @Override
    public void saveConfig(AiModelConfigForm form) {
        String key = StringUtils.hasText(form.getConfigKey()) ? form.getConfigKey() : DEFAULT_KEY;
        AiModelConfig entity = getOrCreate(key);

        entity.setChatProvider(form.getChatProvider());
        entity.setChatBaseUrl(form.getChatBaseUrl());
        entity.setChatModel(form.getChatModel());
        entity.setChatTemperature(form.getChatTemperature() != null ? form.getChatTemperature() : new BigDecimal("0.70"));
        if (StringUtils.hasText(form.getChatApiKey())) {
            entity.setChatApiKey(form.getChatApiKey().trim());
        }

        entity.setEmbeddingProvider(form.getEmbeddingProvider());
        entity.setEmbeddingBaseUrl(form.getEmbeddingBaseUrl());
        entity.setEmbeddingModel(form.getEmbeddingModel());
        entity.setEmbeddingDim(form.getEmbeddingDim() != null ? form.getEmbeddingDim() : 2048);
        if (StringUtils.hasText(form.getEmbeddingApiKey())) {
            entity.setEmbeddingApiKey(form.getEmbeddingApiKey().trim());
        }

        entity.setLightragBaseUrl(form.getLightragBaseUrl());
        entity.setMockEnabled(form.getMockEnabled() != null ? form.getMockEnabled() : 1);
        entity.setExtraJson(form.getExtraJson());

        configMapper.updateById(entity);
        log.info("AI model config saved, key={}, chatModel={}, embeddingModel={}",
                key, entity.getChatModel(), entity.getEmbeddingModel());
    }

    @Override
    public AiModelConfig getRuntimeConfig(String configKey) {
        return getOrCreate(configKey);
    }

    @Override
    public Map<String, Object> testConnection(String type, AiModelConfigForm form) {
        Map<String, Object> result = new LinkedHashMap<>();
        String t = type == null ? "embedding" : type.toLowerCase();
        try {
            if ("chat".equals(t)) {
                result.putAll(testChat(form));
            } else if ("lightrag".equals(t)) {
                result.putAll(testLightRag(form));
            } else {
                result.putAll(testEmbedding(form));
            }
            result.putIfAbsent("success", true);
        } catch (Exception ex) {
            log.warn("AI connection test failed type={}: {}", t, ex.getMessage());
            result.put("success", false);
            result.put("message", ex.getMessage());
        }
        return result;
    }

    private Map<String, Object> testChat(AiModelConfigForm form) {
        AiModelConfig saved = getOrCreate(DEFAULT_KEY);
        String baseUrl = firstNonBlank(form.getChatBaseUrl(), saved.getChatBaseUrl(),
                defaultChatBase(form.getChatProvider() != null ? form.getChatProvider() : saved.getChatProvider()));
        String apiKey = firstNonBlank(form.getChatApiKey(), saved.getChatApiKey());
        String model = firstNonBlank(form.getChatModel(), saved.getChatModel(), "qwen-plus");
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("未配置 Chat API Key");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
        body.put("max_tokens", 8);

        Map<?, ?> resp = webClientBuilder.build()
                .post()
                .uri(trimSlash(baseUrl) + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(30));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Chat 接口连通正常");
        out.put("model", model);
        out.put("baseUrl", baseUrl);
        out.put("rawId", resp == null ? null : resp.get("id"));
        return out;
    }

    private Map<String, Object> testEmbedding(AiModelConfigForm form) {
        AiModelConfig saved = getOrCreate(DEFAULT_KEY);
        String baseUrl = firstNonBlank(form.getEmbeddingBaseUrl(), saved.getEmbeddingBaseUrl(),
                "https://integrate.api.nvidia.com/v1");
        String apiKey = firstNonBlank(form.getEmbeddingApiKey(), saved.getEmbeddingApiKey());
        String model = firstNonBlank(form.getEmbeddingModel(), saved.getEmbeddingModel(),
                "nvidia/llama-nemotron-embed-1b-v2");
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("未配置 Embedding API Key");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", "ecommerce admin embedding probe");
        body.put("encoding_format", "float");
        body.put("input_type", "query");

        Map<?, ?> resp = webClientBuilder.build()
                .post()
                .uri(trimSlash(baseUrl) + "/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(45));

        Integer dim = null;
        Object data = resp == null ? null : resp.get("data");
        if (data instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object emb = first.get("embedding");
            if (emb instanceof List<?> vec) {
                dim = vec.size();
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Embedding 接口连通正常");
        out.put("model", model);
        out.put("baseUrl", baseUrl);
        out.put("detectedDim", dim);
        return out;
    }

    private Map<String, Object> testLightRag(AiModelConfigForm form) {
        AiModelConfig saved = getOrCreate(DEFAULT_KEY);
        String baseUrl = firstNonBlank(form.getLightragBaseUrl(), saved.getLightragBaseUrl(), "http://localhost:9621");
        webClientBuilder.build()
                .get()
                .uri(trimSlash(baseUrl) + "/health")
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(8));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "LightRAG 可达");
        out.put("baseUrl", baseUrl);
        return out;
    }

    private AiModelConfig getOrCreate(String configKey) {
        String key = StringUtils.hasText(configKey) ? configKey : DEFAULT_KEY;
        AiModelConfig entity = configMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getConfigKey, key)
                .last("limit 1"));
        if (entity != null) {
            return entity;
        }
        entity = defaultEntity(key);
        configMapper.insert(entity);
        return entity;
    }

    private AiModelConfig defaultEntity(String key) {
        AiModelConfig entity = new AiModelConfig();
        entity.setConfigKey(key);
        entity.setChatProvider("dashscope");
        entity.setChatBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        entity.setChatModel("qwen-plus");
        entity.setChatTemperature(new BigDecimal("0.70"));
        entity.setEmbeddingProvider("nvidia");
        entity.setEmbeddingBaseUrl("https://integrate.api.nvidia.com/v1");
        entity.setEmbeddingModel("nvidia/llama-nemotron-embed-1b-v2");
        entity.setEmbeddingDim(2048);
        entity.setLightragBaseUrl("http://localhost:9621");
        entity.setMockEnabled(1);
        return entity;
    }

    private AiModelConfigVO toVo(AiModelConfig entity) {
        return AiModelConfigVO.builder()
                .configKey(entity.getConfigKey())
                .chatProvider(entity.getChatProvider())
                .chatBaseUrl(entity.getChatBaseUrl())
                .chatApiKeyMasked(mask(entity.getChatApiKey()))
                .chatApiKeyConfigured(StringUtils.hasText(entity.getChatApiKey()))
                .chatModel(entity.getChatModel())
                .chatTemperature(entity.getChatTemperature())
                .embeddingProvider(entity.getEmbeddingProvider())
                .embeddingBaseUrl(entity.getEmbeddingBaseUrl())
                .embeddingApiKeyMasked(mask(entity.getEmbeddingApiKey()))
                .embeddingApiKeyConfigured(StringUtils.hasText(entity.getEmbeddingApiKey()))
                .embeddingModel(entity.getEmbeddingModel())
                .embeddingDim(entity.getEmbeddingDim())
                .lightragBaseUrl(entity.getLightragBaseUrl())
                .mockEnabled(entity.getMockEnabled())
                .extraJson(entity.getExtraJson())
                .chatProviders(chatProviders())
                .embeddingProviders(embeddingProviders())
                .chatPresets(chatPresets())
                .embeddingPresets(embeddingPresets())
                .build();
    }

    private List<AiModelConfigVO.ProviderOption> chatProviders() {
        List<AiModelConfigVO.ProviderOption> list = new ArrayList<>();
        list.add(AiModelConfigVO.ProviderOption.builder().value("dashscope").label("通义 DashScope").defaultBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1").build());
        list.add(AiModelConfigVO.ProviderOption.builder().value("deepseek").label("DeepSeek").defaultBaseUrl("https://api.deepseek.com").build());
        list.add(AiModelConfigVO.ProviderOption.builder().value("openai").label("OpenAI 兼容").defaultBaseUrl("https://api.openai.com/v1").build());
        list.add(AiModelConfigVO.ProviderOption.builder().value("nvidia").label("NVIDIA NIM").defaultBaseUrl("https://integrate.api.nvidia.com/v1").build());
        list.add(AiModelConfigVO.ProviderOption.builder().value("custom").label("自定义").defaultBaseUrl("").build());
        return list;
    }

    private List<AiModelConfigVO.ProviderOption> embeddingProviders() {
        List<AiModelConfigVO.ProviderOption> list = new ArrayList<>();
        list.add(AiModelConfigVO.ProviderOption.builder().value("nvidia").label("NVIDIA Embedding").defaultBaseUrl("https://integrate.api.nvidia.com/v1").build());
        list.add(AiModelConfigVO.ProviderOption.builder().value("openai").label("OpenAI 兼容").defaultBaseUrl("https://api.openai.com/v1").build());
        list.add(AiModelConfigVO.ProviderOption.builder().value("dashscope").label("通义 Embedding").defaultBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1").build());
        list.add(AiModelConfigVO.ProviderOption.builder().value("custom").label("自定义/本地").defaultBaseUrl("http://localhost:8000/v1").build());
        return list;
    }

    private List<AiModelConfigVO.ModelPreset> chatPresets() {
        List<AiModelConfigVO.ModelPreset> list = new ArrayList<>();
        list.add(AiModelConfigVO.ModelPreset.builder().provider("dashscope").model("qwen-plus").label("通义 qwen-plus").baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1").build());
        list.add(AiModelConfigVO.ModelPreset.builder().provider("dashscope").model("qwen-max").label("通义 qwen-max").baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1").build());
        list.add(AiModelConfigVO.ModelPreset.builder().provider("deepseek").model("deepseek-chat").label("DeepSeek Chat").baseUrl("https://api.deepseek.com").build());
        list.add(AiModelConfigVO.ModelPreset.builder().provider("nvidia").model("meta/llama-3.1-8b-instruct").label("NVIDIA Llama 3.1 8B").baseUrl("https://integrate.api.nvidia.com/v1").build());
        return list;
    }

    private List<AiModelConfigVO.ModelPreset> embeddingPresets() {
        List<AiModelConfigVO.ModelPreset> list = new ArrayList<>();
        list.add(AiModelConfigVO.ModelPreset.builder()
                .provider("nvidia")
                .model("nvidia/llama-nemotron-embed-1b-v2")
                .label("NVIDIA Llama Nemotron Embed 1B v2（当前）")
                .embeddingDim(2048)
                .baseUrl("https://integrate.api.nvidia.com/v1")
                .build());
        list.add(AiModelConfigVO.ModelPreset.builder()
                .provider("nvidia")
                .model("nvidia/nv-embedqa-e5-v5")
                .label("NVIDIA NV-EmbedQA-E5-v5")
                .embeddingDim(1024)
                .baseUrl("https://integrate.api.nvidia.com/v1")
                .build());
        list.add(AiModelConfigVO.ModelPreset.builder()
                .provider("dashscope")
                .model("text-embedding-v3")
                .label("通义 text-embedding-v3")
                .embeddingDim(1024)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .build());
        return list;
    }

    private static String mask(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        String k = key.trim();
        if (k.length() <= 8) {
            return "****";
        }
        return k.substring(0, 4) + "****" + k.substring(k.length() - 4);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return null;
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

    private static String defaultChatBase(String provider) {
        if (provider == null) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        return switch (provider) {
            case "deepseek" -> "https://api.deepseek.com";
            case "openai" -> "https://api.openai.com/v1";
            case "nvidia" -> "https://integrate.api.nvidia.com/v1";
            default -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
        };
    }
}
