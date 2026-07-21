# 6 分钟演示脚本（MVP 目标）

> Phase0：底座登录 + AI 健康检查 + 助手骨架  
> **Phase1 已完成**：运营助手 Tool（OMS/PMS）+ 会话落库 + 多轮 UI  
> Phase2–4：知识库 / 工单 / 洞察

## 0. 环境（30s）

1. MySQL / Redis / Nacos 已启动（可不强制 Docker）
2. `scripts\start-backend.bat` 或 IDEA 运行 `PlatformLauncher`
3. 前端 `cd admin && pnpm run dev` → `http://localhost:9527`（admin/123456）

## 1. 商城底座（60s）

1. 登录 Admin
2. 浏览商品管理、订单管理列表（确认 PMS/OMS 有数据）
3. 说明：基于 youlai-mall 二次开发

## 2. 运营助手 Phase1（90s）— **当前可演示**

1. 打开 **AI中心 → 运营助手**
2. 快捷语或输入：**「查一下待发货订单」**
   - 期望：`intent=query_order`，表格 cards 来自 **真实 OMS**（非 DEMO 单号）
3. 输入：**「智能音箱还有库存吗」**（或库内真实商品名）
   - 期望：`intent=query_product`，商品名/价格/库存 cards 来自 **PMS**
4. 输入：**「今天运营情况怎么样」**
   - 期望：`intent=ops_summary`，分状态订单计数 + 库存预警
5. **刷新页面** → 左侧会话列表点同一会话 → 历史消息仍在
6. （可选）「模型配置」关闭 Mock 并填 Chat Key → 回复语气由 LLM 润色，**cards 仍来自 Tool**

## 3. LightRAG 知识库（90s）— Phase2

1. 上传《售后政策》PDF/MD
2. 提问：「7 天无理由退货怎么处理？」
3. 展示答案 + 引用来源

## 4. 工单 Agent（90s）— Phase3

1. 输入投诉话术（含订单号）
2. 展示 Agent 步骤：意图 → 政策 → 建单
3. 点击「人工接管」，状态变为 escalated

## 5. 数据洞察（60s）— Phase4

1. 输入：「近 7 天品类销量 Top5」
2. 展示图表 + 解读文案
3. 强调白名单查询，无 SQL 注入风险

## 6. 收尾（30s）

1. 架构图：Java 微服务 + Python LightRAG + 多 Agent
2. 工程点：鉴权、会话审计、配置外置、降级策略

## Phase1 验收话术速查

| # | 用户输入 | 期望 |
|---|----------|------|
| 1 | 查一下待发货订单 | 真实订单 cards，intent=`query_order` |
| 2 | 智能音箱还有库存吗 | 商品+库存，intent=`query_product` |
| 3 | 今天运营情况怎么样 | 文本摘要+统计 card，intent=`ops_summary` |
| 4 | 刷新后点同一会话 | 历史消息仍在 |

## Phase0 检查清单

- [x] `GET /api/v1/ai/health` 返回 UP
- [x] Admin 可登录
- [x] 一键启动路径可用

## Phase1 检查清单

- [x] 会话/消息写入 `ai_session` / `ai_message`
- [x] `queryOrders` / `queryProducts` / `summarizeOpsDaily` Feign 只读
- [x] 前端多轮 UI + cards + 会话列表
- [x] mock=1 无 Key 可演示；配置 Key 后可润色
