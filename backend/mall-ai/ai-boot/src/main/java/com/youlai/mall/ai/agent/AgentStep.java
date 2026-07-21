package com.youlai.mall.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流水线单步结果（前端时间线展示）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStep {

    private String name;
    private String status;
    private String detail;
    private Long durationMs;
}
