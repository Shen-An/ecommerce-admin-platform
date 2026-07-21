package com.youlai.mall.ai.agent;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * EscalationGate：低置信度 / 敏感词 → 升级人工。
 */
@Component
public class EscalationGate {

    private static final String[] SENSITIVE = {
            "律师", "起诉", "报警", "工商", "消协", "媒体", "曝光",
            "假货", "骗子", "诈骗", "人身安全", "威胁", "赔偿金", "12315"
    };

    public EscalationDecision evaluate(IntentResult intent, String message) {
        List<String> reasons = new ArrayList<>();
        boolean escalate = false;

        if (intent.getConfidence() < 0.55) {
            escalate = true;
            reasons.add("意图置信度偏低(" + String.format(Locale.ROOT, "%.2f", intent.getConfidence()) + ")");
        }
        if ("complaint".equals(intent.getIntent()) && intent.getConfidence() >= 0.85) {
            escalate = true;
            reasons.add("高优先级投诉");
        }
        if ("high".equalsIgnoreCase(intent.getPriority())) {
            escalate = true;
            if (!reasons.contains("高优先级投诉")) {
                reasons.add("优先级 high");
            }
        }
        String hit = matchSensitive(message);
        if (hit != null) {
            escalate = true;
            reasons.add("命中敏感词:" + hit);
        }
        if (!StringUtils.hasText(intent.getOrderSn())
                && ("refund".equals(intent.getIntent()) || "logistics".equals(intent.getIntent()))) {
            // 不强制升级，但记原因
            reasons.add("未识别到订单号");
        }

        return EscalationDecision.builder()
                .escalate(escalate)
                .reasons(reasons)
                .build();
    }

    private static String matchSensitive(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String m = message.toLowerCase(Locale.ROOT);
        for (String s : SENSITIVE) {
            if (m.contains(s.toLowerCase(Locale.ROOT))) {
                return s;
            }
        }
        return null;
    }
}
