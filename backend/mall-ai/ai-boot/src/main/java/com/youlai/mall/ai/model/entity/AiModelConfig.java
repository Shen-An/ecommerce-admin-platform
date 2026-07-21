package com.youlai.mall.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youlai.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
public class AiModelConfig extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;

    private String chatProvider;
    private String chatBaseUrl;
    private String chatApiKey;
    private String chatModel;
    private BigDecimal chatTemperature;

    private String embeddingProvider;
    private String embeddingBaseUrl;
    private String embeddingApiKey;
    private String embeddingModel;
    private Integer embeddingDim;

    private String lightragBaseUrl;
    private Integer mockEnabled;
    private String extraJson;
    private Long updatedBy;
}
