package com.youlai.mall.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "知识库问答结果")
public class KnowledgeQueryVO {

    private String answer;
    private String mode;
    /** lightrag / local / degraded */
    private String source;
    private Boolean degraded;
    private List<Map<String, Object>> references;
    private String hint;
}
