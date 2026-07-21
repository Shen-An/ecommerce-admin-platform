# LightRAG 本地说明（可选，非主路径）

> **2024+ 项目主路径已改为 mall-ai 纯 Java RAG**（见 [docs/phase2.md](../../docs/phase2.md)）。  
> Key 全部在管理端 **模型配置**；**不必**再为答辩准备本目录 `.env`。  
> 本目录仅保留 Python LightRAG 实验入口。

没有官方成熟 **Java 版 LightRAG**。需要图索引时仍用 Python；需要「真向量 + 统一配置」时用 Java RAG。

## 可选启动

```bat
pip install "lightrag-hku[api]"
copy services\lightrag\env.example services\lightrag\.env
scripts\start-lightrag.bat
```

健康检查：`http://localhost:9621/health`  
Java 侧仍保留 `LightRagClient`，仅在 Java 向量未命中且服务 UP 时可能使用。

## 推荐演示

1. 模型配置 → Embedding Key  
2. 知识库 → 灌入语料 → 重建 Java 向量索引  
3. 问答 `source=java_rag`
