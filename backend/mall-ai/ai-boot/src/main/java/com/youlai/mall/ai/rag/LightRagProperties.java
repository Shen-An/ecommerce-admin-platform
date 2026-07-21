package com.youlai.mall.ai.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.lightrag")
public class LightRagProperties {

    /**
     * LightRAG Server 地址，例如 http://localhost:9621
     */
    private String baseUrl = "http://localhost:9621";

    private long timeoutMs = 60000L;
}
