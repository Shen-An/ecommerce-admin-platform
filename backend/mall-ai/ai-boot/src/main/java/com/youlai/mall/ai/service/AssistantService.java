package com.youlai.mall.ai.service;

import com.youlai.mall.ai.model.form.AssistantChatForm;
import com.youlai.mall.ai.model.vo.AssistantChatVO;

public interface AssistantService {

    AssistantChatVO chat(AssistantChatForm form);
}
