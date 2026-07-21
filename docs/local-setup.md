# 本机中间件启动（无 Docker）

你的环境：

| 组件 | 状态 | 连接信息 |
|------|------|----------|
| MySQL 8.0.26 | 已运行服务 `MySQL8` | `localhost:3306` root / `123456` |
| Redis 8.2.0 | 已运行服务 `Redis` | `localhost:6379` **无密码** |
| Nacos 2.2+ | 需单独安装启动 | `http://localhost:8848/nacos` nacos/nacos |

业务库与 AI 库 **已导入**（`youlai_system` / `oauth2_server` / `mall_*` / `nacos_config` / `mall_ai`）。

## 1. Redis（无密码）

```bat
D:\Coding\JavaWebTools\Redis-8.2.0\redis-cli.exe ping
:: 期望 PONG
```

Nacos 共享配置 `youlai-common.yaml` 中 `redis.password` 必须为空（项目已改好模板）。

## 2. MySQL

```bat
D:\tools\MySQL\bin\mysql.exe -uroot -p123456 -e "SHOW DATABASES;"
```

应能看到：`youlai_system`、`mall_oms`、`mall_pms`、`mall_ai` 等。

若需重导 AI 表：

```bat
D:\tools\MySQL\bin\mysql.exe -uroot -p123456 < docker\mysql\init-ai.sql
```

## 3. 安装并启动 Nacos（仅缺这一项）

### 已准备

- 已下载并解压到：`D:\tools\nacos`（2.2.3）
- 已配置 MySQL 存储：`nacos_config` / root / 123456
- 启动脚本：`scripts/start-nacos.bat`
- 配置导入：`python scripts/import-nacos-config.py --also-original`

### 启动

```bat
scripts\start-nacos.bat
```

控制台：http://localhost:8848/nacos （nacos / nacos）

若 Git Bash 下官方 `startup.sh` 因路径转换失败，请始终用上面的 bat，或：

```bash
export MSYS_NO_PATHCONV=1
# 见 scripts/start-nacos.bat 中的 java 命令
```

### 导入配置

```bash
python scripts/import-nacos-config.py --also-original
```

会发布：

- 原厂：auth/system/pms/oms/sms/ums 等
- 本项目覆盖：`youlai-common.yaml`（**Redis 无密码**）、`youlai-gateway.yaml`（含 `/mall-ai/**` `/mall-pms/**` 等）、`mall-ai.yaml`、`mall-pms.yaml`（本地关 RabbitMQ/Seata）

确认 `youlai-common.yaml`：

```yaml
redis.password:   # 空
mysql.password: 123456
```
## 4. 启动后端（推荐：一个主启动类）

### 方式 A：IDEA 一键（推荐）

1. 打开类：[`backend/dev-launcher/.../PlatformLauncher.java`](../backend/dev-launcher/src/main/java/com/youlai/dev/PlatformLauncher.java)
2. 右键 → **Run 'PlatformLauncher.main()'**  
   - 首次或改代码后：Run Configuration 程序实参加 `--build`
   - 或使用运行配置：**PlatformLauncher (一键启动)**（已带 `--build`）
3. 会依次启动：
   - gateway **9999**
   - auth **9000**（验证码依赖它）
   - system **8800**
   - mall-ai **8805**
   - mall-pms **8802**（商品管理）
   - mall-ums **8801**（会员列表）
   - mall-oms **8803**（订单列表）
   - mall-sms **8804**（营销管理）
4. 日志目录：`backend/logs/launcher/*.log`
5. Ctrl+C / 停止运行配置 → 子进程一并关掉

可选参数：

| 参数 | 含义 |
|------|------|
| `--build` | 启动前 Maven package |
| `--skip-ai` / `--skip-pms` / `--skip-ums` / `--skip-oms` / `--skip-sms` | 跳过对应服务 |

### 方式 B：脚本

```bat
scripts\start-backend.bat
scripts\stop-backend.bat
```

### 方式 C：仍可分开点

