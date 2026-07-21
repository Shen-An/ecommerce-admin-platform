package com.youlai.mall.ai.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "知识库问答请求")
public class KnowledgeQueryForm {

    @NotBlank(message = "问题不能为空")
    private String question;

    @Schema(description = "检索模式 mix/hybrid/local/global/naive")
    private String mode = "mix";
}
