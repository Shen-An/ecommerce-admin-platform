package com.youlai.mall.ai.insight;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自然语言 → 白名单模板（规则匹配，不生成 SQL）。
 */
@Component
public class InsightQueryPlanner {

    private static final Pattern TOP_N = Pattern.compile("top\\s*(\\d+)|前\\s*(\\d+)|(\\d+)\\s*名", Pattern.CASE_INSENSITIVE);

    public InsightPlan plan(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        Map<String, Object> params = new LinkedHashMap<>();
        int topN = extractTopN(question, 5);
        params.put("topN", topN);
        params.put("threshold", 10);

        if (containsAny(q, "库存", "缺货", "预警", "补货", "stock")) {
            return InsightPlan.builder()
                    .template(InsightTemplate.LOW_STOCK)
                    .templateCode(InsightTemplate.LOW_STOCK.getCode())
                    .templateLabel(InsightTemplate.LOW_STOCK.getLabel())
                    .params(params)
                    .reason("命中库存/预警关键词")
                    .build();
        }
        if (containsAny(q, "退货", "退款率", "取消率", "售后率", "退单")) {
            return InsightPlan.builder()
                    .template(InsightTemplate.REFUND_RATE)
                    .templateCode(InsightTemplate.REFUND_RATE.getCode())
                    .templateLabel(InsightTemplate.REFUND_RATE.getLabel())
                    .params(params)
                    .reason("命中退货/取消/售后占比")
                    .build();
        }
        if (containsAny(q, "销量", "top", "畅销", "排行", "品类", "热销", "卖得")) {
            return InsightPlan.builder()
                    .template(InsightTemplate.SALES_TOPN)
                    .templateCode(InsightTemplate.SALES_TOPN.getCode())
                    .templateLabel(InsightTemplate.SALES_TOPN.getLabel())
                    .params(params)
                    .reason("命中销量/TopN/排行")
                    .build();
        }
        if (containsAny(q, "状态分布", "订单分布", "待发货", "待付款", "订单状态", "漏斗")) {
            return InsightPlan.builder()
                    .template(InsightTemplate.ORDER_STATUS_DIST)
                    .templateCode(InsightTemplate.ORDER_STATUS_DIST.getCode())
                    .templateLabel(InsightTemplate.ORDER_STATUS_DIST.getLabel())
                    .params(params)
                    .reason("命中订单状态分布")
                    .build();
        }
        if (containsAny(q, "看板", "综合", "运营", "概况", "dashboard", "怎么样")) {
            return InsightPlan.builder()
                    .template(InsightTemplate.OPS_DASHBOARD)
                    .templateCode(InsightTemplate.OPS_DASHBOARD.getCode())
                    .templateLabel(InsightTemplate.OPS_DASHBOARD.getLabel())
                    .params(params)
                    .reason("命中综合运营")
                    .build();
        }
        // 默认：销量 TopN（演示常用）
        return InsightPlan.builder()
                .template(InsightTemplate.SALES_TOPN)
                .templateCode(InsightTemplate.SALES_TOPN.getCode())
                .templateLabel(InsightTemplate.SALES_TOPN.getLabel())
                .params(params)
                .reason("未精确匹配，默认销量 TopN（白名单）")
                .build();
    }

    private static int extractTopN(String question, int defaultN) {
        if (!StringUtils.hasText(question)) {
            return defaultN;
        }
        Matcher m = TOP_N.matcher(question);
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) {
                    try {
                        int n = Integer.parseInt(m.group(i));
                        return Math.min(Math.max(n, 3), 20);
                    } catch (NumberFormatException ignored) {
                        // fall through
                    }
                }
            }
        }
        return defaultN;
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
