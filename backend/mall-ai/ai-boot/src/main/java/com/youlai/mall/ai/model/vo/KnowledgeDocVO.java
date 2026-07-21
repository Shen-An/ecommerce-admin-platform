package com.youlai.mall.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "知识库文档")
public class KnowledgeDocVO {

    private Long id;
    private String title;
    private String domain;
    private String fileName;
    private String fileUrl;
    private String lightragDocId;
    private String status;
    private String errorMsg;
    private Integer contentLength;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
