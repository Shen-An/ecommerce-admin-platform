package com.youlai.mall.ai.service;

import com.youlai.mall.ai.model.entity.AiModelConfig;
import com.youlai.mall.ai.model.form.AiModelConfigForm;
import com.youlai.mall.ai.model.vo.AiModelConfigVO;

import java.util.Map;

public interface AiModelConfigService {

    AiModelConfigVO getConfig(String configKey);

    void saveConfig(AiModelConfigForm form);

    /**
     * 运行时完整配置（含密钥，仅服务内部使用）
     */
    AiModelConfig getRuntimeConfig(String configKey);

    Map<String, Object> testConnection(String type, AiModelConfigForm form);
}
