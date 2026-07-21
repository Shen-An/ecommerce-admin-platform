package com.youlai.mall.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "运营助手回复")
public class AssistantChatVO {

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "助手回复文本")
    private String reply;

    @Schema(description = "识别意图")
    private String intent;

    @Schema(description = "结构化卡片数据（订单/商品等）")
    private List<Map<String, Object>> cards;

    @Schema(description = "是否 mock 降级")
    private Boolean mock;
}
