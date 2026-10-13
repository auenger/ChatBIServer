# MEPER ChatBI Docker 部署包

目标平台：Linux x86_64（`linux/amd64`），Docker Engine + Docker Compose v2。
包内包含前后端镜像 `images.tar`、`compose.yaml`、`.env.example` 和镜像清单。
控制库使用已有 MySQL 服务，数据库数据、业务库、主密钥和真实凭据不包含在部署包中。

## 部署

在解压目录执行：

```bash
sha256sum -c SHA256SUMS
docker load -i images.tar
cp .env.example .env
chmod 600 .env
# 编辑 .env：控制库地址、用户名/密码、主密钥、管理员密码。
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --wait
docker compose ps
```

浏览器访问 `http://服务器地址:8001`（端口由 `MEPER_HTTP_PORT` 配置）。
前端 Nginx 提供 SPA 路由及 `/api` 同源代理；AI 对话 SSE 已关闭代理缓存与压缩。
后端只在容器网络内提供 8080，不单独开放宿主机 API 端口。
健康检查：`curl http://localhost:8001/healthz`；业务探活：`curl http://localhost:8001/api/ping`。

控制库必须可从后端容器访问，启动时 Flyway 自动检查并执行所需迁移。
若 MySQL 位于同一宿主机，可将 JDBC 主机填为 `host.docker.internal`；不要写 `localhost`。
TLS 模式按目标数据库配置显式设置，连接失败不会自动降级 TLS。

**复用已有控制库时必须沿用原 `MEPER_MASTER_KEY`**，否则已有数据源密码与模型 API Key 无法解密。
新安装可用 `openssl rand -base64 32` 生成主密钥，并将其固定保存；升级时保留 `.env`。

## 停止与升级

```bash
docker compose stop
# 新版本镜像导入后，保留原 .env 并更新 MEPER_IMAGE_TAG：
docker load -i images.tar
docker compose --env-file .env up -d --wait
docker compose logs --tail=100 backend frontend
```

`compose stop` 不删除容器；此部署配置不管理或删除外部数据库。

## 从源码重新构建镜像

在仓库根目录执行：

```bash
mvn -B -ntp package
pnpm --dir webapp build
docker build --platform linux/amd64 -t meper-chatbi-backend:0.1.0-20261013 meper-chatbi-start
docker build --platform linux/amd64 -t meper-chatbi-frontend:0.1.0-20261013 webapp
docker save -o images.tar meper-chatbi-backend:0.1.0-20261013 meper-chatbi-frontend:0.1.0-20261013
```

前端构建要求 Node 20+，GPT-Vis 依赖补丁通过 pnpm 锁文件固定。

## glm-5.3-flash 验收配置

2026-10-13 使用智谱 `glm-5.3-flash`、Anthropic 协议、接入点 `https://open.bigmodel.cn/api/anthropic` 验证知识草稿与跨库分析。模型设置 → 高级参数 → Max Tokens 填 `8192`（API 参数为 `{"maxTokens":8192}`）。未设置时当前网关默认 1024，实际验收出现工具 JSON 截断；不要将仅有文字回复视为草稿已保存，应核对变更回执。复杂跨库多步分析本次耗时约 289 秒，接近当前 300 秒 SSE 上限；不作为时延 SLA。

原控制库的模型配置未被本次验收修改。模型密钥、控制库、测试业务库均不随部署包分发。完整结果见 [验收记录](../../docs/plans/release-acceptance-20261013.md)。
