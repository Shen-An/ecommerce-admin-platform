package com.youlai.mall.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.common.security.util.SecurityUtils;
import com.youlai.mall.ai.mapper.AiKnowledgeDocMapper;
import com.youlai.mall.ai.model.entity.AiKnowledgeDoc;
import com.youlai.mall.ai.model.entity.AiModelConfig;
import com.youlai.mall.ai.model.form.KnowledgeQueryForm;
import com.youlai.mall.ai.model.form.KnowledgeTextForm;
import com.youlai.mall.ai.model.vo.KnowledgeDocVO;
import com.youlai.mall.ai.model.vo.KnowledgeQueryVO;
import com.youlai.mall.ai.rag.LightRagClient;
import com.youlai.mall.ai.service.AiModelConfigService;
import com.youlai.mall.ai.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final AiKnowledgeDocMapper docMapper;
    private final LightRagClient lightRagClient;
    private final AiModelConfigService modelConfigService;

    @Override
    public Map<String, Object> status() {
        AiModelConfig cfg = modelConfigService.getRuntimeConfig(AiModelConfigServiceImpl.DEFAULT_KEY);
        String baseUrl = cfg != null && StringUtils.hasText(cfg.getLightragBaseUrl())
                ? cfg.getLightragBaseUrl() : null;
        boolean up = lightRagClient.health(baseUrl);
        long localDocs = docMapper.selectCount(new LambdaQueryWrapper<>());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lightrag", up ? "UP" : "DOWN");
        data.put("baseUrl", lightRagClient.resolveBaseUrl(baseUrl));
        data.put("localDocCount", localDocs);
        data.put("hint", up
                ? "LightRAG 可用：入库将同步索引；问答优先图+向量检索"
                : "LightRAG 未启动：入库仅写本地库，问答走关键词检索降级");
        return data;
    }

    @Override
    public List<KnowledgeDocVO> listDocs() {
        return docMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDoc>()
                        .orderByDesc(AiKnowledgeDoc::getId)
                        .last("limit 100"))
                .stream()
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocVO ingestText(KnowledgeTextForm form) {
        AiKnowledgeDoc doc = newDocShell(
                form.getTitle(),
                form.getDomain(),
                form.getTitle() + ".md",
                form.getContent());
        pushToLightRag(doc, form.getContent(), null);
        docMapper.insert(doc);
        return toVo(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocVO ingestFile(MultipartFile file, String domain, String title) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
        String content;
        try {
            byte[] bytes = file.getBytes();
            // 文本类直接读；其它类型仅上传 LightRAG，本地 content 记摘要
            if (isTextFile(filename)) {
                content = new String(bytes, StandardCharsets.UTF_8);
            } else {
                content = "[binary file] " + filename + " size=" + bytes.length;
            }
            AiKnowledgeDoc doc = newDocShell(
                    StringUtils.hasText(title) ? title : stripExt(filename),
                    domain,
                    filename,
                    content);
            if (lightRagUp()) {
                try {
                    Map<String, Object> resp = lightRagClient.uploadFile(bytes, filename, lightRagBase());
                    applyTrack(doc, resp);
                    doc.setStatus("indexing");
                } catch (Exception ex) {
                    log.warn("LightRAG upload failed, keep local: {}", ex.getMessage());
                    doc.setStatus("local");
                    doc.setErrorMsg(safe(ex.getMessage()));
                }
            } else {
                doc.setStatus("local");
            }
            docMapper.insert(doc);
            return toVo(doc);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("读取上传文件失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDoc(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("文档ID不能为空");
        }
        docMapper.deleteById(id);
        // LightRAG 远端删除接口版本差异大，Phase2 以本地元数据删除为主
    }

    @Override
    public KnowledgeQueryVO query(KnowledgeQueryForm form) {
        String question = form.getQuestion().trim();
        String mode = StringUtils.hasText(form.getMode()) ? form.getMode() : "mix";

        if (lightRagUp()) {
            Map<String, Object> resp = lightRagClient.query(question, mode, lightRagBase());
            boolean degraded = Boolean.TRUE.equals(resp.get("degraded"));
            if (!degraded) {
                String answer = LightRagClient.extractAnswer(resp);
                List<Map<String, Object>> refs = LightRagClient.extractReferences(resp);
                return KnowledgeQueryVO.builder()
                        .answer(StringUtils.hasText(answer) ? answer : "（模型未返回文本）")
                        .mode(mode)
                        .source("lightrag")
                        .degraded(false)
                        .references(normalizeRefs(refs))
                        .hint("答案来自 LightRAG（mode=" + mode + "）")
                        .build();
            }
        }

        // 本地关键词降级
        KnowledgeQueryVO local = localKeywordAnswer(question);
        if (local != null) {
            return local;
        }
        return KnowledgeQueryVO.builder()
                .answer("未命中知识库内容。请先「灌入演示语料」或上传售后/运营文档；并启动 LightRAG 以启用图+向量检索。")
                .mode(mode)
                .source("degraded")
                .degraded(true)
                .references(List.of())
                .hint("LightRAG DOWN 且本地无匹配段落")
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int seedDemoDocs() {
        List<DemoDoc> demos = demoDocs();
        int n = 0;
        for (DemoDoc d : demos) {
            Long exists = docMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeDoc>()
                    .eq(AiKnowledgeDoc::getTitle, d.title));
            if (exists != null && exists > 0) {
                continue;
            }
            KnowledgeTextForm form = new KnowledgeTextForm();
            form.setTitle(d.title);
            form.setDomain(d.domain);
            form.setContent(d.content);
            ingestText(form);
            n++;
        }
        return n;
    }

    private KnowledgeQueryVO localKeywordAnswer(String question) {
        List<AiKnowledgeDoc> docs = docMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDoc>()
                .isNotNull(AiKnowledgeDoc::getContentText)
                .orderByDesc(AiKnowledgeDoc::getId)
                .last("limit 50"));
        if (docs.isEmpty()) {
            return null;
        }
        List<String> tokens = tokenize(question);
        if (tokens.isEmpty()) {
            tokens = List.of(question);
        }

        record Hit(AiKnowledgeDoc doc, String snippet, int score) {}
        List<Hit> hits = new ArrayList<>();
        for (AiKnowledgeDoc doc : docs) {
            String text = doc.getContentText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            int score = 0;
            for (String t : tokens) {
                if (text.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT))) {
                    score += 2;
                }
                if (doc.getTitle() != null && doc.getTitle().contains(t)) {
                    score += 3;
                }
            }
            if (score <= 0) {
                continue;
            }
            String snippet = bestSnippet(text, tokens);
            hits.add(new Hit(doc, snippet, score));
        }
        if (hits.isEmpty()) {
            return null;
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed());
        List<Hit> top = hits.stream().limit(3).toList();

        StringBuilder answer = new StringBuilder();
        answer.append("【本地知识检索】根据已入库文档整理如下（LightRAG 未启用时的关键词降级）：\n\n");
        List<Map<String, Object>> refs = new ArrayList<>();
        int i = 1;
        for (Hit h : top) {
            answer.append(i).append(". 《").append(h.doc.getTitle()).append("》\n");
            answer.append(h.snippet.trim()).append("\n\n");
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("reference_id", String.valueOf(i));
            ref.put("file_path", h.doc.getFileName() != null ? h.doc.getFileName() : h.doc.getTitle());
            ref.put("title", h.doc.getTitle());
            ref.put("content", List.of(h.snippet));
            ref.put("docId", h.doc.getId());
            refs.add(ref);
            i++;
        }
        return KnowledgeQueryVO.builder()
                .answer(answer.toString().trim())
                .mode("local_keyword")
                .source("local")
                .degraded(true)
                .references(refs)
                .hint("本地 MySQL 关键词匹配；启动 LightRAG 后将升级为图+向量检索")
                .build();
    }

    private void pushToLightRag(AiKnowledgeDoc doc, String content, byte[] fileBytes) {
        if (!lightRagUp()) {
            doc.setStatus("local");
            return;
        }
        try {
            Map<String, Object> resp;
            if (fileBytes != null) {
                resp = lightRagClient.uploadFile(fileBytes, doc.getFileName(), lightRagBase());
            } else {
                resp = lightRagClient.insertText(content, doc.getFileName(), lightRagBase());
            }
            applyTrack(doc, resp);
            doc.setStatus("indexing");
        } catch (Exception ex) {
            log.warn("LightRAG insert failed: {}", ex.getMessage());
            doc.setStatus("local");
            doc.setErrorMsg(safe(ex.getMessage()));
        }
    }

    private void applyTrack(AiKnowledgeDoc doc, Map<String, Object> resp) {
        if (resp == null) {
            return;
        }
        Object track = resp.get("track_id");
        if (track == null) {
            track = resp.get("trackId");
        }
        if (track != null) {
            doc.setLightragDocId(String.valueOf(track));
        }
        Object msg = resp.get("message");
        if (msg != null && !StringUtils.hasText(doc.getErrorMsg())) {
            // 非错误信息可不写
        }
    }

    private AiKnowledgeDoc newDocShell(String title, String domain, String fileName, String content) {
        AiKnowledgeDoc doc = new AiKnowledgeDoc();
        doc.setTitle(title);
        doc.setDomain(StringUtils.hasText(domain) ? domain : "general");
        doc.setFileName(fileName);
        doc.setContentText(content);
        doc.setStatus("pending");
        doc.setCreatedBy(SecurityUtils.getUserId());
        LocalDateTime now = LocalDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        return doc;
    }

    private boolean lightRagUp() {
        return lightRagClient.health(lightRagBase());
    }

    private String lightRagBase() {
        AiModelConfig cfg = modelConfigService.getRuntimeConfig(AiModelConfigServiceImpl.DEFAULT_KEY);
        return cfg != null ? cfg.getLightragBaseUrl() : null;
    }

    private KnowledgeDocVO toVo(AiKnowledgeDoc d) {
        return KnowledgeDocVO.builder()
                .id(d.getId())
                .title(d.getTitle())
                .domain(d.getDomain())
                .fileName(d.getFileName())
                .fileUrl(d.getFileUrl())
                .lightragDocId(d.getLightragDocId())
                .status(d.getStatus())
                .errorMsg(d.getErrorMsg())
                .contentLength(d.getContentText() != null ? d.getContentText().length() : 0)
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    private static List<Map<String, Object>> normalizeRefs(List<Map<String, Object>> refs) {
        if (refs == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : refs) {
            if (r == null) {
                continue;
            }
            out.add(new LinkedHashMap<>(r));
        }
        return out;
    }

    private static List<String> tokenize(String q) {
        String cleaned = q.replaceAll("[\\p{Punct}？?！!，,。；;：:、\\s]+", " ").trim();
        if (!StringUtils.hasText(cleaned)) {
            return List.of();
        }
        // 中文按连续字块 + 英文词
        List<String> tokens = new ArrayList<>();
        for (String part : cleaned.split("\\s+")) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
            // 再拆 2-gram 提升命中
            if (part.length() >= 4 && part.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
                for (int i = 0; i < part.length() - 1; i++) {
                    tokens.add(part.substring(i, i + 2));
                }
            }
        }
        // 业务关键词保底
        for (String k : List.of("退货", "退款", "无理由", "质量", "售后", "SOP", "发货", "库存", "客服", "升级")) {
            if (q.contains(k)) {
                tokens.add(k);
            }
        }
        return tokens.stream().distinct().limit(20).collect(Collectors.toList());
    }

    private static String bestSnippet(String text, List<String> tokens) {
        String[] paras = text.split("\\n\\s*\\n|\\n");
        String best = null;
        int bestScore = -1;
        for (String p : paras) {
            if (!StringUtils.hasText(p) || p.trim().startsWith("#")) {
                // 标题行也可，但优先正文
            }
            int s = 0;
            for (String t : tokens) {
                if (p.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT))) {
                    s++;
                }
            }
            if (s > bestScore) {
                bestScore = s;
                best = p;
            }
        }
        if (best == null) {
            best = text.substring(0, Math.min(200, text.length()));
        }
        best = best.trim();
        return best.length() > 280 ? best.substring(0, 280) + "…" : best;
    }

    private static boolean isTextFile(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".md") || n.endsWith(".txt") || n.endsWith(".csv")
                || n.endsWith(".json") || n.endsWith(".yml") || n.endsWith(".yaml")
                || n.endsWith(".log") || n.endsWith(".html");
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private static String safe(String msg) {
        if (!StringUtils.hasText(msg)) {
            return null;
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    private static List<DemoDoc> demoDocs() {
        return Arrays.asList(
                new DemoDoc("售后服务政策", "售后", """
                        # 售后服务政策（演示文档）

                        ## 7 天无理由退货

                        1. 签收之日起 7 日内，商品完好可申请无理由退货。
                        2. 影响二次销售的商品（已激活、定制、生鲜）不支持无理由退货。
                        3. 退货运费：质量问题由商家承担，无理由退货由买家承担。

                        ## 质量问题退换

                        1. 收到商品 15 日内出现质量问题，可申请换货或退货退款。
                        2. 需提供开箱视频或清晰照片。
                        3. 审核通过后 48 小时内给出处理方案。

                        ## 退款时效

                        1. 退货入库验收通过后，1-3 个工作日原路退回。
                        2. 银行卡到账以发卡行规则为准，通常 1-7 个工作日。

                        ## 客服升级

                        涉及投诉、欺诈、金额超过 1000 元的纠纷，自动升级人工客服处理。
                        """),
                new DemoDoc("运营SOP摘要", "运营", """
                        # 运营 SOP 摘要（演示文档）

                        ## 每日开店检查

                        1. 查看待发货订单，超过 24 小时未发货标红处理。
                        2. 检查库存预警（库存 < 10）并补货。
                        3. 回复前一晚未关闭的客服工单。

                        ## 促销活动

                        1. 活动开始前 24 小时完成价签与库存锁定。
                        2. 活动期间每 2 小时检查转化与退款异常。
                        3. 活动结束后 24 小时内输出复盘：GMV、退款率、热销 SKU。

                        ## 异常订单

                        1. 支付成功未生成履约单：转技术值班。
                        2. 物流停滞超 72 小时：主动联系用户并补偿方案。
                        """)
        );
    }

    private record DemoDoc(String title, String domain, String content) {}
}
