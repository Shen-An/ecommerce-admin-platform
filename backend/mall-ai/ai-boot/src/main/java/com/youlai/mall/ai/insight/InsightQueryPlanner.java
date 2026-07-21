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
            return of(InsightTemplate.LOW_STOCK, params, "命中库存/预警关键词");
        }
        if (containsAny(q, "退货", "退款率", "取消率", "售后率", "退单")) {
            return of(InsightTemplate.REFUND_RATE, params, "命中退货/取消/售后占比");
        }
        if (containsAny(q, "gmv", "成交额", "销售额", "流水", "支付金额", "营收")) {
            return of(InsightTemplate.GMV_SNAPSHOT, params, "命中 GMV/成交额");
        }
        if (containsAny(q, "品类", "分类", "类目") && containsAny(q, "销量", "分布", "排行", "top", "占比")) {
            return of(InsightTemplate.CATEGORY_SALES, params, "命中品类销量");
        }
        if (containsAny(q, "品类分布", "分类销量", "类目销量")) {
            return of(InsightTemplate.CATEGORY_SALES, params, "命中品类分布");
        }
        if (containsAny(q, "销量", "top", "畅销", "排行", "热销", "卖得")) {
            return of(InsightTemplate.SALES_TOPN, params, "命中销量/TopN/排行");
        }
        if (containsAny(q, "状态分布", "订单分布", "待发货", "待付款", "订单状态", "漏斗")) {
            return of(InsightTemplate.ORDER_STATUS_DIST, params, "命中订单状态分布");
        }
        if (containsAny(q, "看板", "综合", "运营", "概况", "dashboard", "怎么样")) {
            return of(InsightTemplate.OPS_DASHBOARD, params, "命中综合运营");
        }
        return of(InsightTemplate.SALES_TOPN, params, "未精确匹配，默认销量 TopN（白名单）");
    }

    private static InsightPlan of(InsightTemplate t, Map<String, Object> params, String reason) {
        return InsightPlan.builder()
                .template(t)
                .templateCode(t.getCode())
                .templateLabel(t.getLabel())
                .params(params)
                .reason(reason)
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
