package com.youlai.mall.ai.controller;

import com.youlai.common.result.Result;
import com.youlai.mall.ai.model.form.KnowledgeQueryForm;
import com.youlai.mall.ai.model.form.KnowledgeTextForm;
import com.youlai.mall.ai.model.vo.KnowledgeDocVO;
import com.youlai.mall.ai.model.vo.KnowledgeQueryVO;
import com.youlai.mall.ai.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "AI-知识库")
@RestController
@RequestMapping("/api/v1/ai/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @Operation(summary = "Java RAG / 知识库状态")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(knowledgeService.status());
    }

    @Operation(summary = "文档列表")
    @GetMapping("/docs")
    public Result<List<KnowledgeDocVO>> listDocs() {
        return Result.success(knowledgeService.listDocs());
    }

    @Operation(summary = "文本入库（MySQL + Java 向量分块）")
    @PostMapping("/docs/text")
    public Result<KnowledgeDocVO> ingestText(@Valid @RequestBody KnowledgeTextForm form) {
        return Result.success(knowledgeService.ingestText(form));
    }

    @Operation(summary = "文件上传入库")
    @PostMapping(value = "/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocVO> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "title", required = false) String title) {
        return Result.success(knowledgeService.ingestFile(file, domain, title));
    }

    @Operation(summary = "删除文档与向量分块")
    @DeleteMapping("/docs/{id}")
    public Result<Void> delete(@Parameter(description = "文档ID") @PathVariable Long id) {
        knowledgeService.deleteDoc(id);
        return Result.success();
    }

    @Operation(summary = "知识库问答（Java 向量 RAG → 关键词降级）")
    @PostMapping("/query")
    public Result<KnowledgeQueryVO> query(@Valid @RequestBody KnowledgeQueryForm form,
                                          @RequestParam(required = false) String mode) {
        if (mode != null && (form.getMode() == null || form.getMode().isBlank())) {
            form.setMode(mode);
        }
        return Result.success(knowledgeService.query(form));
    }

    @Operation(summary = "灌入演示语料（售后政策 + 运营SOP，幂等）")
    @PostMapping("/docs/seed")
    public Result<Map<String, Object>> seed() {
        int n = knowledgeService.seedDemoDocs();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", n);
        data.put("message", n == 0 ? "演示语料已存在（并已尝试补建向量）" : "已灌入 " + n + " 篇演示文档");
        return Result.success(data);
    }

    @Operation(summary = "重建 Java 向量索引（Embedding Key 来自模型配置）")
    @PostMapping("/docs/reindex")
    public Result<Map<String, Object>> reindex() {
        int n = knowledgeService.reindexJavaVectors();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pushed", n);
        data.put("indexed", n);
        data.put("message", n == 0 ? "没有可索引文档" : "已为 " + n + " 篇文档重建 Java 向量索引");
        return Result.success(data);
    }

    @Operation(summary = "刷新文档状态")
    @PostMapping("/docs/refresh-status")
    public Result<Map<String, Object>> refreshStatus() {
        int n = knowledgeService.refreshIndexStatus();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("changed", n);
        data.put("message", n == 0 ? "无状态变更" : "已更新 " + n + " 篇文档状态");
        return Result.success(data);
    }
}
