package com.youlai.mall.ai.service.impl;

import com.youlai.mall.ai.model.form.AssistantChatForm;
import com.youlai.mall.ai.model.vo.AssistantChatVO;
import com.youlai.mall.ai.service.AssistantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MVP 运营助手：先用规则路由保证可演示；后续接入 Spring AI Alibaba + Tool Calling。
 */
@Slf4j
@Service
public class AssistantServiceImpl implements AssistantService {

    private static final AtomicLong SESSION_SEQ = new AtomicLong(1000);

    @Value("${ai.llm.mock-enabled:true}")
    private boolean mockEnabled;

    @Override
    public AssistantChatVO chat(AssistantChatForm form) {
        Long sessionId = form.getSessionId() != null ? form.getSessionId() : SESSION_SEQ.incrementAndGet();
        String message = form.getMessage() == null ? "" : form.getMessage().trim();
        String intent = detectIntent(message);

        List<Map<String, Object>> cards = new ArrayList<>();
        String reply;

        switch (intent) {
            case "query_order" -> {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("type", "order");
                card.put("orderSn", "DEMO202607210001");
                card.put("status", "待发货");
                card.put("amount", "199.00");
                card.put("hint", "真实环境将通过 OMS Feign 查询");
                cards.add(card);
                reply = "已为你查询到相关订单（演示数据）。可继续问「未发货订单」或提供订单号。";
            }
            case "query_product" -> {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("type", "product");
                card.put("name", "演示商品-智能音箱");
                card.put("price", "299.00");
                card.put("stock", 128);
                card.put("hint", "真实环境将通过 PMS Feign 查询");
                cards.add(card);
                reply = "已匹配商品信息（演示数据）。后续将接入真实商品与库存接口。";
            }
            case "ops_summary" -> reply = "【运营摘要-演示】今日订单 126 笔，待发货 18 笔，退款中 3 笔，库存预警 SKU 5 个。接入模型后将基于真实 OMS/PMS 汇总。";
            default -> reply = "我是电商运营智能助手。你可以问我：未发货订单、商品库存、今日运营摘要。当前为骨架/Mock 模式，后续接入 DashScope + Tool。";
        }

        log.info("assistant chat sessionId={}, intent={}, mock={}", sessionId, intent, mockEnabled);
        return AssistantChatVO.builder()
                .sessionId(sessionId)
                .reply(reply)
                .intent(intent)
                .cards(cards)
                .mock(mockEnabled)
                .build();
    }

    private String detectIntent(String message) {
        if (!StringUtils.hasText(message)) {
            return "chitchat";
        }
        String m = message.toLowerCase(Locale.ROOT);
        if (m.contains("订单") || m.contains("发货") || m.contains("order")) {
            return "query_order";
        }
        if (m.contains("商品") || m.contains("库存") || m.contains("sku") || m.contains("product")) {
            return "query_product";
        }
        if (m.contains("摘要") || m.contains("日报") || m.contains("运营") || m.contains("统计")) {
            return "ops_summary";
        }
        return "chitchat";
    }
}
