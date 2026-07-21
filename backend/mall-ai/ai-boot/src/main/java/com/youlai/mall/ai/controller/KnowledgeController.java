package com.youlai.mall.ai.controller;

import com.youlai.common.result.Result;
import com.youlai.mall.ai.rag.LightRagClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "AI-知识库")
@RestController
@RequestMapping("/api/v1/ai/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final LightRagClient lightRagClient;

    @Operation(summary = "LightRAG 连通性")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean up = lightRagClient.health();
        data.put("lightrag", up ? "UP" : "DOWN");
        data.put("hint", up ? "可进行文档索引与问答" : "请启动 LightRAG 或检查 ai.lightrag.base-url");
        return Result.success(data);
    }

    @Operation(summary = "知识库问答（转发 LightRAG，不可用时降级）")
    @PostMapping("/query")
    public Result<Map<String, Object>> query(@RequestBody KnowledgeQueryForm form,
                                             @RequestParam(defaultValue = "mix") String mode) {
        Map<String, Object> result = lightRagClient.query(form.getQuestion(), mode);
        return Result.success(result);
    }

    @Data
    public static class KnowledgeQueryForm {
        private String question;
    }
}
