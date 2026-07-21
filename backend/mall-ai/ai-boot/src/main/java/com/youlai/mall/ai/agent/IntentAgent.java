package com.youlai.mall.ai.agent;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IntentAgent：规则识别投诉/退款/物流/咨询，并抽取订单号。
 */
@Component
public class IntentAgent {

    private static final Pattern ORDER_SN = Pattern.compile(
            "(?:订单号?|order\\s*(?:sn|no)?|订单)[:：\\s#]*([A-Za-z0-9\\-]{6,32})"
                    + "|\\b([A-Z]{0,4}\\d{10,22})\\b",
            Pattern.CASE_INSENSITIVE);

    public IntentResult classify(String message) {
        if (!StringUtils.hasText(message)) {
            return IntentResult.builder()
                    .intent("other")
                    .intentLabel("其他")
                    .confidence(0.3)
                    .priority("low")
                    .build();
        }
        String m = message.toLowerCase(Locale.ROOT);
        String orderSn = extractOrderSn(message);

        if (containsAny(m, "投诉", "差评", "骗子", "假货", "举报", "态度", "服务差", "恶心", "坑")) {
            return IntentResult.builder()
                    .intent("complaint")
                    .intentLabel("投诉")
                    .confidence(0.9)
                    .orderSn(orderSn)
                    .priority("high")
                    .build();
        }
        if (containsAny(m, "退款", "退货", "退换", "七天", "7天", "无理由", "取消订单", "不想要")) {
            return IntentResult.builder()
                    .intent("refund")
                    .intentLabel("退款")
                    .confidence(0.88)
                    .orderSn(orderSn)
                    .priority("medium")
                    .build();
        }
        if (containsAny(m, "物流", "快递", "发货", "到哪了", "未签收", "丢件", "停滞", "运单", "配送")) {
            return IntentResult.builder()
                    .intent("logistics")
                    .intentLabel("物流")
                    .confidence(0.85)
                    .orderSn(orderSn)
                    .priority("medium")
                    .build();
        }
        if (containsAny(m, "咨询", "怎么", "如何", "政策", "售后", "保修", "发票", "能不能")) {
            return IntentResult.builder()
                    .intent("consult")
                    .intentLabel("咨询")
                    .confidence(0.75)
                    .orderSn(orderSn)
                    .priority("low")
                    .build();
        }
        return IntentResult.builder()
                .intent("other")
                .intentLabel("其他")
                .confidence(orderSn != null ? 0.55 : 0.4)
                .orderSn(orderSn)
                .priority("low")
                .build();
    }

    private static String extractOrderSn(String message) {
        Matcher matcher = ORDER_SN.matcher(message);
        if (matcher.find()) {
            String g1 = matcher.group(1);
            if (StringUtils.hasText(g1)) {
                return g1.trim();
            }
            String g2 = matcher.group(2);
            if (StringUtils.hasText(g2)) {
                return g2.trim();
            }
        }
        return null;
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
