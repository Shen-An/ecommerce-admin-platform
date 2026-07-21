# 企业级电商管理平台（AI 增强版）

基于开源微服务商城 [youlai-mall](https://github.com/youlaitech/youlai-mall) + [mall-admin](https://github.com/youlaitech/mall-admin) 二次开发，增量交付：

- **运营智能助手**（Tool Calling 查订单/商品）
- **LightRAG 知识库问答**（图 + 向量混合检索）
- **客服/工单多智能体**（意图 → 政策 RAG → 建单 → 人工接管）
- **数据洞察 Agent**（白名单聚合 + 图表解读）

> 技术栈：Java 17 · Spring Boot 3 · Spring Cloud Alibaba · Vue3 · Element Plus · Spring AI Alibaba · LightRAG · Docker

## 目录结构

```text
ecommerce-admin-platform/
├── admin/                 # Vue3 管理前端（mall-admin）
├── backend/               # 微服务后端（youlai-mall + mall-ai）
│   └── mall-ai/           # ★ 新增 AI 微服务
├── docker/                # 中间件配置与初始化
├── docs/                  # 架构 / 演示 / AI 设计
├── services/lightrag/     # LightRAG 数据目录
├── docker-compose.yml     # MySQL / Redis / Nacos / MinIO
└── .env.example           # 密钥模板
```

## 快速开始（Phase 0）

### 1. 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 17+（本机可用 21 编译，目标字节码 17） |
| Maven | 3.8+ |
| Node | 16+ |
| pnpm | 8+ |
| MySQL | 8.x（本机已有即可） |
| Redis | 本机已有即可 |
| Nacos | 2.2+ 单机 |

> **不强制 Docker。** 本机 MySQL/Redis 可直接用。详见 [docs/local-setup.md](docs/local-setup.md)。

### 2. 本机中间件（推荐路径）

当前约定：

- MySQL：`root / 123456`
- Redis：**无密码**
- Nacos：`D:\tools\nacos`，启动脚本 `scripts/start-nacos.bat`

```bash
# 业务库 + AI 库（已可执行；重复执行注意幂等）
# D:\tools\MySQL\bin\mysql.exe -uroot -p123456 < backend/docs/sql/mysql8/database.sql
# ... 其余 sql 见 docs/local-setup.md

# 启动 Nacos（Windows）
scripts\start-nacos.bat

# 导入配置（含 Redis 空密码、mall-ai 路由）
bash scripts/import-nacos-config.sh
# 另需导入原厂 zip 中其它服务配置（auth/system/pms...），见 local-setup
```

### 3. 启动后端核心服务

```bash
cd backend
mvn -pl youlai-common,youlai-gateway,youlai-auth,youlai-system,mall-ai -am -DskipTests package
```

IDEA 启动顺序：Gateway → Auth → System → **AiApplication(8805)** →（可选）PMS/OMS。

- 文档：`http://localhost:9999/doc.html`
- AI 健康检查：`http://localhost:8805/api/v1/ai/health`

### 4. 启动管理前端

```bash
cd admin
pnpm install
pnpm run dev
# http://localhost:9527  admin/123456
```

### 5. LightRAG + NVIDIA Embedding（Phase 2）

Chat LLM 用国内 API；**Embedding 接英伟达**。说明见 [services/lightrag/README.md](services/lightrag/README.md)。

## AI 模块进度

| 阶段 | 内容 | 状态 |
|------|------|------|
| Phase 0 | 底座 + Docker + mall-ai 骨架 | ✅ 进行中 |
| Phase 1 | 运营助手 + Tool + 会话 | ⏳ |
| Phase 2 | LightRAG 知识库 | ⏳ |
| Phase 3 | 工单多 Agent | ⏳ |
| Phase 4 | 数据洞察 + 打磨 | ⏳ |

当前 `POST /api/v1/ai/assistant/chat` 已提供**规则路由 Mock**，便于前后端联调；接入 DashScope 后关闭 `ai.llm.mock-enabled`。

## 文档

- [架构说明](docs/architecture.md)
- [AI 设计](docs/ai-design.md)
- [演示脚本](docs/demo-script.md)

## 许可证

- 底座 youlai-mall / mall-admin：Apache-2.0
- 本仓库增量代码：Apache-2.0
