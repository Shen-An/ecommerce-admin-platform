package com.youlai.mall.ai.controller;

import com.youlai.common.result.Result;
import com.youlai.mall.ai.model.form.TicketActionForm;
import com.youlai.mall.ai.model.form.TicketChatForm;
import com.youlai.mall.ai.model.vo.AiMessageVO;
import com.youlai.mall.ai.model.vo.AiSessionVO;
import com.youlai.mall.ai.model.vo.TicketChatVO;
import com.youlai.mall.ai.model.vo.TicketVO;
import com.youlai.mall.ai.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI-工单Agent")
@RestController
@RequestMapping("/api/v1/ai/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @Operation(summary = "客服/工单多 Agent 对话（建单）")
    @PostMapping("/chat")
    public Result<TicketChatVO> chat(@Valid @RequestBody TicketChatForm form) {
        return Result.success(ticketService.chat(form));
    }

    @Operation(summary = "工单列表")
    @GetMapping("/list")
    public Result<List<TicketVO>> list(
            @Parameter(description = "状态 open/processing/escalated/closed/all")
            @RequestParam(value = "status", required = false, defaultValue = "all") String status) {
        return Result.success(ticketService.listTickets(status));
    }

    @Operation(summary = "工单详情（含流转日志）")
    @GetMapping("/{id}")
    public Result<TicketVO> detail(@PathVariable("id") Long id) {
        return Result.success(ticketService.getTicket(id));
    }

    @Operation(summary = "人工接管 / 升级")
    @PostMapping("/{id}/escalate")
    public Result<TicketVO> escalate(
            @PathVariable("id") Long id,
            @RequestBody(required = false) TicketActionForm form) {
        return Result.success(ticketService.escalate(id, form != null ? form : new TicketActionForm()));
    }

    @Operation(summary = "关闭工单")
    @PostMapping("/{id}/close")
    public Result<TicketVO> close(
            @PathVariable("id") Long id,
            @RequestBody(required = false) TicketActionForm form) {
        return Result.success(ticketService.close(id, form != null ? form : new TicketActionForm()));
    }

    @Operation(summary = "工单场景会话列表")
    @GetMapping("/sessions")
    public Result<List<AiSessionVO>> listSessions() {
        return Result.success(ticketService.listSessions());
    }

    @Operation(summary = "会话消息")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<AiMessageVO>> listMessages(@PathVariable("id") Long id) {
        return Result.success(ticketService.listMessages(id));
    }
}
