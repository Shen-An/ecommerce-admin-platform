package com.youlai.mall.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "工单详情")
public class TicketVO {

    private Long id;
    private Long sessionId;
    private String orderSn;
    private String intent;
    private String priority;
    private String status;
    private String summary;
    private String assignee;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TicketLogVO> logs;
}
