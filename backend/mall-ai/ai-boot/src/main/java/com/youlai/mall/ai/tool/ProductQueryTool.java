package com.youlai.mall.ai.tool;

import com.youlai.common.result.PageResult;
import com.youlai.mall.ai.client.ProductAdminFeignClient;
import com.youlai.mall.ai.client.dto.SpuPageItemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品/库存查询 Tool：只读 PMS 管理端分页。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductQueryTool {

    private final ProductAdminFeignClient productAdminFeignClient;

    public ToolResult execute(String message) {
        String keywords = resolveKeywords(message);
        try {
            PageResult<SpuPageItemDTO> page = productAdminFeignClient.listPagedSpu(1, 5, keywords);
            List<SpuPageItemDTO> list = page != null && page.getData() != null ? page.getData().getList() : null;
            long total = page != null && page.getData() != null ? page.getData().getTotal() : 0;
            if (list == null || list.isEmpty()) {
                String kw = StringUtils.hasText(keywords) ? "「" + keywords + "」" : "";
                return ToolResult.of("query_product", "queryProducts",
                        "未找到" + kw + "相关商品。可换关键词再试。", new ArrayList<>());
            }
            List<Map<String, Object>> cards = new ArrayList<>();
            int lowStockCount = 0;
            for (SpuPageItemDTO item : list) {
                int stock = sumStock(item);
                if (stock >= 0 && stock < 10) {
                    lowStockCount++;
                }
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("type", "product");
                card.put("spuId", item.getId());
                card.put("name", item.getName());
                card.put("price", formatAmount(item.getPrice()));
                card.put("stock", stock >= 0 ? stock : null);
                card.put("sales", item.getSales());
                card.put("categoryName", item.getCategoryName());
                card.put("brandName", item.getBrandName());
                card.put("status", item.getStatus() != null && item.getStatus() == 1 ? "上架" : "下架/其它");
                cards.add(card);
            }
            String kw = StringUtils.hasText(keywords) ? "「" + keywords + "」" : "";
            String reply = String.format("为你找到 %d 个%s商品（展示 %d 个）%s。",
                    total, kw, cards.size(),
                    lowStockCount > 0 ? "，其中 " + lowStockCount + " 个库存偏低（<10）" : "");
            return ToolResult.of("query_product", "queryProducts", reply, cards);
        } catch (Exception ex) {
            log.warn("queryProducts failed: {}", ex.getMessage());
            return ToolResult.error("query_product", "queryProducts",
                    "商品服务暂时不可用：" + safeMsg(ex) + "。请确认 mall-pms 已启动。");
        }
    }

    /** 库存偏低 SKU 粗估（取首页商品中库存 < threshold 的 SPU 数）。 */
    public int countLowStock(int threshold) {
        try {
            PageResult<SpuPageItemDTO> page = productAdminFeignClient.listPagedSpu(1, 20, null);
            List<SpuPageItemDTO> list = page != null && page.getData() != null ? page.getData().getList() : null;
            if (list == null) {
                return -1;
            }
            int n = 0;
            for (SpuPageItemDTO item : list) {
                int stock = sumStock(item);
                if (stock >= 0 && stock < threshold) {
                    n++;
                }
            }
            return n;
        } catch (Exception ex) {
            log.warn("countLowStock failed: {}", ex.getMessage());
            return -1;
        }
    }

    private String resolveKeywords(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String cleaned = message
                .replaceAll("(?i)查一下|查询|帮我|商品|库存|还有吗|还有|怎么样|情况|sku|spu|库存吗|有没有", "")
                .trim();
        return StringUtils.hasText(cleaned) ? cleaned : null;
    }

    private static int sumStock(SpuPageItemDTO item) {
        if (item.getSkuList() == null || item.getSkuList().isEmpty()) {
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
