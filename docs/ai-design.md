# AI 能力设计

## 场景一：运营智能助手

**目标**：运营人员自然语言查询订单/商品/库存，生成摘要。

**编排（目标形态）**：

1. RouterAgent 识别意图
2. 调用 Tools：`queryOrders` / `queryProducts` / `queryInventory` / `summarizeOpsDaily`
3. 汇总为文本 + 结构化 cards

**当前**：Phase1 已实现规则意图 + OMS/PMS 只读 Tool + 会话落库 + 可选 LLM 润色。  
详见 [phase1.md](./phase1.md)。

## 场景二：LightRAG 知识库

**链路**：

```text
上传文档 → MinIO 存原件 → LightRAG 索引（实体关系图+向量）
提问 → mall-ai → LightRAG mode=mix → 答案 + 引用
```

**当前**：Phase2 已实现文档入库、`LightRagClient` 转发、本地关键词降级。  
详见 [phase2.md](./phase2.md)。

**演示语料**：售后政策、运营 SOP、客服话术。

## 场景三：客服/工单多 Agent

**Sequential 流水线**：

1. IntentAgent：投诉 / 退款 / 物流 / 咨询
2. PolicyRagAgent：检索售后政策
3. TicketAgent：写入 `ai_ticket`
4. EscalationGate：低置信度 / 敏感词 → `escalated`，前端人工接管

## 场景四：数据洞察

自然语言 → QueryPlanner 选择**白名单模板**（销量 TopN、退货率、库存预警）→ 执行聚合 → ECharts option + 解读。

**安全红线**：不执行模型生成的任意 SQL。

## 配置项

| Key | 含义 | 默认 |
|-----|------|------|
| `ai.llm.mock-enabled` | 无模型时规则降级 | true |
| `ai.llm.provider` | dashscope / deepseek | dashscope |
| `ai.lightrag.base-url` | LightRAG 地址 | http://localhost:9621 |
| `AI_DASHSCOPE_API_KEY` | 通义 Key（可被库配置覆盖） | - |
| `NVIDIA_API_KEY` / 库字段 `embedding_api_key` | NVIDIA Embedding | - |

### 前端可配置（推荐）

表 `mall_ai.ai_model_config`（`config_key=default`），管理端 **AI中心 → 模型配置**：

| 字段 | 默认 |
|------|------|
| chat_provider / chat_model | dashscope / `qwen-plus` |
| embedding_provider / embedding_model | nvidia / **`nvidia/llama-nemotron-embed-1b-v2`** |
| embedding_base_url / embedding_dim | `https://integrate.api.nvidia.com/v1` / 2048 |
| lightrag_base_url | `http://localhost:9621` |
| mock_enabled | 1 |

API：`/api/v1/ai/settings`（GET/PUT）+ `/test`（chat|embedding|lightrag）。密钥返回脱敏；PUT 时空 Key 不覆盖。

运行时服务通过 `AiModelConfigService#getRuntimeConfig` 读取完整密钥（仅服务内部）。

## 表结构

见 `docker/mysql/init-ai.sql`：`ai_session` / `ai_message` / `ai_knowledge_doc` / `ai_ticket` / `ai_ticket_log` / `ai_insight_query` / **`ai_model_config`**。  
菜单初始化：`docker/mysql/init-ai-menu.sql`。
