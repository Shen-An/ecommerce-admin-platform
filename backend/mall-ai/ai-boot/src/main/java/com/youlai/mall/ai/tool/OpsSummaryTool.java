package com.youlai.mall.ai.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运营日摘要：聚合订单状态计数 + 库存预警粗估。
 */
@Component
@RequiredArgsConstructor
public class OpsSummaryTool {

    private final OrderQueryTool orderQueryTool;
    private final ProductQueryTool productQueryTool;

    public ToolResult execute(String message) {
        long unpaid = orderQueryTool.countByStatus(0);
        long paid = orderQueryTool.countByStatus(1);
        long shipped = orderQueryTool.countByStatus(2);
        long complete = orderQueryTool.countByStatus(3);
        long canceled = orderQueryTool.countByStatus(4);
        long servicing = orderQueryTool.countByStatus(5);
        int lowStock = productQueryTool.countLowStock(10);

        boolean anyFail = unpaid < 0 || paid < 0;
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        StringBuilder sb = new StringBuilder();
        sb.append("【运营摘要 ").append(date).append("】\n");
        if (anyFail) {
            sb.append("部分订单统计失败（OMS 可能未就绪）。");
            return ToolResult.error("ops_summary", "summarizeOpsDaily", sb.toString());
        }
        long knownTotal = Math.max(unpaid, 0) + Math.max(paid, 0) + Math.max(shipped, 0)
                + Math.max(complete, 0) + Math.max(canceled, 0) + Math.max(servicing, 0);
        sb.append("订单总量（分状态合计）约 ").append(knownTotal).append(" 笔：\n");
        sb.append("- 待付款 ").append(unpaid).append("\n");
        sb.append("- 待发货 ").append(paid).append("\n");
        sb.append("- 已发货 ").append(shipped).append("\n");
        sb.append("- 已完成 ").append(complete).append("\n");
        sb.append("- 已取消 ").append(canceled).append("\n");
        sb.append("- 售后中 ").append(servicing).append("\n");
        if (lowStock >= 0) {
            sb.append("库存预警（首页抽样库存 <10 的 SPU）：").append(lowStock).append(" 个。");
        } else {
            sb.append("库存预警：商品服务暂不可用。");
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "ops_summary");
        card.put("date", date);
        card.put("unpaid", unpaid);
        card.put("paid", paid);
        card.put("shipped", shipped);
        card.put("complete", complete);
        card.put("canceled", canceled);
        card.put("servicing", servicing);
        card.put("lowStock", lowStock);
        ArrayList<Map<String, Object>> cards = new ArrayList<>();
        cards.add(card);
        return ToolResult.of("ops_summary", "summarizeOpsDaily", sb.toString(), cards);
    }
}
