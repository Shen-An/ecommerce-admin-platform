# 演示一页纸（6 分钟）

> 评委/导师速览：「底座 + AI 四场景 + 工程点」。  
> 账号 `admin / 123456` · 前端 `http://localhost:9527` · 网关 `9999`

## 启动

```bat
scripts\start-nacos.bat
scripts\start-backend.bat
cd admin && pnpm run dev
```

真向量 RAG（可选）：**模型配置** 填 Embedding → 知识库 **重建 Java 向量索引**（无需 Python）。

## 时间线

| 时间 | 页面 | 操作 | 卖点 |
|------|------|------|------|
| 0:00 | 登录 | admin | youlai-mall 二次开发 |
| 0:40 | 商品/订单 | 扫列表 | PMS/OMS 真实数据 |
| 1:20 | 运营助手 | 待发货订单 | Tool→真实 OMS cards |
| 1:45 | 运营助手 | 智能音箱库存 | PMS |
| 2:05 | 运营助手 | 今天运营情况 | 状态计数+库存预警 |
| 2:25 | 运营助手 | 刷新→原会话 | 会话落库可审计 |
| 2:45 | 知识库 | 灌入语料→7 天无理由 | Java RAG 或 local 降级 |
| 3:20 | 知识库 | （有 Key）重建向量 | `source=java_rag`，Key 统一配置 |
| 3:40 | 工单Agent | 物流+起诉 | 四步 Agent → escalated |
| 4:15 | 工单Agent | 人工接管 | 状态机+流转日志 |
| 4:40 | 数据洞察 | 销量 Top5 → 状态分布 | 白名单模板，无 SQL |
| 5:05 | 数据洞察 | 品类 / GMV / 综合看板 | 更细指标，Feign 只读 |
| 5:35 | 收尾 | 架构一句 | 微服务 + 朴素 RAG + 多 Agent |

## 洞察快捷语（白名单）

| 话术 | template |
|------|----------|
| 近 7 天商品销量 Top5 | `sales_topn` |
| 品类销量分布 | `category_sales` |
| GMV 成交额快照 | `gmv_snapshot` |
| 订单状态分布怎么样 | `order_status_dist` |
| 库存预警有哪些商品 | `low_stock` |
| 取消和售后占比如何 | `refund_rate` |
| 今天运营综合看板 | `ops_dashboard` |

## 工程亮点（15s）

1. **鉴权**：AI 走网关 JWT  
2. **只读 Tool / 白名单洞察**：无任意 SQL、无写工具  
3. **降级**：无 Chat → Mock；无 Embedding → 关键词  
4. **配置外置**：Chat/Embedding 均在管理端模型配置  

## 故障快修

| 现象 | 处理 |
|------|------|
| 无工单/洞察菜单 | 重登；或执行 `init-ai-menu.sql` |
| jar rename 失败 | 停 :8805 后再打包启动 |
| 助手无订单 | oms:8803 + 库有订单 |
| 知识库全 local | 正常；填 Embedding 后重建索引 |

详见 [demo-script.md](demo-script.md) · [phase2.md](phase2.md) · [phase4.md](phase4.md)
