# 6 分钟演示脚本（MVP 目标）

> Phase0 完成后可演示底座登录 + AI 健康检查 + 助手 Mock。  
> 完整 4 场景在 Phase1–4 完成后按本脚本录制。

## 0. 环境（30s）

1. 展示 `docker compose ps`：MySQL/Redis/Nacos/MinIO Up
2. 打开 Nacos、MinIO 控制台
3. 打开网关文档 `http://localhost:9999/doc.html`

## 1. 商城底座（60s）

1. 登录 Admin：`admin / 123456`
2. 浏览商品管理、订单管理列表
3. 说明：基于 youlai-mall 二次开发，非从零造轮子

## 2. 运营助手（90s）

1. 打开「AI 运营助手」
2. 输入：「帮我看看未发货订单」
3. 展示结构化订单卡片 + 自然语言回复
4. 输入：「今日运营摘要」

## 3. LightRAG 知识库（90s）

1. 上传《售后政策》PDF/MD
2. 提问：「7 天无理由退货怎么处理？」
3. 展示答案 + 引用来源

## 4. 工单 Agent（90s）

1. 输入投诉话术（含订单号）
2. 展示 Agent 步骤：意图 → 政策 → 建单
3. 点击「人工接管」，状态变为 escalated

## 5. 数据洞察（60s）

1. 输入：「近 7 天品类销量 Top5」
2. 展示图表 + 解读文案
3. 强调白名单查询，无 SQL 注入风险

## 6. 收尾（30s）

1. 架构图：Java 微服务 + Python LightRAG + 多 Agent
2. 工程点：鉴权、会话审计、配置外置、降级策略

## Phase0 可演示检查清单

- [ ] `GET /api/v1/ai/health` 返回 UP
- [ ] `POST /api/v1/ai/assistant/chat` 对「订单」返回 query_order
- [ ] Admin 可登录
- [ ] README 可按步骤启动
