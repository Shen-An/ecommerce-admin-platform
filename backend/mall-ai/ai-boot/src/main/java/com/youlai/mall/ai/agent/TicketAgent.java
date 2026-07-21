package com.youlai.mall.ai.agent;

import com.youlai.mall.ai.mapper.AiTicketLogMapper;
import com.youlai.mall.ai.mapper.AiTicketMapper;
import com.youlai.mall.ai.model.entity.AiTicket;
import com.youlai.mall.ai.model.entity.AiTicketLog;
import com.youlai.mall.ai.model.vo.KnowledgeQueryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * TicketAgent：写入 ai_ticket + 流转日志。
 */
@Component
@RequiredArgsConstructor
public class TicketAgent {

    private final AiTicketMapper ticketMapper;
    private final AiTicketLogMapper ticketLogMapper;

    public AiTicket create(Long sessionId,
                           IntentResult intent,
                           String userMessage,
                           KnowledgeQueryVO policy,
                           EscalationDecision decision) {
        AiTicket ticket = new AiTicket();
        ticket.setSessionId(sessionId);
        ticket.setOrderSn(intent.getOrderSn());
        ticket.setIntent(intent.getIntent());
        ticket.setPriority(intent.getPriority() != null ? intent.getPriority() : "medium");
        ticket.setStatus(decision.isEscalate() ? "escalated" : "open");
        ticket.setSummary(buildSummary(userMessage, intent, policy));
        ticket.setAssignee(decision.isEscalate() ? "human_queue" : "ai_bot");
        LocalDateTime now = LocalDateTime.now();
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticketMapper.insert(ticket);

        appendLog(ticket.getId(), "created",
                "建单 intent=" + intent.getIntent()
                        + " confidence=" + String.format("%.2f", intent.getConfidence())
                        + " status=" + ticket.getStatus(),
                "system");

        if (policy != null && StringUtils.hasText(policy.getAnswer())) {
            String snippet = policy.getAnswer().length() > 200
                    ? policy.getAnswer().substring(0, 200) + "…"
                    : policy.getAnswer();
            appendLog(ticket.getId(), "policy_attached",
                    "source=" + policy.getSource() + " | " + snippet,
                    "system");
        }

        if (decision.isEscalate()) {
            String reasons = decision.getReasons() == null ? "" : String.join("; ", decision.getReasons());
            appendLog(ticket.getId(), "escalated", reasons, "system");
        } else {
            appendLog(ticket.getId(), "auto_reply", "AI 自动回复（未升级）", "system");
        }

        return ticket;
    }

    public void escalate(Long ticketId, String operator, String reason) {
        AiTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        ticket.setStatus("escalated");
        ticket.setAssignee("human_queue");
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        appendLog(ticketId, "human_takeover",
                StringUtils.hasText(reason) ? reason : "人工接管",
                StringUtils.hasText(operator) ? operator : "admin");
    }

    public void close(Long ticketId, String operator, String reason) {
        AiTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        ticket.setStatus("closed");
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        appendLog(ticketId, "closed",
                StringUtils.hasText(reason) ? reason : "关闭工单",
                StringUtils.hasText(operator) ? operator : "admin");
    }

    public void appendLog(Long ticketId, String action, String detail, String operator) {
        AiTicketLog log = new AiTicketLog();
        log.setTicketId(ticketId);
        log.setAction(action);
        log.setDetail(detail);
        log.setOperator(operator != null ? operator : "system");
        log.setCreatedAt(LocalDateTime.now());
        ticketLogMapper.insert(log);
    }

    private static String buildSummary(String userMessage, IntentResult intent, KnowledgeQueryVO policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(intent.getIntentLabel() != null ? intent.getIntentLabel() : intent.getIntent()).append("] ");
        if (StringUtils.hasText(intent.getOrderSn())) {
            sb.append("订单 ").append(intent.getOrderSn()).append(" · ");
        }
        String msg = userMessage == null ? "" : userMessage.trim().replaceAll("\\s+", " ");
        if (msg.length() > 120) {
            msg = msg.substring(0, 120) + "…";
        }
        sb.append(msg);
        if (policy != null && StringUtils.hasText(policy.getSource())) {
            sb.append(" | 政策源=").append(policy.getSource());
        }
        String s = sb.toString();
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
