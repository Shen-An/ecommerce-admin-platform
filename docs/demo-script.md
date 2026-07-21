# 6 分钟演示脚本（MVP 目标）

> Phase0–3 已完成：底座 / 运营助手 / 知识库 / 工单 Agent  
> **Phase4 已完成**：数据洞察（白名单 + ECharts）

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

## 3. 知识库 Java RAG（90s）— **Phase2 可演示**

1. 打开 **AI中心 → 知识库**（主路径纯 Java，无需 Python LightRAG）
2. 点击 **灌入演示语料**（售后政策 + 运营 SOP）
3. 提问：**「7 天无理由退货怎么处理？」**
   - 未配 Embedding：`source=local` 关键词，仍含 7 日无理由要点
   - 模型配置已填 Embedding 并点 **重建 Java 向量索引**：`source=java_rag` + 引用 score
4. 再问：**「每日开店要检查哪些事项？」** → 命中运营 SOP
5. （可选）文本入库 / 上传 md 文件

## 4. 工单 Agent（90s）— **当前可演示**

1. 打开 **AI中心 → 工单Agent**（建议先在知识库灌入演示语料）
2. 快捷语：**「订单号 202401150001 物流停滞三天了，还说要起诉你们」**
   - 期望：四步 steps（Intent / PolicyRAG / Escalation / Ticket），敏感词触发 **escalated**
3. 右侧工单列表出现记录；点开看流转日志
4. 点击 **人工接管** → 日志 `human_takeover`
5. （可选）换退款/咨询话术，对比不升级 `open` 场景

## 5. 数据洞察（60s）— **当前可演示**

1. 打开 **AI中心 → 数据洞察**
2. 快捷语：**「近 7 天商品销量 Top5」** → bar 图 + 解读；template=`sales_topn`
3. 再问：**「订单状态分布怎么样」** → pie 图
4. 强调页面 **whitelist** 标签：仅白名单模板 + Feign，无任意 SQL

## 6. 收尾（30s）

1. 架构图：Java 微服务 + Python LightRAG + 多 Agent
2. 工程点：鉴权、会话审计、配置外置、降级策略
3. 一页纸速查：[demo-onepager.md](demo-onepager.md)

## 真向量 RAG（可选加时 30s）

1. **模型配置** 填 Embedding Key（NVIDIA/通义兼容），可选 Chat Key  
2. 知识库 → **重建 Java 向量索引**  
3. 再问「7 天无理由」→ `source=java_rag`

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

## Phase2 检查清单

- [x] 灌入演示语料
- [x] 本地关键词降级问答可用
- [x] 文档列表 / 删除
- [x] Java 向量表 `ai_knowledge_chunk` + 重建索引 API/UI
- [ ] （环境）模型配置填 Embedding 后 `source=java_rag`

## Phase3 / 4 速查

- 工单：投诉+起诉 → escalated；人工接管
- 洞察：销量 Top5 / 品类 / GMV 抽样 / 状态分布；whitelist
