# Phase 4：数据洞察 Agent

## 目标

自然语言 → **QueryPlanner 白名单模板** → Feign 只读聚合（OMS/PMS）→ **ECharts option + 解读文案**。

**安全红线**：不执行模型生成的任意 SQL；模板与参数均服务端白名单。

## 白名单模板

| Code | 说明 | 数据来源 |
|------|------|----------|
| `sales_topn` | 商品销量 TopN | PMS SPU 分页 sales 排序 |
| `order_status_dist` | 订单状态分布 | OMS 分状态 total |
| `low_stock` | 库存预警 | PMS SKU stock 抽样 |
| `refund_rate` | 取消+售后占比 | OMS 状态聚合（近似） |
| `category_sales` | 品类销量分布 | PMS SPU 按 categoryName 聚合销量 |
| `gmv_snapshot` | GMV 抽样快照 | OMS 首页订单 paymentAmount 抽样+外推 |
| `ops_dashboard` | 运营综合看板 | 订单分布 + 低库存 + 热销/抽样 GMV |

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/ai/insight/query` | 提问并返回 option/narrative |
| GET | `/api/v1/ai/insight/history` | 当前用户历史 |
| GET | `/api/v1/ai/insight/templates` | 模板列表 |

网关：`/mall-ai/api/v1/ai/insight/**`。  
落库：`ai_insight_query`（question / plan_json / result_json）。

## 演示

1. AI中心 → **数据洞察**（标签 **白名单 · 无 SQL 注入**）
2. 建议顺序（均有前端快捷语）：
   - **「近 7 天商品销量 Top5」** → `sales_topn` bar
   - **「品类销量分布」** → `category_sales`
   - **「GMV 成交额快照」** → `gmv_snapshot`
   - **「订单状态分布怎么样」** → `order_status_dist` pie
   - **「库存预警有哪些商品」** → `low_stock`
   - **「取消和售后占比如何」** → `refund_rate`
   - **「今天运营综合看板」** → `ops_dashboard`
3. 口播：Planner 只映射白名单 code → Feign 只读 → ECharts option + narrative

## 代码

- `insight/InsightQueryPlanner` · `InsightQueryExecutor`
- `InsightServiceImpl` · `InsightController`
- 前端：`admin/src/views/ai/insight/index.vue`（echarts）
- 菜单 id=205

## 验收

- [x] 规划器只映射白名单 code
- [x] 执行层仅 Feign 只读
- [x] 返回 ECharts option + narrative
- [x] 查询写入 `ai_insight_query`
- [x] 管理端 UI + 菜单
