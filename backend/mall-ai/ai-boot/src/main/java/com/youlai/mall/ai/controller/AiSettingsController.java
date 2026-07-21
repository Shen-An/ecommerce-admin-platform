package com.youlai.mall.ai.controller;

import com.youlai.common.result.Result;
import com.youlai.mall.ai.model.form.AiModelConfigForm;
import com.youlai.mall.ai.model.vo.AiModelConfigVO;
import com.youlai.mall.ai.service.AiModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "AI-模型配置")
@RestController
@RequestMapping("/api/v1/ai/settings")
@RequiredArgsConstructor
public class AiSettingsController {

    private final AiModelConfigService modelConfigService;

    @Operation(summary = "获取模型配置（密钥脱敏）")
    @GetMapping
    public Result<AiModelConfigVO> get(@RequestParam(defaultValue = "default") String configKey) {
        return Result.success(modelConfigService.getConfig(configKey));
    }

    @Operation(summary = "保存模型配置")
    @PutMapping
    public Result<Void> save(@Valid @RequestBody AiModelConfigForm form) {
        modelConfigService.saveConfig(form);
        return Result.success();
    }

    @Operation(summary = "连通性测试 type=chat|embedding|lightrag")
    @PostMapping("/test")
    public Result<Map<String, Object>> test(@RequestParam(defaultValue = "embedding") String type,
                                            @RequestBody(required = false) AiModelConfigForm form) {
        if (form == null) {
            form = new AiModelConfigForm();
        }
        return Result.success(modelConfigService.testConnection(type, form));
    }
}
