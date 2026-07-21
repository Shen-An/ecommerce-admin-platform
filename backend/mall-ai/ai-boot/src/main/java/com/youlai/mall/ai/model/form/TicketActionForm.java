package com.youlai.mall.ai.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "工单操作（接管/关闭）")
public class TicketActionForm {

    private String reason;
}
