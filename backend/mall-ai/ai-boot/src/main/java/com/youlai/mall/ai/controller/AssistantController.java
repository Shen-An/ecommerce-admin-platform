package com.youlai.mall.ai.controller;

import com.youlai.common.result.Result;
import com.youlai.mall.ai.model.form.AssistantChatForm;
import com.youlai.mall.ai.model.vo.AssistantChatVO;
import com.youlai.mall.ai.service.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI-运营助手")
@RestController
@RequestMapping("/api/v1/ai/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @Operation(summary = "运营助手对话（MVP：规则路由 + 可 mock）")
    @PostMapping("/chat")
    public Result<AssistantChatVO> chat(@Valid @RequestBody AssistantChatForm form) {
        return Result.success(assistantService.chat(form));
    }
}
