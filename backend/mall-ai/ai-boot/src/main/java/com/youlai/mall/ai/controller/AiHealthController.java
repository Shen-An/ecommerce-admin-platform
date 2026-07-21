package com.youlai.mall.ai.controller;

import com.youlai.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "AI-健康检查")
@RestController
@RequestMapping("/api/v1/ai")
public class AiHealthController {

    @Value("${spring.application.name:mall-ai}")
    private String appName;

    @Value("${ai.llm.mock-enabled:true}")
    private boolean mockEnabled;

    @Value("${ai.lightrag.base-url:}")
    private String lightRagBaseUrl;

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", appName);
        data.put("status", "UP");
        data.put("time", LocalDateTime.now().toString());
        data.put("llmMockEnabled", mockEnabled);
        data.put("lightRagBaseUrl", lightRagBaseUrl);
        data.put("features", new String[]{"assistant", "knowledge", "ticket-agent", "insight"});
        return Result.success(data);
    }
}
