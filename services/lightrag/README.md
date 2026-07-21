# LightRAG 本地说明

本项目 **不必 Docker**。LightRAG 用本机 Python 启动；**Embedding 计划接英伟达（NVIDIA）**，Chat LLM 仍可用通义/DeepSeek。

## 模型分工（重要）

| 角色 | 计划 | 说明 |
|------|------|------|
| Chat / 抽取 LLM | 通义 DashScope 或 DeepSeek | 问答、实体关系抽取 |
| **Embedding** | **NVIDIA**（NIM 或 OpenAI 兼容接口） | 向量检索，与 Chat 分离配置 |

LightRAG 支持为 LLM 与 Embedding 配置不同 binding（以官方 env 为准）。

## 启用方式（Phase 2，本机）

```bash
# 推荐（官方）
# uv tool install "lightrag-hku[api]"
# 配置 .env 后
# lightrag-server
```

根目录 `.env` 预留示例（按你实际 NVIDIA 端点修改）：

```env
# Chat LLM（国内）
LLM_BINDING=openai
LLM_MODEL=deepseek-chat
# LLM_BINDING_HOST=...
# LLM_BINDING_API_KEY=...

# Embedding = NVIDIA
EMBEDDING_BINDING=openai
EMBEDDING_MODEL=nvidia/nv-embedqa-e5-v5
EMBEDDING_DIM=1024
EMBEDDING_BINDING_HOST=https://integrate.api.nvidia.com/v1
NVIDIA_API_KEY=nvapi-xxx
# 部分环境用 OPENAI_API_KEY 兼容字段传 NVIDIA key，以 LightRAG 文档为准
```

也可用 **本地 GPU** 上的 NVIDIA 模型（Ollama / vLLM / NIM 本地），只要提供 OpenAI-compatible embeddings API。

## 验收

1. `http://localhost:9621/health` 可用  
2. 上传 `demo-docs/售后政策.md` 能索引  
3. `mall-ai`：`ai.lightrag.base-url=http://localhost:9621`  
4. `POST /api/v1/ai/knowledge/query` 返回非降级答案  

## 目录

- `rag_storage/`：索引与图谱（gitignore）
- `inputs/`：导入目录
- `demo-docs/`：演示语料（售后政策、运营 SOP）

## Java 集成

`backend/mall-ai/ai-boot/.../rag/LightRagClient.java`