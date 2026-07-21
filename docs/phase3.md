# Phase 3：客服/工单多 Agent

## 目标

Sequential 流水线处理客诉话术：

1. **IntentAgent** — 投诉 / 退款 / 物流 / 咨询 + 订单号抽取  
2. **PolicyRagAgent** — 复用 Phase2 知识库问答（LightRAG 或本地降级）  
3. **EscalationGate** — 低置信度 / 高优投诉 / 敏感词 → `escalated`  
4. **TicketAgent** — 写入 `ai_ticket` + `ai_ticket_log`

前端展示 Agent 步骤时间线、工单列表、人工接管 / 关闭。

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/ai/ticket/chat` | 对话建单 |
| GET | `/api/v1/ai/ticket/list?status=` | 工单列表 |
| GET | `/api/v1/ai/ticket/{id}` | 详情 + 日志 |
| POST | `/api/v1/ai/ticket/{id}/escalate` | 人工接管 |
| POST | `/api/v1/ai/ticket/{id}/close` | 关闭 |
| GET | `/api/v1/ai/ticket/sessions` | 会话列表 |
| GET | `/api/v1/ai/ticket/sessions/{id}/messages` | 历史消息 |

网关前缀：`/mall-ai/...`（与助手一致）。

## 状态机

`open` → `escalated`（自动或人工）→ `closed`  
处理人：`ai_bot` / `human_queue` / `admin:{userId}`

## 演示

1. 知识库先「灌入演示语料」（政策命中更好）  
2. AI中心 → **工单Agent**  
3. 快捷语：`订单号 202401150001 物流停滞…起诉…`  
   - 期望：intent=logistics 或 complaint，steps 四步齐全，敏感词触发 escalated  
4. 点 **人工接管** → 日志 `human_takeover`，状态 escalated  
5. 再测退款 / 咨询话术，对比不升级场景

## 代码位置

- Agents：`backend/mall-ai/.../agent/*`  
- Service：`TicketServiceImpl`  
- 前端：`admin/src/views/ai/ticket/index.vue`  
- 菜单：`docker/mysql/init-ai-menu.sql` id=204

## 验收清单

- [x] 建单写入 `ai_ticket` / `ai_ticket_log`  
- [x] 四步 Agent 时间线返回  
- [x] 政策检索走 KnowledgeService  
- [x] 人工接管 / 关闭  
- [x] 管理端菜单与 UI  
