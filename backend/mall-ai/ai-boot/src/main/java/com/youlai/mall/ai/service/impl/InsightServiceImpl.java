package com.youlai.mall.ai.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.common.security.util.SecurityUtils;
import com.youlai.mall.ai.insight.InsightPlan;
import com.youlai.mall.ai.insight.InsightQueryExecutor;
import com.youlai.mall.ai.insight.InsightQueryPlanner;
import com.youlai.mall.ai.insight.InsightTemplate;
import com.youlai.mall.ai.mapper.AiInsightQueryMapper;
import com.youlai.mall.ai.model.entity.AiInsightQuery;
import com.youlai.mall.ai.model.form.InsightQueryForm;
import com.youlai.mall.ai.model.vo.InsightHistoryVO;
import com.youlai.mall.ai.model.vo.InsightQueryVO;
import com.youlai.mall.ai.service.InsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase4 数据洞察：白名单模板 + Feign 聚合 + ECharts option，禁止任意 SQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightServiceImpl implements InsightService {

    private static final String SECURITY_NOTE =
            "仅执行白名单查询模板（Feign 只读聚合），不解析、不执行模型生成的 SQL。";

    private final InsightQueryPlanner planner;
    private final InsightQueryExecutor executor;
    private final AiInsightQueryMapper insightQueryMapper;

    @Override
    public InsightQueryVO query(InsightQueryForm form) {
        String question = form.getQuestion() == null ? "" : form.getQuestion().trim();
        InsightPlan plan = planner.plan(question);

        if (StringUtils.hasText(form.getTemplate())) {
            InsightTemplate forced = InsightTemplate.fromCode(form.getTemplate());
            if (forced != null) {
                plan = InsightPlan.builder()
                        .template(forced)
                        .templateCode(forced.getCode())
                        .templateLabel(forced.getLabel())
                        .params(plan.getParams())
                        .reason("用户指定模板 " + forced.getCode())
                        .build();
            }
        }

        Map<String, Object> exec = executor.execute(plan);

        Map<String, Object> planJson = new LinkedHashMap<>();
        planJson.put("template", plan.getTemplateCode());
        planJson.put("params", plan.getParams());
        planJson.put("reason", plan.getReason());

        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("chartType", exec.get("chartType"));
        resultJson.put("option", exec.get("option"));
        resultJson.put("narrative", exec.get("narrative"));
        resultJson.put("metrics", exec.get("metrics"));

        AiInsightQuery row = new AiInsightQuery();
        row.setUserId(SecurityUtils.getUserId());
        row.setQuestion(question);
        row.setPlanJson(JSONUtil.toJsonStr(planJson));
        row.setResultJson(JSONUtil.toJsonStr(resultJson));
        row.setCreatedAt(LocalDateTime.now());
        insightQueryMapper.insert(row);

        log.info("insight query id={}, template={}, userId={}",
                row.getId(), plan.getTemplateCode(), row.getUserId());

        return InsightQueryVO.builder()
                .queryId(row.getId())
                .question(question)
                .templateCode(plan.getTemplateCode())
                .templateLabel(plan.getTemplateLabel())
                .planReason(plan.getReason())
                .params(plan.getParams())
                .chartType(String.valueOf(exec.get("chartType")))
                .option(castMap(exec.get("option")))
                .narrative(String.valueOf(exec.get("narrative")))
                .metrics(castMap(exec.get("metrics")))
                .whitelist(true)
                .securityNote(SECURITY_NOTE)
                .createdAt(row.getCreatedAt())
                .build();
    }

    @Override
    public List<InsightHistoryVO> history(int limit) {
        int lim = Math.min(Math.max(limit, 1), 50);
        Long userId = SecurityUtils.getUserId();
        LambdaQueryWrapper<AiInsightQuery> qw = new LambdaQueryWrapper<AiInsightQuery>()
                .orderByDesc(AiInsightQuery::getId)
                .last("limit " + lim);
        if (userId != null) {
            qw.eq(AiInsightQuery::getUserId, userId);
        }
        return insightQueryMapper.selectList(qw).stream().map(r -> {
            String code = null;
            try {
                if (StringUtils.hasText(r.getPlanJson())) {
                    Map<?, ?> m = JSONUtil.toBean(r.getPlanJson(), Map.class);
                    Object t = m.get("template");
                    code = t != null ? String.valueOf(t) : null;
                }
            } catch (Exception ignored) {
                // ignore
            }
            InsightTemplate tpl = InsightTemplate.fromCode(code);
            return InsightHistoryVO.builder()
                    .id(r.getId())
                    .question(r.getQuestion())
                    .templateCode(code)
                    .templateLabel(tpl != null ? tpl.getLabel() : code)
                    .createdAt(r.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, String>> templates() {
        List<Map<String, String>> list = new ArrayList<>();
        for (InsightTemplate t : InsightTemplate.values()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", t.getCode());
            m.put("label", t.getLabel());
            list.add(m);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }
}
