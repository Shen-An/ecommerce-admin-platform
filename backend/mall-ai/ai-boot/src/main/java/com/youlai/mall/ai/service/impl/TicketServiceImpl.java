package com.youlai.mall.ai.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.common.security.util.SecurityUtils;
import com.youlai.mall.ai.agent.AgentStep;
import com.youlai.mall.ai.agent.EscalationDecision;
import com.youlai.mall.ai.agent.EscalationGate;
import com.youlai.mall.ai.agent.IntentAgent;
import com.youlai.mall.ai.agent.IntentResult;
import com.youlai.mall.ai.agent.PolicyRagAgent;
import com.youlai.mall.ai.agent.TicketAgent;
import com.youlai.mall.ai.mapper.AiMessageMapper;
import com.youlai.mall.ai.mapper.AiSessionMapper;
import com.youlai.mall.ai.mapper.AiTicketLogMapper;
import com.youlai.mall.ai.mapper.AiTicketMapper;
import com.youlai.mall.ai.model.entity.AiMessage;
import com.youlai.mall.ai.model.entity.AiSession;
import com.youlai.mall.ai.model.entity.AiTicket;
import com.youlai.mall.ai.model.entity.AiTicketLog;
import com.youlai.mall.ai.model.form.TicketActionForm;
import com.youlai.mall.ai.model.form.TicketChatForm;
import com.youlai.mall.ai.model.vo.AiMessageVO;
import com.youlai.mall.ai.model.vo.AiSessionVO;
import com.youlai.mall.ai.model.vo.KnowledgeQueryVO;
import com.youlai.mall.ai.model.vo.TicketChatVO;
import com.youlai.mall.ai.model.vo.TicketLogVO;
import com.youlai.mall.ai.model.vo.TicketVO;
import com.youlai.mall.ai.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase3 工单多 Agent 流水线：Intent → PolicyRAG → Ticket → EscalationGate。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    public static final String SCENE_TICKET = "ticket";

    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final AiTicketMapper ticketMapper;
    private final AiTicketLogMapper ticketLogMapper;
    private final IntentAgent intentAgent;
    private final PolicyRagAgent policyRagAgent;
    private final EscalationGate escalationGate;
    private final TicketAgent ticketAgent;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketChatVO chat(TicketChatForm form) {
        String message = form.getMessage() == null ? "" : form.getMessage().trim();
        Long userId = SecurityUtils.getUserId();
        AiSession session = resolveSession(form.getSessionId(), userId, message);
        saveMessage(session.getId(), "user", message, null, null);

        List<AgentStep> steps = new ArrayList<>();

        long t0 = System.currentTimeMillis();
        IntentResult intent = intentAgent.classify(message);
        steps.add(AgentStep.builder()
                .name("IntentAgent")
                .status("ok")
                .detail(intent.getIntentLabel() + " conf=" + String.format("%.2f", intent.getConfidence())
                        + (StringUtils.hasText(intent.getOrderSn()) ? " order=" + intent.getOrderSn() : ""))
                .durationMs(System.currentTimeMillis() - t0)
                .build());

        long t1 = System.currentTimeMillis();
        KnowledgeQueryVO policy = policyRagAgent.retrieve(message, intent.getIntent());
        String policySnippet = policy.getAnswer();
        if (policySnippet != null && policySnippet.length() > 280) {
            policySnippet = policySnippet.substring(0, 280) + "…";
        }
        steps.add(AgentStep.builder()
                .name("PolicyRagAgent")
                .status(Boolean.TRUE.equals(policy.getDegraded()) ? "degraded" : "ok")
                .detail("source=" + policy.getSource() + (StringUtils.hasText(policySnippet)
                        ? " | " + policySnippet.replace("\n", " ")
                        : ""))
                .durationMs(System.currentTimeMillis() - t1)
                .build());

        long t2 = System.currentTimeMillis();
        EscalationDecision decision = escalationGate.evaluate(intent, message);
        steps.add(AgentStep.builder()
                .name("EscalationGate")
                .status(decision.isEscalate() ? "escalate" : "pass")
                .detail(decision.isEscalate()
                        ? String.join("; ", decision.getReasons() != null ? decision.getReasons() : List.of())
                        : "无需升级，AI 可自动回复")
                .durationMs(System.currentTimeMillis() - t2)
                .build());

        long t3 = System.currentTimeMillis();
        AiTicket ticket = ticketAgent.create(session.getId(), intent, message, policy, decision);
        steps.add(AgentStep.builder()
                .name("TicketAgent")
                .status("ok")
                .detail("ticketId=" + ticket.getId() + " status=" + ticket.getStatus()
                        + " assignee=" + ticket.getAssignee())
                .durationMs(System.currentTimeMillis() - t3)
                .build());

        String reply = buildReply(intent, policy, decision, ticket);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("ticketId", ticket.getId());
        meta.put("intent", intent.getIntent());
        meta.put("status", ticket.getStatus());
        meta.put("escalated", decision.isEscalate());
        meta.put("steps", steps);
        if (policy.getReferences() != null) {
            meta.put("references", policy.getReferences());
        }

        saveMessage(session.getId(), "assistant", reply,
                JSONUtil.toJsonStr(meta),
                JSONUtil.toJsonStr(steps));
        touchSession(session, message);

        log.info("ticket chat sessionId={}, ticketId={}, intent={}, escalated={}",
                session.getId(), ticket.getId(), intent.getIntent(), decision.isEscalate());

        return TicketChatVO.builder()
                .sessionId(session.getId())
                .ticketId(ticket.getId())
                .reply(reply)
                .intent(intent.getIntent())
                .intentLabel(intent.getIntentLabel())
                .confidence(intent.getConfidence())
                .orderSn(intent.getOrderSn())
                .priority(intent.getPriority())
                .status(ticket.getStatus())
                .escalated(decision.isEscalate())
                .escalateReasons(decision.getReasons())
                .policySource(policy.getSource())
                .policySnippet(policySnippet)
                .steps(steps)
                .references(policy.getReferences())
                .build();
    }

    @Override
    public List<TicketVO> listTickets(String status) {
        LambdaQueryWrapper<AiTicket> qw = new LambdaQueryWrapper<AiTicket>()
                .orderByDesc(AiTicket::getId)
                .last("limit 100");
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            qw.eq(AiTicket::getStatus, status);
        }
        return ticketMapper.selectList(qw).stream()
                .map(t -> toTicketVo(t, false))
                .collect(Collectors.toList());
    }

    @Override
    public TicketVO getTicket(Long id) {
        AiTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        return toTicketVo(ticket, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketVO escalate(Long id, TicketActionForm form) {
        String operator = currentOperator();
        String reason = form != null ? form.getReason() : null;
        ticketAgent.escalate(id, operator, reason);
        return getTicket(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketVO close(Long id, TicketActionForm form) {
        String operator = currentOperator();
        String reason = form != null ? form.getReason() : null;
        ticketAgent.close(id, operator, reason);
        return getTicket(id);
    }

    @Override
    public List<AiSessionVO> listSessions() {
        Long userId = SecurityUtils.getUserId();
        LambdaQueryWrapper<AiSession> qw = new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getScene, SCENE_TICKET)
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
            result.add(AiMessageVO.builder()
                    .id(m.getId())
                    .role(m.getRole())
                    .content(m.getContent())
                    .cards(null)
                    .createdAt(m.getCreatedAt())
                    .build());
        }
        return result;
    }

    private String buildReply(IntentResult intent, KnowledgeQueryVO policy,
                              EscalationDecision decision, AiTicket ticket) {
        StringBuilder sb = new StringBuilder();
        sb.append("已识别意图：**").append(intent.getIntentLabel()).append("**");
        sb.append("（置信度 ").append(String.format("%.0f%%", intent.getConfidence() * 100)).append("）\n");
        if (StringUtils.hasText(intent.getOrderSn())) {
            sb.append("关联订单号：`").append(intent.getOrderSn()).append("`\n");
        } else {
            sb.append("未从话术中解析到订单号，可补充后重试。\n");
        }
        sb.append("\n**政策参考**（").append(policy.getSource() != null ? policy.getSource() : "n/a").append("）：\n");
        if (StringUtils.hasText(policy.getAnswer())) {
            String ans = policy.getAnswer().trim();
            if (ans.length() > 400) {
                ans = ans.substring(0, 400) + "…";
            }
            sb.append(ans).append("\n");
        } else {
            sb.append("暂无命中条款，建议人工核对售后手册。\n");
        }
        sb.append("\n工单已创建：#").append(ticket.getId())
                .append("，状态 **").append(ticket.getStatus()).append("**");
        if (decision.isEscalate()) {
            sb.append("（已升级人工队列）");
            if (decision.getReasons() != null && !decision.getReasons().isEmpty()) {
                sb.append("\n升级原因：").append(String.join("；", decision.getReasons()));
            }
            sb.append("\n请点击「人工接管」确认，或继续补充信息。");
        } else {
            sb.append("，由 AI 助手跟进；如需人工可点「人工接管」。");
        }
        return sb.toString();
    }

    private TicketVO toTicketVo(AiTicket t, boolean withLogs) {
        List<TicketLogVO> logs = null;
        if (withLogs) {
            logs = ticketLogMapper.selectList(new LambdaQueryWrapper<AiTicketLog>()
                            .eq(AiTicketLog::getTicketId, t.getId())
                            .orderByAsc(AiTicketLog::getId))
                    .stream()
                    .map(l -> TicketLogVO.builder()
                            .id(l.getId())
                            .ticketId(l.getTicketId())
                            .action(l.getAction())
                            .detail(l.getDetail())
                            .operator(l.getOperator())
                            .createdAt(l.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());
        }
        return TicketVO.builder()
                .id(t.getId())
                .sessionId(t.getSessionId())
                .orderSn(t.getOrderSn())
                .intent(t.getIntent())
                .priority(t.getPriority())
                .status(t.getStatus())
                .summary(t.getSummary())
                .assignee(t.getAssignee())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .logs(logs)
                .build();
    }

    private AiSession resolveSession(Long sessionId, Long userId, String firstMessage) {
        if (sessionId != null) {
            AiSession existing = sessionMapper.selectById(sessionId);
            if (existing != null && SCENE_TICKET.equals(existing.getScene())) {
                if (userId == null || existing.getUserId() == null || userId.equals(existing.getUserId())) {
                    return existing;
                }
            }
        }
        AiSession session = new AiSession();
        session.setUserId(userId);
        session.setScene(SCENE_TICKET);
        session.setTitle(buildTitle(firstMessage));
        session.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);
        return session;
    }

    private void touchSession(AiSession session, String message) {
        if (!StringUtils.hasText(session.getTitle()) || "新工单会话".equals(session.getTitle())) {
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
        if (session == null || !SCENE_TICKET.equals(session.getScene())) {
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
            return "新工单会话";
        }
        String t = message.trim().replaceAll("\\s+", " ");
        return t.length() > 24 ? t.substring(0, 24) + "…" : t;
    }

    private static String currentOperator() {
        Long userId = SecurityUtils.getUserId();
        return userId != null ? "admin:" + userId : "admin";
    }
}
