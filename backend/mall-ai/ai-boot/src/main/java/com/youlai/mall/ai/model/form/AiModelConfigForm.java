package com.youlai.mall.ai.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "AI 模型配置表单")
public class AiModelConfigForm {

    @Schema(description = "配置键，默认 default")
    private String configKey = "default";

    @NotBlank
    @Schema(description = "对话模型提供商")
    private String chatProvider;

    @Schema(description = "对话 API Base URL")
    private String chatBaseUrl;

    @Schema(description = "对话 API Key（留空表示不修改已有密钥）")
    private String chatApiKey;

    @NotBlank
    @Schema(description = "对话模型名")
    private String chatModel;

    @Schema(description = "温度")
    private BigDecimal chatTemperature;

    @NotBlank
    @Schema(description = "Embedding 提供商")
    private String embeddingProvider;

    @Schema(description = "Embedding API Base URL")
    private String embeddingBaseUrl;

    @Schema(description = "Embedding API Key（留空表示不修改已有密钥）")
    private String embeddingApiKey;

    @NotBlank
    @Schema(description = "Embedding 模型名")
    private String embeddingModel;

    @Schema(description = "向量维度")
    private Integer embeddingDim;

    @Schema(description = "LightRAG 服务地址")
    private String lightragBaseUrl;

    @Schema(description = "是否启用 Mock 降级 1/0")
    private Integer mockEnabled;

    @Schema(description = "扩展 JSON")
    private String extraJson;
}
