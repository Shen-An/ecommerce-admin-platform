# LightRAG 本地说明

本项目 **不必 Docker**。LightRAG 用本机 Python 启动；**Embedding 计划接英伟达（NVIDIA）**，Chat LLM 仍可用通义/DeepSeek。

Java 侧已对接：`LightRagClient` + 知识库 API；**服务未启动时 mall-ai 会用本地关键词降级**，答辩仍可演示。

## 模型分工

| 角色 | 计划 | 说明 |
|------|------|------|
| Chat / 抽取 LLM | 通义 DashScope 或 DeepSeek | 问答、实体关系抽取 |
| **Embedding** | **NVIDIA** OpenAI 兼容 | 向量检索，与 Chat 分离 |

## 快速启动

```bat
REM 1. 安装
pip install "lightrag-hku[api]"

REM 2. 配置
copy services\lightrag\env.example services\lightrag\.env
REM 编辑 .env 填入 LLM_BINDING_API_KEY / EMBEDDING_BINDING_API_KEY

REM 3. 启动
scripts\start-lightrag.bat
```

健康检查：`http://localhost:9621/health`  
Swagger：`http://localhost:9621/docs`

管理端 **模型配置** 中 `lightrag_base_url` 默认 `http://localhost:9621`。

## 验收

1. `/health` 可用  
2. 管理端知识库「灌入演示语料」或上传 `demo-docs/*.md`  
3. LightRAG UP 时点 **同步到 LightRAG**（推 local 文档建真索引），再 **刷新索引状态**  
4. 提问「7 天无理由退货怎么处理？」→ `source=lightrag`  
5. LightRAG DOWN 时仍可 `source=local` 关键词降级

## 目录

- `rag_storage/`：索引与图谱（gitignore）
- `inputs/`：导入目录
- `demo-docs/`：演示语料
- `env.example`：环境变量模板

## Java 集成

- 客户端：`backend/mall-ai/.../rag/LightRagClient.java`
- API：`/api/v1/ai/knowledge/**`
- 设计说明：[docs/phase2.md](../../docs/phase2.md)
