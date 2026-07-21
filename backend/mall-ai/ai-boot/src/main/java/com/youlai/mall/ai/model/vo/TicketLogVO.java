package com.youlai.mall.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "工单流转日志")
public class TicketLogVO {

    private Long id;
    private Long ticketId;
    private String action;
    private String detail;
    private String operator;
    private LocalDateTime createdAt;
}
