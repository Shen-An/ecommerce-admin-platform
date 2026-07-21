package com.youlai.mall.ai.insight;

/**
 * 白名单查询模板 ID（禁止任意 SQL）。
 */
public enum InsightTemplate {

    /** 订单状态分布 */
    ORDER_STATUS_DIST("order_status_dist", "订单状态分布"),

    /** 商品销量 TopN */
    SALES_TOPN("sales_topn", "商品销量 TopN"),

    /** 库存预警 */
    LOW_STOCK("low_stock", "库存预警"),

    /** 取消/售后占比（近似退货率） */
    REFUND_RATE("refund_rate", "取消与售后占比"),

    /** 品类销量聚合 */
    CATEGORY_SALES("category_sales", "品类销量分布"),

    /** GMV 抽样快照（首页订单金额） */
    GMV_SNAPSHOT("gmv_snapshot", "GMV 抽样快照"),

    /** 综合运营看板 */
    OPS_DASHBOARD("ops_dashboard", "运营综合看板");

    private final String code;
    private final String label;

    InsightTemplate(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static InsightTemplate fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (InsightTemplate t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        return null;
    }
}
