package com.youlai.mall.ai.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "运营助手对话请求")
public class AssistantChatForm {

    @Schema(description = "会话ID，空则新建")
    private Long sessionId;

    @NotBlank(message = "消息不能为空")
    @Schema(description = "用户消息")
    private String message;
}
