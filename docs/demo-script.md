# 6 分钟演示脚本（MVP 目标）

> Phase0–4 已完成：底座 / 运营助手 / **Java 朴素 RAG** / 工单 Agent / 数据洞察  
> Key 统一走 **模型配置页**；Python LightRAG **非必需**

## 0. 环境（30s）

1. MySQL / Redis / Nacos 已启动（可不强制 Docker）
2. `scripts\start-backend.bat` 或 IDEA 运行 `PlatformLauncher`
3. 前端 `cd admin && pnpm run dev` → `http://localhost:9527`（admin/123456）

## 1. 商城底座（50s）

1. 登录 Admin
2. 浏览商品管理、订单管理列表（确认 PMS/OMS 有数据）
3. 说明：基于 youlai-mall 二次开发

## 2. 运营助手 Phase1（80s）

1. 打开 **AI中心 → 运营助手**
2. 快捷语：**「查一下待发货订单」**
   - 期望：`intent=query_order`，表格 cards 来自 **真实 OMS**（非 DEMO 单号）
3. **「智能音箱还有库存吗」**（或库内真实商品名）
   - 期望：`intent=query_product`，PMS 商品/价格/库存
4. **「今天运营情况怎么样」**
   - 期望：`intent=ops_summary`，分状态订单计数 + 库存预警
5. **刷新页面** → 左侧点同一会话 → 历史消息仍在
6. （可选）模型配置关闭 Mock 并填 Chat Key → 语气润色，**cards 仍来自 Tool**

## 3. 知识库 Java RAG Phase2（80s）

1. 打开 **AI中心 → 知识库**（纯 Java 朴素 RAG，无需 Python）
2. **灌入演示语料**（售后政策 + 运营 SOP）
3. **「7 天无理由退货怎么处理？」**
   - 无 Embedding：`source=local` 关键词，仍含 7 日要点
   - 有 Embedding 且点过 **重建 Java 向量索引**：`source=java_rag` + 引用 score
4. **「每日开店要检查哪些事项？」** → 运营 SOP
5. （可选加时）模型配置填 Embedding → 重建索引 → 再问同一句强调 `java_rag`

## 4. 工单 Agent Phase3（80s）

1. 打开 **AI中心 → 工单Agent**（建议先灌过知识库）
2. 快捷语：**「订单号 202401150001 物流停滞三天了，还说要起诉你们」**
   - 四步：Intent → PolicyRAG → Escalation → Ticket；敏感词 → **escalated**
3. 右侧工单列表 → 点开流转日志
4. **人工接管** → 日志 `human_takeover`
5. （可选）普通退款/咨询 → 对比 `open` 不升级

## 5. 数据洞察 Phase4（70s）

1. 打开 **AI中心 → 数据洞察**（标签：**白名单 · 无 SQL 注入**）
2. 按序点快捷语（每条 1 图 + 解读）：

| 快捷语 | template | 图类型（示意） |
|--------|----------|----------------|
| 近 7 天商品销量 Top5 | `sales_topn` | bar |
| 品类销量分布 | `category_sales` | bar/pie |
| GMV 成交额快照 | `gmv_snapshot` | 指标/柱 |
| 订单状态分布怎么样 | `order_status_dist` | pie |
| 库存预警有哪些商品 | `low_stock` | table/bar |
| 取消和售后占比如何 | `refund_rate` | pie |
| 今天运营综合看板 | `ops_dashboard` | 多图/组合 |

3. 口播：QueryPlanner **只映射白名单** → Feign 只读 OMS/PMS → ECharts option；**禁止任意 SQL**

## 6. 收尾（20s）

1. 架构：Java 微服务 + **Java 朴素 RAG** + 多 Agent + 白名单洞察  
2. 工程点：网关鉴权、会话/工单/洞察落库审计、配置外置、无 Key 可降级  
3. 一页纸：[demo-onepager.md](demo-onepager.md)

---

## Phase1 验收话术

| # | 用户输入 | 期望 |
|---|----------|------|
| 1 | 查一下待发货订单 | 真实订单 cards，`query_order` |
| 2 | 智能音箱还有库存吗 | 商品+库存，`query_product` |
| 3 | 今天运营情况怎么样 | 摘要+统计，`ops_summary` |
| 4 | 刷新后点同一会话 | 历史消息仍在 |

## 检查清单

### Phase0
- [x] `GET /api/v1/ai/health` UP
- [x] Admin 可登录
- [x] 一键启动路径可用

### Phase1
- [x] `ai_session` / `ai_message`
- [x] Feign 只读 OMS/PMS
- [x] 多轮 UI + cards + 会话列表
- [x] mock 可演示；配 Chat Key 可润色

### Phase2
- [x] 灌入演示语料
- [x] 关键词降级 `source=local`
- [x] 文档列表 / 删除
- [x] `ai_knowledge_chunk` + 重建 Java 向量索引
- [ ] （环境）Embedding Key 后 `source=java_rag`

### Phase3
- [x] 四步 Agent 流水线
- [x] 投诉+起诉 → escalated；人工接管

### Phase4
- [x] 7 个白名单模板（销量/品类/GMV/状态/库存/退款占比/综合看板）
- [x] ECharts + 解读 + `ai_insight_query` 落库
