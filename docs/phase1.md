# Phase 1：运营智能助手（Tool + 会话）

> 状态：**待开发**  
> 前置：Phase 0 底座完成（微服务可一键启动、管理端可登录、商品/订单/会员/营销页可用、AI 模型配置页可用）  
> 目标周期：约 1 周

---

## 1. 阶段目标

把「运营助手」从 **规则 Mock** 升级为可演示的 **真实业务问答**：

1. 运营人员用自然语言查 **订单 / 商品 / 库存**
2. 后端通过 **Tool Calling** 调 OMS/PMS（禁止模型直接写库）
3. **会话落库**，刷新页面可续聊
4. 无 API Key 时仍可 Mock 降级，有 Key 时走通义/兼容 OpenAI 接口

**本阶段不做**：LightRAG 知识库（Phase 2）、工单多 Agent（Phase 3）、数据洞察图表（Phase 4）。

---

## 2. Phase 0 已具备（本阶段依赖）

| 能力 | 说明 |
|------|------|
| 全栈启动 | `PlatformLauncher` 默认起 gateway/auth/system/ai/pms/ums/oms/sms |
| 网关路由 | `/mall-ai/**` `/mall-pms/**` `/mall-oms/**` `/mall-ums/**` `/mall-sms/**` |
| 本地降级 | RabbitMQ / Seata 默认关闭，管理端列表页不依赖 MQ |
| AI 模型配置 | `mall_ai.ai_model_config` + 前端「AI中心 → 模型配置」 |
| 助手骨架 | `POST /api/v1/ai/assistant/chat` 规则意图 + 演示 cards |
| 会话表 | `ai_session` / `ai_message`（见 `docker/mysql/init-ai.sql`） |
| 菜单 | AI中心 / 运营助手 / 知识库 / 模型配置 |

本地启动见 [local-setup.md](./local-setup.md)。

---

## 3. 范围

### 3.1 In Scope

| 项 | 说明 |
|----|------|
| 意图识别 | 订单查询 / 商品库存 / 运营摘要 / 闲聊 |
| Tool | `queryOrders`、`queryProducts`（含库存）、`summarizeOpsDaily` |
| Feign | mall-ai → mall-oms / mall-pms（只读查询） |
| 会话 | 创建会话、消息写入 `ai_session`/`ai_message`、按 sessionId 续聊 |
| LLM | 读 `AiModelConfigService#getRuntimeConfig`；`mock_enabled=1` 走规则 |
| 前端 | 运营助手页：多轮对话 UI、cards 展示、会话列表（可简版） |
| 安全 | 管理端 JWT；Tool 只读；参数校验；敏感字段脱敏 |

### 3.2 Out of Scope

- 任意 SQL / 自由写库
- 退款、改价、强制关单等写操作 Tool
- LightRAG 上传/检索
- 工单创建与人工接管
- ECharts 洞察大屏

---

## 4. 目标架构

```text
Admin 运营助手页
    │  POST /mall-ai/api/v1/ai/assistant/chat
    ▼
Gateway ──► mall-ai
              │
              ├─ 加载会话历史 (ai_message)
              ├─ 读模型配置 (ai_model_config)
              ├─ mock? 规则路由 : LLM + Tool Calling
              │         │
              │         ├─ queryOrders  ──Feign──► mall-oms
              │         ├─ queryProducts──Feign──► mall-pms
              │         └─ summarizeOpsDaily ──► OMS/PMS 聚合
              └─ 落库 user/assistant 消息 + 返回 reply + cards
```

---

## 5. 接口约定

### 5.1 对话（已有，需增强）

```http
POST /api/v1/ai/assistant/chat
Content-Type: application/json

{
  "sessionId": null,
  "message": "查一下待发货订单"
}
```

响应：

```json
{
  "code": "00000",
  "data": {
    "sessionId": 1001,
    "reply": "为你找到 3 笔待发货订单…",
    "intent": "query_order",
    "cards": [
      { "type": "order", "orderSn": "...", "status": "待发货", "amount": "199.00" }
    ],
    "mock": false
  }
}
```

