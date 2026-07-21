package com.youlai.mall.ai.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "工单 Agent 对话请求")
public class TicketChatForm {

    private Long sessionId;

    @NotBlank(message = "消息不能为空")
    private String message;
}
