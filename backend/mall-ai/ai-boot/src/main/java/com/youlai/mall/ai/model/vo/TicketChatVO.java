package com.youlai.mall.ai.model.vo;

import com.youlai.mall.ai.agent.AgentStep;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "工单 Agent 对话结果")
public class TicketChatVO {

    private Long sessionId;
    private Long ticketId;
    private String reply;
    private String intent;
    private String intentLabel;
    private Double confidence;
    private String orderSn;
    private String priority;
    private String status;
    private Boolean escalated;
    private List<String> escalateReasons;
    private String policySource;
    private String policySnippet;
    private List<AgentStep> steps;
    private List<Map<String, Object>> references;
}
