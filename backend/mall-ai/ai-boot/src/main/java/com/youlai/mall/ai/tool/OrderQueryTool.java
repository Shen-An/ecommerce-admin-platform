package com.youlai.mall.ai.tool;

import com.youlai.common.result.PageResult;
import com.youlai.mall.ai.client.OrderAdminFeignClient;
import com.youlai.mall.ai.client.dto.OrderPageItemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 订单查询 Tool：只读 OMS 管理端分页。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderQueryTool {

    private static final Pattern ORDER_SN = Pattern.compile("(20\\d{12,}|[A-Z]{2,}\\d{10,}|\\d{16,})");

    private final OrderAdminFeignClient orderAdminFeignClient;

    public ToolResult execute(String message) {
        Integer status = resolveStatus(message);
        String keywords = resolveKeywords(message);
        int pageSize = 5;
        try {
            PageResult<OrderPageItemDTO> page = orderAdminFeignClient.getOrderPage(1, pageSize, keywords, status);
            List<OrderPageItemDTO> list = page != null && page.getData() != null ? page.getData().getList() : null;
            long total = page != null && page.getData() != null ? page.getData().getTotal() : 0;
            if (list == null || list.isEmpty()) {
                String filter = describeFilter(status, keywords);
                return ToolResult.of("query_order", "queryOrders",
                        "未查到" + filter + "订单。可换关键词或状态再试。", new ArrayList<>());
            }
            List<Map<String, Object>> cards = new ArrayList<>();
            for (OrderPageItemDTO item : list) {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("type", "order");
                card.put("orderSn", item.getOrderSn());
                card.put("status", StringUtils.hasText(item.getStatusLabel())
                        ? item.getStatusLabel()
                        : statusLabel(item.getStatus()));
                card.put("amount", formatAmount(item.getPaymentAmount()));
                card.put("totalQuantity", item.getTotalQuantity());
                if (item.getOrderItems() != null && !item.getOrderItems().isEmpty()) {
                    card.put("skuName", item.getOrderItems().get(0).getSkuName());
                }
                cards.add(card);
            }
            String filter = describeFilter(status, keywords);
            String reply = String.format("为你找到 %d 笔%s订单（本次展示 %d 笔）：",
                    total, filter, cards.size());
            return ToolResult.of("query_order", "queryOrders", reply, cards);
        } catch (Exception ex) {
            log.warn("queryOrders failed: {}", ex.getMessage());
            return ToolResult.error("query_order", "queryOrders",
                    "订单服务暂时不可用：" + safeMsg(ex) + "。请确认 mall-oms 已启动且当前账号有权限。");
        }
    }

    public long countByStatus(Integer status) {
        try {
            PageResult<OrderPageItemDTO> page = orderAdminFeignClient.getOrderPage(1, 1, null, status);
            if (page != null && page.getData() != null) {
                return page.getData().getTotal();
            }
        } catch (Exception ex) {
            log.warn("count orders status={} failed: {}", status, ex.getMessage());
        }
        return -1;
    }

    private Integer resolveStatus(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String m = message.toLowerCase(Locale.ROOT);
        if (m.contains("待付款") || m.contains("未付款") || m.contains("未支付")) {
            return 0;
        }
        if (m.contains("待发货") || m.contains("未发货") || m.contains("已付款")) {
            return 1;
        }
        if (m.contains("已发货") || m.contains("物流")) {
            return 2;
        }
        if (m.contains("已完成") || m.contains("完成")) {
            return 3;
        }
        if (m.contains("取消") || m.contains("关闭")) {
            return 4;
        }
        if (m.contains("售后") || m.contains("退款")) {
            return 5;
        }
        return null;
    }

    private String resolveKeywords(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        Matcher matcher = ORDER_SN.matcher(message.toUpperCase(Locale.ROOT));
        if (matcher.find()) {
            return matcher.group(1);
        }
        // 去掉意图词后的残余作为模糊关键词（避免整句塞进 keywords）
        String cleaned = message
                .replaceAll("(?i)查一下|查询|帮我|订单|待发货|未发货|已发货|待付款|未付款|已完成|取消|售后|退款|情况|怎么样|有哪些|列表", "")
                .trim();
        return StringUtils.hasText(cleaned) ? cleaned : null;
    }

    private static String describeFilter(Integer status, String keywords) {
        StringBuilder sb = new StringBuilder();
        if (status != null) {
            sb.append(statusLabel(status));
        }
        if (StringUtils.hasText(keywords)) {
            if (sb.length() > 0) {
                sb.append("、关键词「").append(keywords).append("」");
            } else {
                sb.append("关键词「").append(keywords).append("」");
            }
        }
        return sb.length() == 0 ? "相关" : sb.toString();
    }

    private static String statusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待付款";
            case 1 -> "待发货";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            case 5 -> "售后中";
            default -> "状态" + status;
        };
    }

    private static String formatAmount(Long fen) {
        if (fen == null) {
            return "-";
        }
        return BigDecimal.valueOf(fen)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static String safeMsg(Exception ex) {
        String msg = ex.getMessage();
        if (!StringUtils.hasText(msg)) {
            return ex.getClass().getSimpleName();
        }
        return msg.length() > 120 ? msg.substring(0, 120) + "..." : msg;
    }
}
