package com.youlai.mall.ai.insight;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class InsightPlan {

    private InsightTemplate template;
    private String templateCode;
    private String templateLabel;
    /** 例如 topN=5, threshold=10 */
    private Map<String, Object> params;
    private String reason;
}
