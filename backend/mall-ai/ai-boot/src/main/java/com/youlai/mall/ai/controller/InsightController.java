package com.youlai.mall.ai.controller;

import com.youlai.common.result.Result;
import com.youlai.mall.ai.model.form.InsightQueryForm;
import com.youlai.mall.ai.model.vo.InsightHistoryVO;
import com.youlai.mall.ai.model.vo.InsightQueryVO;
import com.youlai.mall.ai.service.InsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "AI-数据洞察")
@RestController
@RequestMapping("/api/v1/ai/insight")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @Operation(summary = "自然语言洞察（白名单模板 + ECharts）")
    @PostMapping("/query")
    public Result<InsightQueryVO> query(@Valid @RequestBody InsightQueryForm form) {
        return Result.success(insightService.query(form));
    }

    @Operation(summary = "历史查询")
    @GetMapping("/history")
    public Result<List<InsightHistoryVO>> history(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.success(insightService.history(limit));
    }

    @Operation(summary = "白名单模板列表")
    @GetMapping("/templates")
    public Result<List<Map<String, String>>> templates() {
        return Result.success(insightService.templates());
    }
}