### 5.2 建议新增

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/ai/assistant/sessions` | 当前用户会话列表 |
| GET | `/api/v1/ai/assistant/sessions/{id}/messages` | 历史消息 |
| DELETE | `/api/v1/ai/assistant/sessions/{id}` | 结束/删除会话（可选） |

---

## 6. Tool 规格

| Tool 名 | 入参（示例） | 数据源 | 返回 cards 类型 |
|---------|--------------|--------|-----------------|
| `queryOrders` | `status?` `orderSn?` `pageSize?` | OMS 管理端订单查询 | `order` |
| `queryProducts` | `keyword?` `spuId?` | PMS SPU/SKU | `product` |
| `summarizeOpsDaily` | `date?` 默认今天 | OMS 计数 + PMS 库存预警（可简化） | 文本摘要，可无 card |

规则：

- Tool 入参由服务端 Schema 校验，拒绝越权字段
- 仅查询当前登录运营账号有权限的数据（沿用资源服务鉴权）
- 失败时返回可读错误，不把堆栈抛给前端

---

## 7. 实现任务清单

### P1-1 会话持久化

- [ ] `AiSession` / `AiMessage` Entity + Mapper + Service
- [ ] chat 时：无 sessionId 则新建；写入 user 消息；写 assistant 消息
- [ ] 按用户维度列会话、拉历史

### P1-2 Feign 只读客户端

- [ ] `oms-api` / 已有 `pms-api`：封装订单分页、商品分页（或最小 DTO）
- [ ] mall-ai 引入依赖并 `@EnableFeignClients`
- [ ] 本地联调：网关 token 透传 / 内部调用策略（与现有 common-security 一致）

### P1-3 Tool 层

- [ ] `OrderQueryTool` / `ProductQueryTool` / `OpsSummaryTool`
- [ ] 统一转换为 `cards` 结构
- [ ] 单元测试：入参校验 + Mock Feign

### P1-4 LLM 接入

- [ ] 从 `ai_model_config` 读 chat baseUrl / model / apiKey
- [ ] `mock_enabled=true`：保留现有规则路由，但 Tool 可切真实 Feign（可选开关）
- [ ] `mock_enabled=false`：DashScope OpenAI 兼容 或 Spring AI Alibaba 调 Tool
- [ ] 超时、重试、空 Key 友好提示

### P1-5 前端运营助手

- [ ] 聊天窗口（消息气泡、loading、错误提示）
- [ ] 渲染 `cards`（订单表 / 商品卡片）
- [ ] 会话列表 + 切换续聊
- [ ] 与「模型配置」页联动：提示当前 mock / 模型名

### P1-6 演示与文档

- [ ] 准备 3 条固定话术与期望结果
- [ ] 更新 [demo-script.md](./demo-script.md)
- [ ] README 进度表 Phase 1 勾选

---

## 8. 演示话术（验收）

| # | 用户输入 | 期望 |
|---|----------|------|
| 1 | 查一下待发货订单 | 返回真实/联调订单 cards，intent=`query_order` |
| 2 | 智能音箱还有库存吗 | 商品 + 库存信息，intent=`query_product` |
| 3 | 今天运营情况怎么样 | 文本摘要（订单量/待发货/预警等），intent=`ops_summary` |
| 4 | 刷新页面后点同一会话 | 历史消息仍在 |

关闭 Mock 且配置有效 Chat Key 后：回复语气由模型生成，但 **cards 必须来自 Tool 真实结果**。

---

## 9. 验收标准

- [ ] 管理端登录后 **AI中心 → 运营助手** 可多轮对话
- [ ] 至少 2 个 Tool 打到真实 OMS/PMS（非写死 DEMO 单号）
- [ ] 会话与消息写入 MySQL，重启服务后可查
- [ ] `mock_enabled=1` 无 Key 可演示；`=0` 且配置 Key 后可走模型
- [ ] 无 500：Feign/LLM 失败有业务错误码与中文提示
- [ ] 一键启动路径不变：`PlatformLauncher` + Nacos + 前端 dev

---

## 10. 风险与对策

| 风险 | 对策 |
|------|------|
| Feign 鉴权 / 内部调用拿不到用户上下文 | 优先复用网关 TokenRelay；必要时 mall-ai 内服务账号 + 只读接口白名单（谨慎） |
| LLM Tool Calling 不稳定 | 保留规则意图兜底；Tool 结果优先展示 cards |
| OMS 依赖复杂 DTO | Phase1 只接管理端分页查询，字段最小化 |
| 无 RabbitMQ 时订单超时关单不可用 | 管理端查询不受影响；关单属交易链路，不在 Phase1 |

---

## 11. 交付物

1. 代码：mall-ai 会话 + Tool +（可选）LLM；admin 运营助手页  
2. 文档：本文 + demo-script 更新  
3. 演示：3 条话术录屏或答辩口述路径  

---

## 12. 下一步（Phase 2 预告）

- LightRAG 服务启动与文档入库  
- 知识库上传 / 提问页接真实检索  
- Embedding 默认 `nvidia/llama-nemotron-embed-1b-v2`（配置页已预留）
