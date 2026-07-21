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

    @Operation(summary = "LightRAG / 本地知识库状态")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(knowledgeService.status());
    }

    @Operation(summary = "文档列表")
    @GetMapping("/docs")
    public Result<List<KnowledgeDocVO>> listDocs() {
        return Result.success(knowledgeService.listDocs());
    }

    @Operation(summary = "文本入库（同步写 MySQL，LightRAG 可用则索引）")
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

    @Operation(summary = "删除文档元数据")
    @DeleteMapping("/docs/{id}")
    public Result<Void> delete(@Parameter(description = "文档ID") @PathVariable Long id) {
        knowledgeService.deleteDoc(id);
        return Result.success();
    }

    @Operation(summary = "知识库问答（LightRAG 优先，失败本地关键词降级）")
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
        data.put("message", n == 0 ? "演示语料已存在" : "已灌入 " + n + " 篇演示文档");
        return Result.success(data);
    }
}
