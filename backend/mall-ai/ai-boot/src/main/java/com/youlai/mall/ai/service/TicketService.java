package com.youlai.mall.ai.service;

import com.youlai.mall.ai.model.form.TicketActionForm;
import com.youlai.mall.ai.model.form.TicketChatForm;
import com.youlai.mall.ai.model.vo.AiMessageVO;
import com.youlai.mall.ai.model.vo.AiSessionVO;
import com.youlai.mall.ai.model.vo.TicketChatVO;
import com.youlai.mall.ai.model.vo.TicketVO;

import java.util.List;

public interface TicketService {

    TicketChatVO chat(TicketChatForm form);

    List<TicketVO> listTickets(String status);

    TicketVO getTicket(Long id);

    TicketVO escalate(Long id, TicketActionForm form);

    TicketVO close(Long id, TicketActionForm form);

    List<AiSessionVO> listSessions();

    List<AiMessageVO> listMessages(Long sessionId);
}
