package com.youlai.mall.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "AI 历史消息")
public class AiMessageVO {

    private Long id;
    private String role;
    private String content;
    private List<Map<String, Object>> cards;
    private LocalDateTime createdAt;
}
