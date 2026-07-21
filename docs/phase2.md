# Phase 2：LightRAG 知识库

> 状态：**已实现（MVP，含本地降级）**  
> 前置：Phase 1 运营助手可用  
> 目标：文档入库 + 知识问答；LightRAG 可用时走图+向量，不可用时关键词降级仍可演示

---

## 1. 阶段目标

1. 管理端 **AI中心 → 知识库** 可上传/文本入库/灌入演示语料  
2. 提问返回答案 + 引用（LightRAG 或本地段落）  
3. Java 只做 HTTP 客户端 + 元数据表 `ai_knowledge_doc`  
4. Embedding 配置预留 NVIDIA（模型配置页），Chat 仍可用通义/DeepSeek  

**本阶段不做**：MinIO 强制存原件（可选增强）、工单 Agent（Phase 3）、洞察大屏（Phase 4）

---

## 2. 架构

```text
Admin 知识库页
   │  文档 CRUD / query
   ▼
Gateway → mall-ai
            ├─ ai_knowledge_doc（MySQL 元数据 + content_text 缓存）
            ├─ LightRagClient ──HTTP──► LightRAG :9621
            │     /documents/text | /documents/upload | /query | /health
            └─ LightRAG DOWN → 本地关键词检索 content_text
```

---

## 3. API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/ai/knowledge/status` | LightRAG UP/DOWN + 本地文档数 |
| GET | `/api/v1/ai/knowledge/docs` | 文档列表 |
| POST | `/api/v1/ai/knowledge/docs/text` | 文本入库 |
| POST | `/api/v1/ai/knowledge/docs/upload` | multipart 文件上传 |
| POST | `/api/v1/ai/knowledge/docs/seed` | 灌入售后政策 + 运营 SOP（幂等） |
| DELETE | `/api/v1/ai/knowledge/docs/{id}` | 删本地元数据 |
| POST | `/api/v1/ai/knowledge/query` | 问答 body: `{question, mode?}` |

---

## 4. 本地演示（无 LightRAG / 无 NVIDIA Key）

1. 启动后端（`PlatformLauncher`）+ 前端  
2. 打开 **知识库** → **灌入演示语料**  
3. 问：「7 天无理由退货怎么处理？」  
4. 期望：`source=local`，答案含 7 日无理由要点 + 引用折叠  

状态标签 `local` 表示仅本地缓存，未同步 LightRAG。

---

## 5. 启用真实 LightRAG（可选增强）

```bat
REM 1) 安装（任选）
pip install "lightrag-hku[api]"
REM 或: uv tool install "lightrag-hku[api]"

REM 2) 配置 services/lightrag/.env（复制 env.example）
REM    Chat: 通义/DeepSeek OpenAI 兼容
REM    Embedding: NVIDIA integrate.api.nvidia.com

REM 3) 启动
scripts\start-lightrag.bat
```

验收：

1. `http://localhost:9621/health`  
2. 模型配置页「LightRAG」连通性测试通过  
3. 知识库页点 **同步到 LightRAG**（把 `local` 文档推索引）  
4. 稍候 **刷新索引状态** → `indexing` → `ready`  
5. 问答 `source=lightrag`，mode=`mix`  

相关 API：`POST /docs/reindex`、`POST /docs/refresh-status`；status 返回 `readyCount` / `indexingCount`。

官方 API 参考：[LightRAG API Server](https://github.com/HKUDS/LightRAG)

---

## 6. 表字段增量

`ai_knowledge_doc` 增加：

- `content_text` MEDIUMTEXT — 原文缓存  
- `error_msg` VARCHAR(512) — 索引失败信息  

已有库执行：

```sql
ALTER TABLE mall_ai.ai_knowledge_doc
  ADD COLUMN content_text MEDIUMTEXT NULL COMMENT '原文缓存' AFTER created_by,
  ADD COLUMN error_msg VARCHAR(512) NULL AFTER content_text;
```

（`init-ai.sql` 已包含上述字段。）

---

## 7. 验收标准

- [x] 灌入演示语料后可问答  
- [x] 文本入库 / 上传 / 删除  
- [x] LightRAG DOWN 时本地降级不 500  
- [x] LightRAG UP 时转发 `/query` 与入库接口  
- [x] 前端展示答案、source、引用  
- [x] 一键启动路径不变  

---

## 8. 下一步 Phase 3

客服/工单多 Agent：意图 → 政策 RAG（复用本阶段检索）→ 建单 → 人工接管。