1. `GatewayApplication`（9999）  
2. `AuthApplication`（9000）  
3. `SystemApplication`（8800）  
4. `AiApplication`（8805）  
5. `PmsApplication`（8802）  
6. `UmsApplication`（8801）  
7. `OmsApplication`（8803）  
8. `SmsApplication`（8804）  

验证：

```bash
curl http://localhost:9999/youlai-auth/api/v1/auth/captcha
curl http://localhost:8805/api/v1/ai/health
curl "http://localhost:9999/mall-pms/api/v1/brands/page?pageNum=1&pageSize=10"
curl "http://localhost:9999/mall-ums/api/v1/members?pageNum=1&pageSize=10"
curl "http://localhost:9999/mall-oms/api/v1/orders?pageNum=1&pageSize=10"
curl "http://localhost:9999/mall-sms/api/v1/coupons/page?pageNum=1&pageSize=10"
```

## 5. 启动前端

```bash
cd admin
pnpm install
pnpm run dev
# http://localhost:9527  admin/123456
```

登录后侧边栏应出现 **AI中心**（动态菜单，来自 `sys_menu`）。若没有，重新执行：

```bat
D:\tools\MySQL\bin\mysql.exe -uroot -p123456 --default-character-set=utf8mb4 < docker\mysql\init-ai-menu.sql
```

然后 **退出重新登录** 刷新路由。

### AI 模型配置页

路径：**AI中心 → 模型配置**（`/ai/settings`）

- 对话模型：默认通义 `qwen-plus`（DashScope OpenAI 兼容）
- Embedding：默认 **`nvidia/llama-nemotron-embed-1b-v2`**（维度 2048，Base `https://integrate.api.nvidia.com/v1`）
- API Key 脱敏展示；表单留空表示不修改已有密钥
- 可分别测试 Chat / Embedding / LightRAG 连通性

接口（经网关）：

```text
GET  /mall-ai/api/v1/ai/settings
PUT  /mall-ai/api/v1/ai/settings
POST /mall-ai/api/v1/ai/settings/test?type=embedding|chat|lightrag
```

配置表：`mall_ai.ai_model_config`（初始化见 `docker/mysql/init-ai.sql` / `init-ai-model-config.sql`）。

## 6. LightRAG + NVIDIA Embedding（Phase 2）

见 [services/lightrag/README.md](../services/lightrag/README.md)。  
Chat 用国内 API，**Embedding 单独走 NVIDIA**；密钥与模型名优先读前端「模型配置」落库结果。

## 常见问题

**Redis 连接失败 / AUTH 错误**  
本机 Redis **无密码**。Nacos `youlai-common.yaml` 必须是：

```yaml
redis:
  password: null   # 不要写 password: 空串，否则 Redisson 会发 AUTH 失败
```

已提供兜底：`common-redis` 会把空串密码规范为 `null`。改完配置后执行  
`python scripts/import-nacos-config.py` 并 **Rebuild + 重启** 各服务。

**Nacos 连不上 MySQL**  
确认 `nacos_config` 库已存在（本项目 SQL 已建），`allowPublicKeyRetrieval=true`，密码 123456。

**RabbitMQ / Seata 报错**  
商品/订单服务本地默认 `app.rabbitmq.enabled=false` 且排除 `RabbitAutoConfiguration`，`seata.enabled=false`。管理端列表页不依赖 MQ。

**订单/会员/营销页面系统错误**  
需要对应服务已注册：`mall-oms:8803`、`mall-ums:8801`、`mall-sms:8804`，网关含 `/mall-oms/**` `/mall-ums/**` `/mall-sms/**`。改配置后执行 `python scripts/import-nacos-config.py` 并启动/重启这些服务。

**商品管理四个页面系统错误**  
需要：1）Nacos 网关含 `/mall-pms/**` 路由；2）`mall-pms` 已注册且端口 8802 在听。改路由后执行 `python scripts/import-nacos-config.py` 并重启 gateway + pms。
