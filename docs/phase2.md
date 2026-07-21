# Phase 2：知识库（纯 Java RAG）

> 状态：**已实现** — 主路径为 mall-ai 内向量 RAG（Key 全部来自「模型配置」）  
> 无官方 Java 版 LightRAG；Python LightRAG 仅可选保留，**答辩默认不必启动**  
> 前置：Phase 1 运营助手可用

---

## 1. 阶段目标

1. 管理端 **AI中心 → 知识库** 可上传/文本入库/灌入演示语料  
2. 提问返回答案 + 引用：`source=java_rag`（向量）或 `local`（关键词降级）  
3. Embedding / Chat Key **只在模型配置页**维护，不需要 `services/lightrag/.env`  
4. 表：`ai_knowledge_doc` + `ai_knowledge_chunk`（JSON 向量）

---

## 2. 架构

```text
Admin 知识库页 / 模型配置
   │
   ▼
Gateway → mall-ai
            ├─ ai_knowledge_doc（原文缓存）
            ├─ ai_knowledge_chunk（分块 + embedding_json）
            ├─ EmbeddingClient ──HTTP──► NVIDIA / DashScope OpenAI 兼容 /embeddings
            ├─ ChatLlmClient（可选）生成综合答案
            └─ 无 Embedding Key → 关键词检索 content_text
```

检索：问题 embedding → 与 chunk 余弦相似度 Top-K → 可选 Chat 润色。

---

## 3. API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/ai/knowledge/status` | engine/embedding/chat/chunk 计数 |
| GET | `/api/v1/ai/knowledge/docs` | 文档列表 |
| POST | `/api/v1/ai/knowledge/docs/text` | 文本入库 + 尝试向量化 |
| POST | `/api/v1/ai/knowledge/docs/upload` | multipart 上传 |
| POST | `/api/v1/ai/knowledge/docs/seed` | 演示语料（幂等） |
| POST | `/api/v1/ai/knowledge/docs/reindex` | **重建 Java 向量索引** |
| DELETE | `/api/v1/ai/knowledge/docs/{id}` | 删文档与分块 |
| POST | `/api/v1/ai/knowledge/query` | 问答 |

---

## 4. 演示路径

### 无 Embedding Key（关键词）

1. 知识库 → **灌入演示语料**  
2. 问：「7 天无理由退货怎么处理？」→ `source=local`

### 真向量 RAG（推荐）

1. **AI中心 → 模型配置**：填写 Embedding API Key / baseUrl / model（NVIDIA 默认）  
2. （可选）Chat Key，用于 `vector+llm` 综合答案  
3. 知识库 → 灌入语料 → **重建 Java 向量索引**  
4. 再问同样问题 → `source=java_rag`，引用带 score  

---

## 5. 与旧 LightRAG 的关系

- 无成熟官方 **Java LightRAG**；图 RAG 仍属 Python 生态。  
- 本项目改为 **纯 Java 检索增强**，满足「真 RAG + 配置统一」。  
- `LightRagClient` 仍保留：若本机 :9621 在跑，query 在 Java 向量未命中时可能回落 LightRAG；**默认不依赖**。  
- `services/lightrag/` 文档仅作可选实验。

---

## 6. 代码入口

- `llm/EmbeddingClient.java`  
- `rag/JavaRagService.java`  
- `service/impl/KnowledgeServiceImpl.java`  
- 前端：`admin/src/views/ai/knowledge/index.vue`
