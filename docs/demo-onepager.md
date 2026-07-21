# 演示一页纸（6 分钟）

> 目标：评委/导师 6 分钟看清「底座 + AI 四场景 + 工程点」。  
> 账号：`admin / 123456` · 前端 `http://localhost:9527` · 网关 `9999`

## 启动

```bat
scripts\start-nacos.bat
scripts\start-backend.bat
cd admin && pnpm run dev
```

可选真 RAG：`scripts\start-lightrag.bat`（需 `services/lightrag/.env` 填 Key）

## 话术时间线

| 时间 | 页面 | 操作 | 一句话卖点 |
|------|------|------|------------|
| 0:00 | 登录 | admin 登录 | youlai-mall 二次开发底座 |
| 0:40 | 商品/订单 | 扫一眼列表 | PMS/OMS 有真实数据 |
| 1:20 | 运营助手 | 「查一下待发货订单」 | Tool→真实 OMS cards，非假单号 |
| 1:50 | 运营助手 | 「智能音箱还有库存吗」 | PMS 库存 |
| 2:10 | 运营助手 | 「今天运营情况怎么样」 | 状态计数 + 库存预警 |
| 2:30 | 运营助手 | 刷新 → 点原会话 | 会话落库可审计 |
| 2:50 | 知识库 | 灌入演示语料 → 「7 天无理由」 | local 或 lightrag 双模式 |
| 3:30 | 知识库 | （若 LightRAG UP）同步索引 | 真图向量 / 本地降级都可讲 |
| 3:50 | 工单Agent | 物流+起诉投诉语 | 四步 Agent + escalated |
| 4:30 | 工单Agent | 人工接管 | 状态机 + 流转日志 |
| 5:00 | 数据洞察 | 销量 Top5 / GMV / 品类 | 白名单模板，无 SQL 注入 |
| 5:40 | 收尾 | 架构一句 | 微服务 + LightRAG + 多 Agent |

## 工程亮点（口播 15s）

1. **鉴权**：AI API 走网关 JWT；健康检查可放行  
2. **只读 Tool / 白名单洞察**：禁止任意 SQL、无写工具  
3. **降级**：无 LLM Key → Mock；无 LightRAG → 本地关键词  
4. **配置外置**：模型 Key / LightRAG URL 管理端可改  

## 故障快修

| 现象 | 处理 |
|------|------|
| 菜单无「工单/洞察」 | 重登；或执行 `init-ai-menu.sql` 菜单段 |
| jar 打包 rename 失败 | `stop-backend` 杀端口后 `--build` |
| 助手无订单 | 确认 oms:8803 + 库有订单数据 |
| 知识库全 local | 正常；讲降级；有 Key 再启 LightRAG |

详见 [demo-script.md](demo-script.md) · [phase3.md](phase3.md) · [phase4.md](phase4.md)
