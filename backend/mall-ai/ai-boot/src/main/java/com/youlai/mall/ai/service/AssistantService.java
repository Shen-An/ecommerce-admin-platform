package com.youlai.mall.ai.service;

import com.youlai.mall.ai.model.form.AssistantChatForm;
import com.youlai.mall.ai.model.vo.AiMessageVO;
import com.youlai.mall.ai.model.vo.AiSessionVO;
import com.youlai.mall.ai.model.vo.AssistantChatVO;

import java.util.List;

public interface AssistantService {

    AssistantChatVO chat(AssistantChatForm form);

    List<AiSessionVO> listSessions();

    List<AiMessageVO> listMessages(Long sessionId);

    void deleteSession(Long sessionId);
}
