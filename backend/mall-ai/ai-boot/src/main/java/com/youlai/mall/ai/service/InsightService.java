package com.youlai.mall.ai.service;

import com.youlai.mall.ai.model.form.InsightQueryForm;
import com.youlai.mall.ai.model.vo.InsightHistoryVO;
import com.youlai.mall.ai.model.vo.InsightQueryVO;

import java.util.List;
import java.util.Map;

public interface InsightService {

    InsightQueryVO query(InsightQueryForm form);

    List<InsightHistoryVO> history(int limit);

    List<Map<String, String>> templates();
}
