package com.youlai.mall.ai.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "数据洞察问答")
public class InsightQueryForm {

    @NotBlank(message = "问题不能为空")
    private String question;

    @Schema(description = "强制模板 code，可选；不传则由规划器选择")
    private String template;
}
