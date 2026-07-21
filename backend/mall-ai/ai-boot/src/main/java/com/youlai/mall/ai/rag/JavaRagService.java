package com.youlai.mall.ai.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.mall.ai.llm.ChatLlmClient;
import com.youlai.mall.ai.llm.EmbeddingClient;
import com.youlai.mall.ai.mapper.AiKnowledgeChunkMapper;
import com.youlai.mall.ai.model.entity.AiKnowledgeChunk;
import com.youlai.mall.ai.model.entity.AiKnowledgeDoc;
import com.youlai.mall.ai.model.entity.AiModelConfig;
import com.youlai.mall.ai.model.vo.KnowledgeQueryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 Java RAG：分块 → Embedding（模型配置页 Key）→ 余弦检索 → 可选 Chat 生成。
 * 不依赖 Python LightRAG。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JavaRagService {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 80;
    private static final int TOP_K = 4;
    private static final double MIN_SCORE = 0.25;

    private final AiKnowledgeChunkMapper chunkMapper;
    private final EmbeddingClient embeddingClient;
    private final ChatLlmClient chatLlmClient;

    public boolean embeddingReady(AiModelConfig config) {
        return embeddingClient.isAvailable(config);
    }

    /**
     * 为文档重建向量分块。返回写入 chunk 数；无 Embedding Key 时返回 0。
     */
    public int indexDocument(AiKnowledgeDoc doc, AiModelConfig config) {
        if (doc == null || doc.getId() == null || !StringUtils.hasText(doc.getContentText())) {
            return 0;
        }
        if (doc.getContentText().startsWith("[binary file]")) {
            return 0;
        }
        deleteChunks(doc.getId());
        if (!embeddingClient.isAvailable(config)) {
            log.info("skip vector index doc {}: embedding not configured", doc.getId());
            return 0;
        }
        List<String> pieces = splitChunks(doc.getContentText());
        int n = 0;
        int idx = 0;
        for (String piece : pieces) {
            float[] vec = embeddingClient.embed(config, piece);
            AiKnowledgeChunk chunk = new AiKnowledgeChunk();
            chunk.setDocId(doc.getId());
            chunk.setChunkIndex(idx++);
            chunk.setContent(piece);
            if (vec != null) {
                chunk.setEmbeddingJson(EmbeddingClient.toJson(vec));
                chunk.setEmbeddingDim(vec.length);
            }
            chunk.setCreatedAt(LocalDateTime.now());
            chunkMapper.insert(chunk);
            n++;
        }
        log.info("java rag indexed doc {} chunks={}", doc.getId(), n);
        return n;
    }

    public void deleteChunks(Long docId) {
        if (docId == null) {
            return;
        }
        chunkMapper.delete(new LambdaQueryWrapper<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getDocId, docId));
    }

    public long countChunks() {
        return chunkMapper.selectCount(new LambdaQueryWrapper<>());
    }

    public long countEmbeddedChunks() {
        return chunkMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeChunk>()
                .isNotNull(AiKnowledgeChunk::getEmbeddingJson)
                .ne(AiKnowledgeChunk::getEmbeddingJson, ""));
    }

    /**
     * 向量检索 + 可选 LLM 综合。无向量时返回 null（由调用方走关键词）。
     */
    public KnowledgeQueryVO query(String question, AiModelConfig config,
                                  Map<Long, AiKnowledgeDoc> docById) {
        if (!StringUtils.hasText(question) || !embeddingClient.isAvailable(config)) {
            return null;
        }
        float[] qVec = embeddingClient.embed(config, question);
        if (qVec == null) {
            return null;
        }
        List<AiKnowledgeChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiKnowledgeChunk>()
                .isNotNull(AiKnowledgeChunk::getEmbeddingJson)
                .ne(AiKnowledgeChunk::getEmbeddingJson, "")
                .orderByDesc(AiKnowledgeChunk::getId)
                .last("limit 500"));
        if (chunks.isEmpty()) {
            return null;
        }

        record Scored(AiKnowledgeChunk chunk, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (AiKnowledgeChunk c : chunks) {
            float[] v = EmbeddingClient.fromJson(c.getEmbeddingJson());
            double s = EmbeddingClient.cosine(qVec, v);
            if (s >= MIN_SCORE) {
                scored.add(new Scored(c, s));
            }
        }
        if (scored.isEmpty()) {
            return null;
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<Scored> top = scored.stream().limit(TOP_K).toList();

        StringBuilder ctx = new StringBuilder();
        List<Map<String, Object>> refs = new ArrayList<>();
        int i = 1;
        for (Scored hit : top) {
            AiKnowledgeDoc d = docById != null ? docById.get(hit.chunk.getDocId()) : null;
            String title = d != null ? d.getTitle() : ("doc#" + hit.chunk.getDocId());
            String snip = hit.chunk.getContent();
            if (snip != null && snip.length() > 400) {
                snip = snip.substring(0, 400) + "…";
            }
            ctx.append("[").append(i).append("] 《").append(title).append("》\n")
                    .append(hit.chunk.getContent()).append("\n\n");
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("reference_id", String.valueOf(i));
            ref.put("title", title);
            ref.put("file_path", d != null && d.getFileName() != null ? d.getFileName() : title);
            ref.put("content", List.of(snip != null ? snip : ""));
            ref.put("score", Math.round(hit.score * 1000) / 1000.0);
            ref.put("docId", hit.chunk.getDocId());
            refs.add(ref);
            i++;
        }

        String llmAnswer = chatLlmClient.answerWithContext(config, question, ctx.toString());
        String answer;
        boolean usedLlm = StringUtils.hasText(llmAnswer);
        if (usedLlm) {
            answer = llmAnswer;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("【Java 向量检索】根据知识库相似度排序片段整理：\n\n");
            int j = 1;
            for (Scored hit : top) {
                AiKnowledgeDoc d = docById != null ? docById.get(hit.chunk.getDocId()) : null;
                String title = d != null ? d.getTitle() : ("doc#" + hit.chunk.getDocId());
                String snip = hit.chunk.getContent();
                if (snip != null && snip.length() > 280) {
                    snip = snip.substring(0, 280) + "…";
                }
                sb.append(j++).append(". 《").append(title).append("》（相似度 ")
                        .append(String.format("%.2f", hit.score)).append("）\n")
                        .append(snip != null ? snip.trim() : "").append("\n\n");
            }
            answer = sb.toString().trim();
        }

        return KnowledgeQueryVO.builder()
                .answer(answer)
                .mode(usedLlm ? "vector+llm" : "vector")
                .source("java_rag")
                .degraded(false)
                .references(refs)
                .hint(usedLlm
                        ? "Java RAG：Embedding 检索 + Chat 生成（Key 来自模型配置页）"
                        : "Java RAG：仅向量检索摘录（可配置 Chat Key 生成综合答案）")
                .build();
    }

    public static List<String> splitChunks(String text) {
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return out;
        }
        String t = text.replace("\r\n", "\n").trim();
        // 优先按空行分段，再硬切
        String[] paras = t.split("\\n\\s*\\n");
        StringBuilder buf = new StringBuilder();
        for (String p : paras) {
            String para = p.trim();
            if (para.isEmpty()) {
                continue;
            }
            if (buf.length() + para.length() + 2 <= CHUNK_SIZE) {
                if (buf.length() > 0) {
                    buf.append("\n\n");
                }
                buf.append(para);
            } else {
                if (buf.length() > 0) {
                    out.add(buf.toString());
                    String prev = buf.toString();
                    buf = new StringBuilder();
                    if (prev.length() > CHUNK_OVERLAP) {
                        buf.append(prev.substring(prev.length() - CHUNK_OVERLAP));
                    }
                }
                if (para.length() <= CHUNK_SIZE) {
                    if (buf.length() > 0) {
                        buf.append("\n\n");
                    }
                    buf.append(para);
                } else {
                    for (int i = 0; i < para.length(); i += CHUNK_SIZE - CHUNK_OVERLAP) {
                        int end = Math.min(para.length(), i + CHUNK_SIZE);
                        out.add(para.substring(i, end));
                        if (end >= para.length()) {
                            break;
                        }
                    }
                }
            }
        }
        if (buf.length() > 0) {
            out.add(buf.toString());
        }
        if (out.isEmpty() && StringUtils.hasText(t)) {
            for (int i = 0; i < t.length(); i += CHUNK_SIZE - CHUNK_OVERLAP) {
                int end = Math.min(t.length(), i + CHUNK_SIZE);
                out.add(t.substring(i, end));
                if (end >= t.length()) {
                    break;
                }
            }
        }
        return out;
    }
}
