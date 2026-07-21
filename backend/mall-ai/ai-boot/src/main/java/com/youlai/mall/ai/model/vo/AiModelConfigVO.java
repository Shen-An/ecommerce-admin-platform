package com.youlai.mall.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "AI 模型配置（密钥脱敏）")
public class AiModelConfigVO {

    private String configKey;

    private String chatProvider;
    private String chatBaseUrl;
    private String chatApiKeyMasked;
    private Boolean chatApiKeyConfigured;
    private String chatModel;
    private BigDecimal chatTemperature;

    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingApiKeyMasked;
    private Boolean embeddingApiKeyConfigured;
    private String embeddingModel;
    private Integer embeddingDim;

    private String lightragBaseUrl;
    private Integer mockEnabled;
    private String extraJson;

    private List<ProviderOption> chatProviders;
    private List<ProviderOption> embeddingProviders;
    private List<ModelPreset> chatPresets;
    private List<ModelPreset> embeddingPresets;

    @Data
    @Builder
    public static class ProviderOption {
        private String value;
        private String label;
        private String defaultBaseUrl;
    }

    @Data
    @Builder
    public static class ModelPreset {
        private String provider;
        private String model;
        private String label;
        private Integer embeddingDim;
        private String baseUrl;
    }
}
