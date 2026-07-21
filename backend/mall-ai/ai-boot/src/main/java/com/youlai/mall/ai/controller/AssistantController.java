package com.youlai.mall.ai.controller;

import com.youlai.common.result.Result;
import com.youlai.mall.ai.model.form.AssistantChatForm;
import com.youlai.mall.ai.model.vo.AiMessageVO;
import com.youlai.mall.ai.model.vo.AiSessionVO;
import com.youlai.mall.ai.model.vo.AssistantChatVO;
import com.youlai.mall.ai.service.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI-运营助手")
@RestController
@RequestMapping("/api/v1/ai/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @Operation(summary = "运营助手对话（会话落库 + Tool + 可选 LLM 润色）")
    @PostMapping("/chat")
    public Result<AssistantChatVO> chat(@Valid @RequestBody AssistantChatForm form) {
        return Result.success(assistantService.chat(form));
    }

    @Operation(summary = "当前用户会话列表")
    @GetMapping("/sessions")
    public Result<List<AiSessionVO>> listSessions() {
        return Result.success(assistantService.listSessions());
    }

    @Operation(summary = "会话历史消息")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<AiMessageVO>> listMessages(
            @Parameter(description = "会话ID") @PathVariable("id") Long id) {
        return Result.success(assistantService.listMessages(id));
    }

    @Operation(summary = "结束/删除会话")
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(
            @Parameter(description = "会话ID") @PathVariable("id") Long id) {
        assistantService.deleteSession(id);
        return Result.success();
    }
}
