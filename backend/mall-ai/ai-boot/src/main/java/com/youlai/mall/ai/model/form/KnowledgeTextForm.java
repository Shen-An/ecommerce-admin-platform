package com.youlai.mall.ai.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "知识库文本文档入库")
public class KnowledgeTextForm {

    @NotBlank(message = "标题不能为空")
    private String title;

    @Schema(description = "领域：售后/运营/商品/general")
    private String domain;

    @NotBlank(message = "正文不能为空")
    private String content;
}
