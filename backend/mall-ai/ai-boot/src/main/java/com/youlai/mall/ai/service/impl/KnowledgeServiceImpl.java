package com.youlai.mall.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.common.security.util.SecurityUtils;
import com.youlai.mall.ai.llm.EmbeddingClient;
import com.youlai.mall.ai.mapper.AiKnowledgeDocMapper;
import com.youlai.mall.ai.model.entity.AiKnowledgeDoc;
import com.youlai.mall.ai.model.entity.AiModelConfig;
import com.youlai.mall.ai.model.form.KnowledgeQueryForm;
import com.youlai.mall.ai.model.form.KnowledgeTextForm;
import com.youlai.mall.ai.model.vo.KnowledgeDocVO;
import com.youlai.mall.ai.model.vo.KnowledgeQueryVO;
import com.youlai.mall.ai.rag.JavaRagService;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库：主路径为纯 Java RAG（模型配置 Embedding/Chat），关键词降级；LightRAG 可选。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final AiKnowledgeDocMapper docMapper;
    private final JavaRagService javaRagService;
    private final EmbeddingClient embeddingClient;
    private final LightRagClient lightRagClient;
    private final AiModelConfigService modelConfigService;

    @Override
    public Map<String, Object> status() {
        AiModelConfig cfg = runtimeConfig();
        boolean emb = embeddingClient.isAvailable(cfg);
        boolean chat = cfg != null
                && (cfg.getMockEnabled() == null || cfg.getMockEnabled() == 0)
                && StringUtils.hasText(cfg.getChatApiKey());
        long localDocs = docMapper.selectCount(new LambdaQueryWrapper<>());
        long ready = docMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeDoc>()
                .eq(AiKnowledgeDoc::getStatus, "ready"));
        long localOnly = docMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeDoc>()
                .eq(AiKnowledgeDoc::getStatus, "local"));
        long failed = docMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeDoc>()
                .eq(AiKnowledgeDoc::getStatus, "failed"));
        long chunks = javaRagService.countChunks();
        long embedded = javaRagService.countEmbeddedChunks();

        String baseUrl = cfg != null && StringUtils.hasText(cfg.getLightragBaseUrl())
                ? cfg.getLightragBaseUrl() : null;
        boolean lrUp = lightRagClient.health(baseUrl);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("engine", "java_rag");
        data.put("embedding", emb ? "READY" : "NOT_CONFIGURED");
        data.put("chat", chat ? "READY" : "OPTIONAL");
        data.put("embeddingModel", cfg != null ? cfg.getEmbeddingModel() : null);
        data.put("chunkCount", chunks);
        data.put("embeddedChunkCount", embedded);
        data.put("localDocCount", localDocs);
        data.put("readyCount", ready);
        data.put("localOnlyCount", localOnly);
        data.put("failedCount", failed);
        data.put("lightrag", lrUp ? "UP" : "DOWN");
        data.put("baseUrl", lightRagClient.resolveBaseUrl(baseUrl));
        if (emb) {
            data.put("hint", "Java RAG 可用：入库自动分块+向量（Key 来自「模型配置」）。"
                    + "问答：向量检索" + (chat ? "+LLM 生成" : "（可配 Chat Key 生成答案）")
                    + "；无向量时降级关键词。");
        } else {
            data.put("hint", "请在「AI中心 → 模型配置」填写 Embedding API Key/模型，"
                    + "再点「重建 Java 向量索引」。当前仅关键词检索。");
        }
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
        indexDoc(doc);
        docMapper.insert(doc);
        // insert 后才有 id，再写 chunk
        javaRagService.deleteChunks(doc.getId());
        int n = javaRagService.indexDocument(doc, runtimeConfig());
        if (n > 0 && embeddingClient.isAvailable(runtimeConfig())) {
            doc.setStatus("ready");
            doc.setErrorMsg(null);
            doc.setUpdatedAt(LocalDateTime.now());
            docMapper.updateById(doc);
        }
        return toVo(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocVO ingestFile(MultipartFile file, String domain, String title) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
        try {
            byte[] bytes = file.getBytes();
            String content;
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
            indexDoc(doc);
            docMapper.insert(doc);
            if (!content.startsWith("[binary file]")) {
                int n = javaRagService.indexDocument(doc, runtimeConfig());
                if (n > 0 && embeddingClient.isAvailable(runtimeConfig())) {
                    doc.setStatus("ready");
                    doc.setErrorMsg(null);
                    doc.setUpdatedAt(LocalDateTime.now());
                    docMapper.updateById(doc);
                }
            }
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
        javaRagService.deleteChunks(id);
        docMapper.deleteById(id);
    }

    @Override
    public KnowledgeQueryVO query(KnowledgeQueryForm form) {
        String question = form.getQuestion().trim();
        AiModelConfig cfg = runtimeConfig();
        Map<Long, AiKnowledgeDoc> docMap = loadDocMap();

        // 1) Java 向量 RAG（主路径）
        KnowledgeQueryVO javaHit = javaRagService.query(question, cfg, docMap);
        if (javaHit != null) {
            return javaHit;
        }

        // 2) 可选 LightRAG（若仍在跑）
        if (lightRagUp()) {
            String mode = StringUtils.hasText(form.getMode()) ? form.getMode() : "mix";
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
                        .hint("答案来自可选 LightRAG（mode=" + mode + "）")
                        .build();
            }
        }

        // 3) 关键词降级
        KnowledgeQueryVO local = localKeywordAnswer(question);
        if (local != null) {
            return local;
        }
        return KnowledgeQueryVO.builder()
                .answer("未命中知识库。请「灌入演示语料」；并在模型配置填写 Embedding Key 后点「重建 Java 向量索引」。")
                .mode("none")
                .source("degraded")
                .degraded(true)
                .references(List.of())
                .hint("无向量命中且关键词无匹配")
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
        // 已有文档也尝试补建向量（无 Embedding Key 时跳过）
        try {
            reindexJavaVectors();
        } catch (Exception ex) {
            log.info("seed skip vector reindex: {}", ex.getMessage());
        }
        return n;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reindexJavaVectors() {
        AiModelConfig cfg = runtimeConfig();
        if (!embeddingClient.isAvailable(cfg)) {
            throw new IllegalStateException("请先在「模型配置」填写 Embedding API Key 与模型，再重建索引");
        }
        List<AiKnowledgeDoc> docs = docMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDoc>()
                .isNotNull(AiKnowledgeDoc::getContentText)
                .orderByAsc(AiKnowledgeDoc::getId)
                .last("limit 100"));
        int n = 0;
        for (AiKnowledgeDoc doc : docs) {
            if (!StringUtils.hasText(doc.getContentText())
                    || doc.getContentText().startsWith("[binary file]")) {
                continue;
            }
            try {
                int chunks = javaRagService.indexDocument(doc, cfg);
                if (chunks > 0) {
                    doc.setStatus("ready");
                    doc.setErrorMsg(null);
                } else {
                    doc.setStatus("local");
                    doc.setErrorMsg("embedding returned empty");
                }
                doc.setUpdatedAt(LocalDateTime.now());
                docMapper.updateById(doc);
                n++;
            } catch (Exception ex) {
                log.warn("java reindex doc {} failed: {}", doc.getId(), ex.getMessage());
                doc.setStatus("failed");
                doc.setErrorMsg(safe(ex.getMessage()));
                doc.setUpdatedAt(LocalDateTime.now());
                docMapper.updateById(doc);
            }
        }
        log.info("reindexJavaVectors docs={}", n);
        return n;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reindexToLightRag() {
        // 兼容旧 API：改为重建 Java 向量
        return reindexJavaVectors();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refreshIndexStatus() {
        int changed = 0;
        // 可选：仍探测 LightRAG track
        if (lightRagUp()) {
            List<AiKnowledgeDoc> indexing = docMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDoc>()
                    .eq(AiKnowledgeDoc::getStatus, "indexing")
                    .last("limit 50"));
            for (AiKnowledgeDoc doc : indexing) {
                if (StringUtils.hasText(doc.getLightragDocId())) {
                    Map<String, Object> track = lightRagClient.trackStatus(doc.getLightragDocId(), lightRagBase());
                    if (LightRagClient.looksReady(track)) {
                        doc.setStatus("ready");
                        doc.setUpdatedAt(LocalDateTime.now());
                        docMapper.updateById(doc);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    private void indexDoc(AiKnowledgeDoc doc) {
        if (embeddingClient.isAvailable(runtimeConfig())) {
            doc.setStatus("indexing");
        } else {
            doc.setStatus("local");
        }
    }

    private Map<Long, AiKnowledgeDoc> loadDocMap() {
        return docMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDoc>()
                        .last("limit 200"))
                .stream()
                .collect(Collectors.toMap(AiKnowledgeDoc::getId, Function.identity(), (a, b) -> a));
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
            hits.add(new Hit(doc, bestSnippet(text, tokens), score));
        }
        if (hits.isEmpty()) {
            return null;
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed());
        List<Hit> top = hits.stream().limit(3).toList();

        StringBuilder answer = new StringBuilder();
        answer.append("【本地关键词检索】（未配置 Embedding 或向量未命中时的降级）：\n\n");
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
                .hint("关键词匹配；在模型配置填写 Embedding 后可升级为 Java 向量 RAG")
                .build();
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

    private AiModelConfig runtimeConfig() {
        return modelConfigService.getRuntimeConfig(AiModelConfigServiceImpl.DEFAULT_KEY);
    }

    private boolean lightRagUp() {
        return lightRagClient.health(lightRagBase());
    }

    private String lightRagBase() {
        AiModelConfig cfg = runtimeConfig();
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
            if (r != null) {
                out.add(new LinkedHashMap<>(r));
            }
        }
        return out;
    }

    private static List<String> tokenize(String q) {
        String cleaned = q.replaceAll("[\\p{Punct}？?！!，,。；;：:、\\s]+", " ").trim();
        if (!StringUtils.hasText(cleaned)) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : cleaned.split("\\s+")) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
            if (part.length() >= 4 && part.codePoints().anyMatch(
                    cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
                for (int i = 0; i < part.length() - 1; i++) {
                    tokens.add(part.substring(i, i + 2));
                }
            }
        }
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
