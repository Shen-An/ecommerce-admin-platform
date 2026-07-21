package com.youlai.mall.ai.agent;

import com.youlai.mall.ai.model.form.KnowledgeQueryForm;
import com.youlai.mall.ai.model.vo.KnowledgeQueryVO;
import com.youlai.mall.ai.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * PolicyRagAgent：复用 Phase2 知识库检索售后政策。
 */
@Component
@RequiredArgsConstructor
public class PolicyRagAgent {

    private final KnowledgeService knowledgeService;

    public KnowledgeQueryVO retrieve(String userMessage, String intent) {
        String question = buildPolicyQuestion(userMessage, intent);
        KnowledgeQueryForm form = new KnowledgeQueryForm();
        form.setQuestion(question);
        form.setMode("mix");
        try {
            return knowledgeService.query(form);
        } catch (Exception e) {
            return KnowledgeQueryVO.builder()
                    .answer("暂未检索到适用政策，请人工核实。")
                    .source("degraded")
                    .degraded(true)
                    .mode("mix")
                    .hint(e.getMessage())
                    .build();
        }
    }

    private static String buildPolicyQuestion(String userMessage, String intent) {
        String base = StringUtils.hasText(userMessage) ? userMessage : "售后政策";
        return switch (intent == null ? "" : intent) {
            case "refund" -> "退货退款政策：" + base;
            case "logistics" -> "物流异常与停滞处理：" + base;
            case "complaint" -> "客诉处理规范：" + base;
            case "consult" -> "售后咨询：" + base;
            default -> base;
        };
    }
}
