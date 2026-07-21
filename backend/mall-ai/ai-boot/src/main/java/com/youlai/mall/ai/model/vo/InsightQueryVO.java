package com.youlai.mall.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@Schema(description = "数据洞察结果")
public class InsightQueryVO {

    private Long queryId;
    private String question;
    private String templateCode;
    private String templateLabel;
    private String planReason;
    private Map<String, Object> params;
    private String chartType;
    /** ECharts option JSON 对象 */
    private Map<String, Object> option;
    private String narrative;
    private Map<String, Object> metrics;
    private Boolean whitelist;
    private String securityNote;
    private LocalDateTime createdAt;
}
