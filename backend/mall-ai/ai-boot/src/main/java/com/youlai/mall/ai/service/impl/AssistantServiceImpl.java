package com.youlai.mall.ai.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.common.security.util.SecurityUtils;
import com.youlai.mall.ai.llm.ChatLlmClient;
import com.youlai.mall.ai.mapper.AiMessageMapper;
import com.youlai.mall.ai.mapper.AiSessionMapper;
import com.youlai.mall.ai.model.entity.AiMessage;
import com.youlai.mall.ai.model.entity.AiModelConfig;
import com.youlai.mall.ai.model.entity.AiSession;
import com.youlai.mall.ai.model.form.AssistantChatForm;
import com.youlai.mall.ai.model.vo.AiMessageVO;
import com.youlai.mall.ai.model.vo.AiSessionVO;
import com.youlai.mall.ai.model.vo.AssistantChatVO;
import com.youlai.mall.ai.service.AiModelConfigService;
import com.youlai.mall.ai.service.AssistantService;
import com.youlai.mall.ai.tool.OpsSummaryTool;
import com.youlai.mall.ai.tool.OrderQueryTool;
import com.youlai.mall.ai.tool.ProductQueryTool;
import com.youlai.mall.ai.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase1 运营助手：会话落库 + 规则意图 + 只读 Tool（OMS/PMS）+ 可选 LLM 润色。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantServiceImpl implements AssistantService {

    public static final String SCENE_ASSISTANT = "assistant";

    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final AiModelConfigService modelConfigService;
    private final OrderQueryTool orderQueryTool;
    private final ProductQueryTool productQueryTool;
    private final OpsSummaryTool opsSummaryTool;
    private final ChatLlmClient chatLlmClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssistantChatVO chat(AssistantChatForm form) {
        String message = form.getMessage() == null ? "" : form.getMessage().trim();
        Long userId = SecurityUtils.getUserId();
        AiSession session = resolveSession(form.getSessionId(), userId, message);

        saveMessage(session.getId(), "user", message, null, null);

        String intent = detectIntent(message);
        ToolResult toolResult = dispatchTool(intent, message);

        AiModelConfig runtime = modelConfigService.getRuntimeConfig(AiModelConfigServiceImpl.DEFAULT_KEY);
        boolean mock = runtime == null || runtime.getMockEnabled() == null || runtime.getMockEnabled() == 1
                || !chatLlmClient.isAvailable(runtime);

        String reply = toolResult.getReply();
        if (!mock && StringUtils.hasText(reply)) {
            String polished = chatLlmClient.polishReply(runtime, message, reply, toolResult.getIntent());
            if (StringUtils.hasText(polished)) {
                reply = polished;
            }
        }

        List<Map<String, Object>> cards = toolResult.getCards() != null ? toolResult.getCards() : new ArrayList<>();
        Map<String, Object> toolMeta = new LinkedHashMap<>();
        toolMeta.put("intent", toolResult.getIntent());
        toolMeta.put("tool", toolResult.getToolName());
        if (StringUtils.hasText(toolResult.getToolError())) {
            toolMeta.put("error", toolResult.getToolError());
        }
        toolMeta.put("cards", cards);

        saveMessage(session.getId(), "assistant", reply,
                JSONUtil.toJsonStr(toolMeta),
                cards.isEmpty() ? null : JSONUtil.toJsonStr(cards));

        touchSession(session, message);

        log.info("assistant chat sessionId={}, intent={}, tool={}, mock={}, userId={}",
                session.getId(), toolResult.getIntent(), toolResult.getToolName(), mock, userId);

        return AssistantChatVO.builder()
                .sessionId(session.getId())
                .reply(reply)
                .intent(toolResult.getIntent())
                .cards(cards)
                .mock(mock)
                .build();
    }

    @Override
    public List<AiSessionVO> listSessions() {
        Long userId = SecurityUtils.getUserId();
        LambdaQueryWrapper<AiSession> qw = new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getScene, SCENE_ASSISTANT)
                .eq(AiSession::getStatus, 1)
                .orderByDesc(AiSession::getUpdatedAt)
                .last("limit 50");
        if (userId != null) {
            qw.eq(AiSession::getUserId, userId);
        }
        return sessionMapper.selectList(qw).stream().map(this::toSessionVo).collect(Collectors.toList());
    }

    @Override
    public List<AiMessageVO> listMessages(Long sessionId) {
        assertSessionOwner(sessionId);
        List<AiMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getId)
                .last("limit 200"));
        List<AiMessageVO> result = new ArrayList<>();
        for (AiMessage m : messages) {
            List<Map<String, Object>> cards = parseCards(m.getRefsJson());
            result.add(AiMessageVO.builder()
                    .id(m.getId())
                    .role(m.getRole())
                    .content(m.getContent())
                    .cards(cards)
                    .createdAt(m.getCreatedAt())
                    .build());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        AiSession session = assertSessionOwner(sessionId);
        session.setStatus(0);
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private ToolResult dispatchTool(String intent, String message) {
        return switch (intent) {
            case "query_order" -> orderQueryTool.execute(message);
            case "query_product" -> productQueryTool.execute(message);
            case "ops_summary" -> opsSummaryTool.execute(message);
            default -> ToolResult.of("chitchat", null,
                    "我是电商运营智能助手。你可以问我：\n"
                            + "1. 待发货/待付款订单\n"
                            + "2. 商品库存（如：智能音箱还有库存吗）\n"
                            + "3. 今天运营情况怎么样\n"
                            + (mockHint()),
                    new ArrayList<>());
        };
    }

    private String mockHint() {
        AiModelConfig runtime = modelConfigService.getRuntimeConfig(AiModelConfigServiceImpl.DEFAULT_KEY);
        if (runtime != null && runtime.getMockEnabled() != null && runtime.getMockEnabled() == 0
                && StringUtils.hasText(runtime.getChatApiKey())) {
            return "当前已配置模型 " + runtime.getChatModel() + "，工具结果可由模型润色。";
        }
        return "当前为规则/Tool 模式（Mock 润色关闭或未配置 Key），卡片数据来自真实 OMS/PMS。";
    }

    private String detectIntent(String message) {
        if (!StringUtils.hasText(message)) {
            return "chitchat";
        }
        String m = message.toLowerCase(Locale.ROOT);
        if (m.contains("摘要") || m.contains("日报") || m.contains("运营") || m.contains("统计")
                || m.contains("情况怎么样") || m.contains("今天怎么样") || m.contains("gmv")) {
            return "ops_summary";
        }
        if (m.contains("订单") || m.contains("发货") || m.contains("order") || m.contains("退款")
                || m.contains("付款") || m.contains("支付")) {
            return "query_order";
        }
        if (m.contains("商品") || m.contains("库存") || m.contains("sku") || m.contains("product")
                || m.contains("音箱") || m.contains("还有吗")) {
            return "query_product";
        }
        return "chitchat";
    }

    private AiSession resolveSession(Long sessionId, Long userId, String firstMessage) {
        if (sessionId != null) {
            AiSession existing = sessionMapper.selectById(sessionId);
            if (existing != null && SCENE_ASSISTANT.equals(existing.getScene())) {
                if (userId == null || existing.getUserId() == null || userId.equals(existing.getUserId())) {
                    return existing;
                }
            }
        }
        AiSession session = new AiSession();
        session.setUserId(userId);
        session.setScene(SCENE_ASSISTANT);
        session.setTitle(buildTitle(firstMessage));
        session.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);
        return session;
    }

    private void touchSession(AiSession session, String message) {
        if (!StringUtils.hasText(session.getTitle()) || "新对话".equals(session.getTitle())) {
            session.setTitle(buildTitle(message));
        }
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private void saveMessage(Long sessionId, String role, String content, String toolJson, String refsJson) {
        AiMessage msg = new AiMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolCallsJson(toolJson);
        msg.setRefsJson(refsJson);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    private AiSession assertSessionOwner(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("会话ID不能为空");
        }
        AiSession session = sessionMapper.selectById(sessionId);
        if (session == null || !SCENE_ASSISTANT.equals(session.getScene())) {
            throw new IllegalArgumentException("会话不存在");
        }
        Long userId = SecurityUtils.getUserId();
        if (userId != null && session.getUserId() != null && !userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        return session;
    }

    private AiSessionVO toSessionVo(AiSession s) {
        return AiSessionVO.builder()
                .id(s.getId())
                .scene(s.getScene())
                .title(s.getTitle())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private static String buildTitle(String message) {
        if (!StringUtils.hasText(message)) {
            return "新对话";
        }
        String t = message.trim().replaceAll("\\s+", " ");
        return t.length() > 24 ? t.substring(0, 24) + "…" : t;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseCards(String refsJson) {
        if (!StringUtils.hasText(refsJson)) {
            return null;
        }
        try {
            List<?> raw = JSONUtil.parseArray(refsJson);
            List<Map<String, Object>> cards = new ArrayList<>();
            for (Object o : raw) {
                if (o instanceof Map<?, ?> map) {
                    cards.add((Map<String, Object>) map);
                }
            }
            return cards;
        } catch (Exception ignored) {
            return null;
        }
    }
}
