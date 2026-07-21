# 架构说明

## 总览

本项目在 **youlai-mall 微服务商城** 之上新增 **mall-ai** 服务，通过网关统一鉴权，对接国内 LLM（通义/DeepSeek）与 **LightRAG** Python 服务，实现运营助手、知识库、工单 Agent、数据洞察。

```text
Vue3 Admin ──► Gateway(:9999) ──► auth / system / pms / oms / ★mall-ai(:8805)
                                      │
                          ┌───────────┴────────────┐
                          ▼                        ▼
                 Spring AI Alibaba            LightRAG Server
                 Multi-Agent + Tools          Graph + Vector RAG
                          │                        │
                          ▼                        ▼
                 MySQL / Redis / MinIO        rag_storage
```

## 服务清单

| 服务 | 职责 | 端口 |
|------|------|------|
| youlai-gateway | 路由、鉴权、CORS、文档聚合 | 9999 |
| youlai-auth | OAuth2 / JWT | 9000 |
| youlai-system | 用户/菜单/角色/文件 | 8800 |
| mall-ums | 会员 | 8801 |
| mall-pms | 商品 | 8802 |
| mall-oms | 订单 | 8803 |
| mall-sms | 营销 | 8804 |
| **mall-ai** | AI 能力 | **8805** |

## mall-ai 内部包结构

```text
com.youlai.mall.ai
├── controller     # REST
├── agent          # 多 Agent 编排（Phase1+）
├── tool           # 业务 Tool（查单/查品/建工单）
├── rag            # LightRagClient
├── insight        # 白名单分析
├── service / mapper / model
└── config
```

## 关键设计

1. **业务写操作必须走 Tool + 服务端校验**，禁止模型直接写库。
2. **LightRAG 异构集成**：Java 只做 HTTP 客户端，索引与图谱由 Python 服务负责。
3. **数据洞察禁止自由 SQL**，仅允许预置聚合模板。
4. **无 API Key 可启动**：`ai.llm.mock-enabled=true` 时助手规则降级。

## 中间件

| 组件 | 地址 | 账号 |
|------|------|------|
| MySQL | localhost:3306 | root / 123456 |
| Redis | localhost:6379 | **无密码** |
| Nacos | localhost:8848/nacos | nacos / nacos |
| MinIO | localhost:9090 | minioadmin / minioadmin |
| LightRAG | localhost:9621 | Phase2 启用 |

## 网关路由

显式路由（见 Nacos `youlai-gateway.yaml`）：

- `/youlai-auth/**` → youlai-auth
- `/youlai-system/**` → youlai-system
- `/mall-ai/**` → mall-ai

同时开启 `discovery.locator`，服务名小写路径亦可访问。
