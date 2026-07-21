package com.youlai.mall.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "洞察历史摘要")
public class InsightHistoryVO {

    private Long id;
    private String question;
    private String templateCode;
    private String templateLabel;
    private LocalDateTime createdAt;
}
