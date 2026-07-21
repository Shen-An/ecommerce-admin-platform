package com.youlai.mall.ai.insight;

import com.youlai.common.result.PageResult;
import com.youlai.mall.ai.client.ProductAdminFeignClient;
import com.youlai.mall.ai.client.dto.SpuPageItemDTO;
import com.youlai.mall.ai.tool.OrderQueryTool;
import com.youlai.mall.ai.tool.ProductQueryTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 白名单模板执行器：仅通过 Feign 只读聚合，不拼 SQL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightQueryExecutor {

    private final OrderQueryTool orderQueryTool;
    private final ProductQueryTool productQueryTool;
    private final ProductAdminFeignClient productAdminFeignClient;

    public Map<String, Object> execute(InsightPlan plan) {
        InsightTemplate template = plan.getTemplate();
        int topN = intParam(plan, "topN", 5);
        int threshold = intParam(plan, "threshold", 10);

        return switch (template) {
            case ORDER_STATUS_DIST -> orderStatusDist();
            case SALES_TOPN -> salesTopN(topN);
            case LOW_STOCK -> lowStock(threshold, topN);
            case REFUND_RATE -> refundRate();
            case CATEGORY_SALES -> categorySales(topN);
            case GMV_SNAPSHOT -> gmvSnapshot();
            case OPS_DASHBOARD -> opsDashboard(threshold);
        };
    }

    private Map<String, Object> orderStatusDist() {
        long unpaid = safe(orderQueryTool.countByStatus(0));
        long paid = safe(orderQueryTool.countByStatus(1));
        long shipped = safe(orderQueryTool.countByStatus(2));
        long complete = safe(orderQueryTool.countByStatus(3));
        long canceled = safe(orderQueryTool.countByStatus(4));
        long servicing = safe(orderQueryTool.countByStatus(5));

        List<String> names = List.of("待付款", "待发货", "已发货", "已完成", "已取消", "售后中");
        List<Long> values = List.of(unpaid, paid, shipped, complete, canceled, servicing);
        List<Map<String, Object>> pieData = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", names.get(i));
            item.put("value", values.get(i));
            pieData.add(item);
        }

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of("trigger", "item"));
        option.put("legend", Map.of("orient", "vertical", "left", "left"));
        option.put("series", List.of(Map.of(
                "name", "订单状态",
                "type", "pie",
                "radius", "55%",
                "data", pieData
        )));

        long total = values.stream().mapToLong(Long::longValue).sum();
        String narrative = String.format(
                "当前订单分状态合计约 %d 笔：待付款 %d、待发货 %d、已发货 %d、已完成 %d、已取消 %d、售后中 %d。",
                total, unpaid, paid, shipped, complete, canceled, servicing);

        return result("pie", option, narrative, Map.of(
                "unpaid", unpaid, "paid", paid, "shipped", shipped,
                "complete", complete, "canceled", canceled, "servicing", servicing, "total", total
        ));
    }

    private Map<String, Object> salesTopN(int topN) {
        List<SpuPageItemDTO> list = fetchSpus(Math.max(topN, 20));
        list = list.stream()
                .sorted(Comparator.comparingInt((SpuPageItemDTO s) -> s.getSales() == null ? 0 : s.getSales()).reversed())
                .limit(topN)
                .collect(Collectors.toList());

        List<String> names = new ArrayList<>();
        List<Integer> sales = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SpuPageItemDTO s : list) {
            String name = s.getName() != null ? s.getName() : ("SPU-" + s.getId());
            if (name.length() > 12) {
                name = name.substring(0, 12) + "…";
            }
            names.add(name);
            int sale = s.getSales() == null ? 0 : s.getSales();
            sales.add(sale);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("spuId", s.getId());
            row.put("name", s.getName());
            row.put("sales", sale);
            row.put("categoryName", s.getCategoryName());
            row.put("brandName", s.getBrandName());
            rows.add(row);
        }

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("grid", Map.of("left", "3%", "right", "4%", "bottom", "3%", "containLabel", true));
        option.put("xAxis", Map.of("type", "category", "data", names, "axisLabel", Map.of("interval", 0, "rotate", 30)));
        option.put("yAxis", Map.of("type", "value", "name", "销量"));
        option.put("series", List.of(Map.of(
                "name", "销量",
                "type", "bar",
                "data", sales,
                "itemStyle", Map.of("color", "#409EFF")
        )));

        String topName = list.isEmpty() ? "无" : (list.get(0).getName() != null ? list.get(0).getName() : "-");
        String narrative = list.isEmpty()
                ? "暂无商品销量数据（请确认 PMS 有上架商品）。"
                : String.format("按当前 PMS 分页抽样，销量 Top%d 榜首为「%s」（%d）。图表基于管理端商品列表销量字段，非任意 SQL。",
                topN, topName, list.get(0).getSales() == null ? 0 : list.get(0).getSales());

        return result("bar", option, narrative, Map.of("rows", rows, "topN", topN));
    }

    private Map<String, Object> lowStock(int threshold, int topN) {
        List<SpuPageItemDTO> list = fetchSpus(30);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Integer> stocks = new ArrayList<>();
        for (SpuPageItemDTO s : list) {
            int stock = sumStock(s);
            if (stock >= 0 && stock < threshold) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("spuId", s.getId());
                row.put("name", s.getName());
                row.put("stock", stock);
                row.put("sales", s.getSales());
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparingInt(r -> (Integer) r.get("stock")));
        if (rows.size() > topN) {
            rows = new ArrayList<>(rows.subList(0, topN));
        }
        for (Map<String, Object> r : rows) {
            String n = String.valueOf(r.get("name"));
            if (n.length() > 12) {
                n = n.substring(0, 12) + "…";
            }
            names.add(n);
            stocks.add((Integer) r.get("stock"));
        }

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("xAxis", Map.of("type", "category", "data", names, "axisLabel", Map.of("rotate", 30)));
        option.put("yAxis", Map.of("type", "value", "name", "库存"));
        option.put("series", List.of(Map.of(
                "name", "库存",
                "type", "bar",
                "data", stocks,
                "itemStyle", Map.of("color", "#E6A23C")
        )));

        String narrative = String.format(
                "库存 < %d 的 SPU 抽样命中 %d 个（展示 Top %d）。建议优先补货销量高且库存低的 SKU。",
                threshold, rows.size(), topN);

        return result("bar", option, narrative, Map.of("rows", rows, "threshold", threshold));
    }

    private Map<String, Object> refundRate() {
        long complete = safe(orderQueryTool.countByStatus(3));
        long canceled = safe(orderQueryTool.countByStatus(4));
        long servicing = safe(orderQueryTool.countByStatus(5));
        long paid = safe(orderQueryTool.countByStatus(1));
        long shipped = safe(orderQueryTool.countByStatus(2));
        long unpaid = safe(orderQueryTool.countByStatus(0));
        long total = unpaid + paid + shipped + complete + canceled + servicing;
        long bad = canceled + servicing;
        double rate = total > 0 ? (bad * 100.0 / total) : 0.0;

        List<Map<String, Object>> pieData = List.of(
                Map.of("name", "正常链路(含进行中)", "value", Math.max(total - bad, 0)),
                Map.of("name", "已取消", "value", canceled),
                Map.of("name", "售后中", "value", servicing)
        );
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of("trigger", "item"));
        option.put("series", List.of(Map.of(
                "type", "pie",
                "radius", new String[]{"40%", "65%"},
                "data", pieData
        )));

        String narrative = String.format(
                "以「已取消+售后中」近似异常/退货压力：合计 %d / 总量 %d ≈ %.1f%%。已完成 %d，待发货 %d，已发货 %d。（非真实退货率表，白名单状态聚合）",
                bad, total, rate, complete, paid, shipped);

        return result("pie", option, narrative, Map.of(
                "total", total, "canceled", canceled, "servicing", servicing,
                "approxRatePercent", Math.round(rate * 10) / 10.0
        ));
    }

    private Map<String, Object> categorySales(int topN) {
        List<SpuPageItemDTO> list = fetchSpus(40);
        Map<String, Integer> catSales = new LinkedHashMap<>();
        for (SpuPageItemDTO s : list) {
            String cat = StringUtils.hasText(s.getCategoryName()) ? s.getCategoryName() : "未分类";
            int sale = s.getSales() == null ? 0 : s.getSales();
            catSales.merge(cat, sale, Integer::sum);
        }
        List<Map.Entry<String, Integer>> sorted = catSales.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .collect(Collectors.toList());

        List<String> names = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        List<Map<String, Object>> pieData = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> e : sorted) {
            names.add(e.getKey());
            values.add(e.getValue());
            pieData.add(Map.of("name", e.getKey(), "value", e.getValue()));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", e.getKey());
            row.put("sales", e.getValue());
            rows.add(row);
        }

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of("trigger", "item"));
        option.put("legend", Map.of("orient", "vertical", "left", "left"));
        option.put("series", List.of(Map.of(
                "name", "品类销量",
                "type", "pie",
                "radius", new String[]{"35%", "60%"},
                "data", pieData
        )));

        String top = sorted.isEmpty() ? "无" : sorted.get(0).getKey();
        String narrative = sorted.isEmpty()
                ? "暂无品类销量数据。"
                : String.format("按 PMS 商品销量字段聚合，品类 Top%d 首位「%s」（%d）。抽样 SPU=%d，非全库 SQL。",
                topN, top, sorted.get(0).getValue(), list.size());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("rows", rows);
        metrics.put("sampleSpu", list.size());
        metrics.put("categories", names);
        return result("pie", option, narrative, metrics);
    }

    private Map<String, Object> gmvSnapshot() {
        // 抽样各有效状态首页订单金额（分）
        long paidFen = Math.max(orderQueryTool.samplePaymentAmountFen(1, 30), 0);
        long shippedFen = Math.max(orderQueryTool.samplePaymentAmountFen(2, 30), 0);
        long completeFen = Math.max(orderQueryTool.samplePaymentAmountFen(3, 30), 0);
        long sampleFen = paidFen + shippedFen + completeFen;
        double sampleYuan = sampleFen / 100.0;

        long paidCnt = safe(orderQueryTool.countByStatus(1));
        long shippedCnt = safe(orderQueryTool.countByStatus(2));
        long completeCnt = safe(orderQueryTool.countByStatus(3));
        long activeCnt = paidCnt + shippedCnt + completeCnt;

        // 用抽样均值外推粗估（演示用）
        int sampleOrders = 0;
        sampleOrders += orderQueryTool.sampleOrders(1, 30).size();
        sampleOrders += orderQueryTool.sampleOrders(2, 30).size();
        sampleOrders += orderQueryTool.sampleOrders(3, 30).size();
        double avgOrderYuan = sampleOrders > 0 ? sampleYuan / sampleOrders : 0;
        double estGmvYuan = avgOrderYuan * activeCnt;

        List<String> cats = List.of("待发货抽样", "已发货抽样", "已完成抽样");
        List<Double> vals = List.of(paidFen / 100.0, shippedFen / 100.0, completeFen / 100.0);
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("xAxis", Map.of("type", "category", "data", cats));
        option.put("yAxis", Map.of("type", "value", "name", "元"));
        option.put("series", List.of(Map.of(
                "name", "抽样支付额",
                "type", "bar",
                "data", vals,
                "itemStyle", Map.of("color", "#F56C6C")
        )));

        String narrative = String.format(
                "GMV 抽样快照：首页各状态合计抽样支付额约 ¥%.2f（%d 笔样本）。有效订单约 %d 笔，按样本客单价 ¥%.2f 粗估全量 ≈ ¥%.2f。"
                        + "仅 Feign 分页抽样，禁止任意 SQL。",
                sampleYuan, sampleOrders, activeCnt, avgOrderYuan, estGmvYuan);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("samplePaymentYuan", Math.round(sampleYuan * 100) / 100.0);
        metrics.put("sampleOrderCount", sampleOrders);
        metrics.put("activeOrderCount", activeCnt);
        metrics.put("avgOrderYuan", Math.round(avgOrderYuan * 100) / 100.0);
        metrics.put("estimatedGmvYuan", Math.round(estGmvYuan * 100) / 100.0);
        return result("bar", option, narrative, metrics);
    }

    private Map<String, Object> opsDashboard(int threshold) {
        Map<String, Object> status = orderStatusDist();
        int low = productQueryTool.countLowStock(threshold);
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = new LinkedHashMap<>((Map<String, Object>) status.get("metrics"));
        metrics.put("lowStock", low);

        // 附加 GMV 抽样与销量榜首
        long completeFen = Math.max(orderQueryTool.samplePaymentAmountFen(3, 20), 0);
        metrics.put("completeSampleYuan", completeFen / 100.0);
        List<SpuPageItemDTO> top = fetchSpus(10).stream()
                .sorted(Comparator.comparingInt((SpuPageItemDTO s) -> s.getSales() == null ? 0 : s.getSales()).reversed())
                .limit(1)
                .collect(Collectors.toList());
        if (!top.isEmpty()) {
            metrics.put("topSpu", top.get(0).getName());
            metrics.put("topSales", top.get(0).getSales());
        }

        List<String> cats = List.of("待付款", "待发货", "已发货", "已完成", "已取消", "售后中", "低库存SPU");
        List<Object> vals = List.of(
                metrics.get("unpaid"), metrics.get("paid"), metrics.get("shipped"),
                metrics.get("complete"), metrics.get("canceled"), metrics.get("servicing"),
                Math.max(low, 0)
        );
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("xAxis", Map.of("type", "category", "data", cats, "axisLabel", Map.of("rotate", 25)));
        option.put("yAxis", Map.of("type", "value"));
        option.put("series", List.of(Map.of("type", "bar", "data", vals, "itemStyle", Map.of("color", "#67C23A"))));

        String extra = "";
        if (metrics.get("topSpu") != null) {
            extra = " 热销榜首「" + metrics.get("topSpu") + "」销量 " + metrics.get("topSales") + "。";
        }
        if (metrics.get("completeSampleYuan") != null) {
            extra += " 已完成订单抽样支付额约 ¥" + metrics.get("completeSampleYuan") + "。";
        }
        String narrative = status.get("narrative") + " 低库存 SPU 抽样：" + (low >= 0 ? low : "服务不可用") + "。" + extra;
        return result("bar", option, narrative, metrics);
    }

    private List<SpuPageItemDTO> fetchSpus(int size) {
        try {
            PageResult<SpuPageItemDTO> page = productAdminFeignClient.listPagedSpu(1, size, null);
            if (page != null && page.getData() != null && page.getData().getList() != null) {
                return new ArrayList<>(page.getData().getList());
            }
        } catch (Exception e) {
            log.warn("fetchSpus failed: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private static int sumStock(SpuPageItemDTO item) {
        if (item == null || item.getSkuList() == null || item.getSkuList().isEmpty()) {
            return -1;
        }
        int sum = 0;
        boolean any = false;
        for (SpuPageItemDTO.SkuItem sku : item.getSkuList()) {
            if (sku.getStock() != null) {
                sum += sku.getStock();
                any = true;
            }
        }
        return any ? sum : -1;
    }

    private static long safe(long v) {
        return v < 0 ? 0 : v;
    }

    private static int intParam(InsightPlan plan, String key, int def) {
        if (plan.getParams() == null || plan.getParams().get(key) == null) {
            return def;
        }
        Object v = plan.getParams().get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static Map<String, Object> result(String chartType, Map<String, Object> option,
                                              String narrative, Map<String, Object> metrics) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("chartType", chartType);
        r.put("option", option);
        r.put("narrative", narrative);
        r.put("metrics", metrics);
        return r;
    }
}
