# SCX 服务器部署指南

目标机示例：`root@42.81.102.181`  
仓库：`git@gitea.smartcute.cn:karl419/smart-converge-x.git`

## 部署标准（后续以本约定为准）

**线上统一：`宝塔面板` + `bash 脚本`。**

| 职责 | 谁来做 | 说明 |
| --- | --- | --- |
| 站点 / SSL / 伪静态 / 反代 | **宝塔** | 网站根目录 + 粘贴 `nginx-scx-baota-rewrite.conf` |
| 拉代码 / 构建 / 迁移 / 重启后端 | **bash** | `baota-deploy.sh`（推荐）或 `update.sh` / `restart-all.sh` |
| 后端进程 | **systemd** `scx-boot` | 仍由 bash 启停，配置 `/etc/scx/scx.env` |
| Nginx 配置文件 | **只在宝塔改** | bash **禁止**覆盖 `/www/server/panel/vhost/nginx/*` |

### 日常命令（推荐：对标浩荣 `bt-deploy-scripts`）

脚本目录：`scripts/bt-deploy/`（**00 / 03 最常用**；无 02，启停用宝塔面板）

```bash
cd /opt/smart-converge-x/scripts/bt-deploy

# ⭐ 只同步 Gitea
bash 00-sync_repos.sh

# ⭐ 同步 + 打包 + 发布前端（不自动重启服务）
bash 03-deploy_all.sh

# 打包完成后：宝塔 → Java 项目管理器 → 重启 scx-boot
```

详见：[`scripts/bt-deploy/README.md`](../bt-deploy/README.md)

### 兼容入口（`scripts/deploy/baota-deploy.sh`）

```bash
bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh status
bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh restart
bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh update
```

宝塔站点一次性配置：

1. 新建网站，根目录例如 `/www/wwwroot/scx`（或设 `SCX_WEB_ROOT` / `SCX_BAOTA_SITE_ROOT`）
2. **伪静态**粘贴：`scripts/deploy/nginx-scx-baota-rewrite.conf`
3. 确认 `/api` → `http://127.0.0.1:9999/`（只剥 `/api`，不要拼成 `/boot/boot`）
4. SSL 在宝塔申请；**不要**对公网放行 `9999`

## 架构

```text
浏览器
  │  :80 / :443
  ▼
宝塔 Nginx  ──静态──▶ /www/wwwroot/scx   (前端 dist，可由 SCX_WEB_ROOT 覆盖)
  │
  └── /api/* 伪静态反代 ──▶ 127.0.0.1:9999/*   (scx-boot.jar, systemd)
                           │
              MySQL / Redis / RocketMQ（/etc/scx/scx.env 注入）
```

| 组件 | 默认 |
| --- | --- |
| 代码目录 | `/opt/smart-converge-x` |
| 环境变量 | `/etc/scx/scx.env` |
| 前端静态 | `/www/wwwroot/scx`（宝塔）或 `/var/www/scx` |
| 后端服务 | `scx-boot.service` |
| 后端端口 | `9999`，context-path `/boot` |
| 日志 | `journalctl -u scx-boot` 与 `/var/log/scx/` |
| 部署入口 | `scripts/deploy/baota-deploy.sh` |

## 部署前准备

### 1. 依赖中间件（必须先就绪）

当前 `application-prod.yml` 直连内网：

- MySQL、Redis：仅从 `/etc/scx/scx.env` 或外部密钥管理注入，文档不记录主机、账号或密码。
- RocketMQ NameServer：固定为 `127.0.0.1:9876`（本机单节点只用于受控恢复，不代表高可用）。

### 单独初始化数据库

部署机须能访问内网 RDS，并安装 `mysql` 客户端：

```bash
# 仓库已 clone 到 /opt/smart-converge-x 时
bash /opt/smart-converge-x/scripts/deploy/db-init.sh
```

脚本会：连通性检查 → 创建库（若有权限）→ `init-database.sh` 建表/迁移 → `verify-database.sh` 校验。

也可手动：

```bash
export MYSQL_HOST='<由外部密钥管理注入>'
export MYSQL_PORT=3306
export MYSQL_USERNAME='<由外部密钥管理注入>'
export MYSQL_PASSWORD='<由外部密钥管理注入>'
cd /opt/smart-converge-x/scx-backend/db/smart-converge-x
bash ./init-database.sh
bash ./verify-database.sh
```

### 2. Gitea SSH 权限

服务器需要能 `git clone` 私有仓库。首次 `install.sh` 会生成 `/root/.ssh/id_ed25519`，把 **公钥** 加到 Gitea：

- 用户 SSH Keys，或  
- 仓库 Deploy Keys（只读即可）

### 3. 安全组 / 防火墙

放行：

- `22` SSH  
- `80`（及可选 `443`）HTTP  

**不要** 对公网放行 `9999`（后端仅本机 Nginx 反代）。

### 4. 部署方式说明（宝塔 + bash）

| 组件 | 方式 |
| --- | --- |
| 后端 | `systemd` 单元 `scx-boot.service`（bash 启停） |
| 前端 | 构建产物 rsync 到宝塔网站根（`SCX_WEB_ROOT`） |
| 反向代理 / SSL | **宝塔**站点 + 伪静态 |
| 更新 / 重启 | `scripts/deploy/baota-deploy.sh` |

Nginx 相关文件：

| 文件 | 用途 |
| --- | --- |
| `scripts/deploy/nginx-scx-baota-rewrite.conf` | **标准**：宝塔「伪静态」粘贴（**不要**用其它商城项目的伪静态） |
| `scripts/deploy/nginx-scx.conf` | 完整 `server {}`，仅无宝塔的纯系统 Nginx 备用 |
| `scripts/deploy/nginx-scx-rewrite.conf` | 仅 location 片段备用 |

**样式丢失排查**：网站根目录必须是 dist 根目录（含 `css/`、`js/`、`jse/`）。  
若 `.css` 请求返回的是 `index.html`（Content-Type: `text/html`），浏览器会拒绝应用样式。  
静态 location 必须使用 `try_files $uri =404`，不能让缺失资源回退 SPA。

**路径约定（不要改错）：**

前端 `VITE_GLOB_API_URL=/api` 且 `VITE_OPEN_BOOT=true` 时，请求拦截器会再拼 `/boot`：

```text
浏览器 /api/boot/xxx  →  Nginx 剥掉 /api  →  http://127.0.0.1:9999/boot/xxx
浏览器 /              →  /var/www/scx （SPA 静态文件）
```

`proxy_pass` 必须是 `http://127.0.0.1:9999/;`（只去掉 `/api`）。  
若写成 `http://127.0.0.1:9999/boot/;`，会变成 `/boot/boot/...`，白名单接口（如验证码）返回 401。

手动安装 / 重载 Nginx：

```bash
cp /opt/smart-converge-x/scripts/deploy/nginx-scx.conf /etc/nginx/conf.d/scx.conf
# 避免默认站点抢 80
rm -f /etc/nginx/sites-enabled/default /etc/nginx/conf.d/default.conf 2>/dev/null || true
nginx -t && systemctl enable nginx && systemctl reload nginx
```

### 可视化配置 SCX Nginx（推荐，无需宝塔）

项目自带轻量面板，**只改** `/etc/nginx/conf.d/scx.conf`，不接管系统 Nginx：

```bash
bash /opt/smart-converge-x/scripts/deploy/start-nginx-panel.sh install
bash /opt/smart-converge-x/scripts/deploy/start-nginx-panel.sh url
```

本机用 SSH 隧道打开（默认只监听 `127.0.0.1:9088`）：

```bash
ssh -N -L 9088:127.0.0.1:9088 root@42.81.102.181
# 浏览器打开 start-nginx-panel.sh url 打印的地址
```

面板能力：表单改端口/域名/静态目录/后端地址/超时、启用 /boot 与 WebSocket、原始 conf 编辑、`nginx -t`、应用并 reload。

---

## 首次部署（在 42.81.102.181 上）

```bash
# 1) 登录
# 先由基础设施负责人带外确认 SHA256 指纹；包装器不会 keyscan/accept-new
SCX_SSH_FINGERPRINT_CONFIRMED='SHA256:rrH8nVzaA40JGueBrcH8Jq2tad//8FL0WbUEuHSOXZo' \
  bash /opt/smart-converge-x/scripts/deploy/ssh-production.sh

# 2) 若本机还没有仓库，可先临时拉脚本；或直接 clone 后执行 install
# 推荐：先配置好 SSH 能访问 Gitea，再：
git clone -b main git@gitea.smartcute.cn:karl419/smart-converge-x.git /opt/smart-converge-x

# 3) 执行首次安装（装 JDK/Maven/Node/Nginx、迁移、构建、systemd、启动）
bash /opt/smart-converge-x/scripts/deploy/install.sh
```

按提示：

1. 若打印了 SSH 公钥 → 加到 Gitea 后 **再跑一次** `install.sh`  
2. 确认 MySQL/Redis/RocketMQ 已可用后回车继续  
3. 按需编辑 `/etc/scx/scx.env`（数据库密码等）

验证：

```bash
curl -s http://127.0.0.1:9999/boot/actuator/health
# 浏览器打开 http://42.81.102.181/
# 默认管理员 system / 123456（登录后立刻改密）
```

---

## 日常一键重启（后端 + XXL-JOB + 前端/Nginx）

在服务器执行（**推荐宝塔入口**）：

```bash
bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh restart
bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh update
```

兼容旧脚本（已支持宝塔 Nginx reload）：

```bash
# 仅重启（不重新构建）
bash /opt/smart-converge-x/scripts/deploy/restart-all.sh

# 拉代码后重启
bash /opt/smart-converge-x/scripts/deploy/restart-all.sh --pull

# 拉代码 + 构建后端/前端 + 重启（XXL-JOB 不在生产机编译）
bash /opt/smart-converge-x/scripts/deploy/restart-all.sh --build
```

| 组件 | 重启方式 |
| --- | --- |
| 后端 scx-boot | `systemctl restart scx-boot` |
| XXL-JOB Admin | `systemctl restart xxl-job-admin`（未安装则跳过） |
| 前端 | 静态目录 `/var/www/scx`，`systemctl reload nginx` |

单独重启：

```bash
systemctl restart scx-boot
systemctl restart xxl-job-admin
systemctl reload nginx
```

建议别名：

```bash
echo "alias scx-restart='bash /opt/smart-converge-x/scripts/deploy/restart-all.sh'" >> /root/.bashrc
echo "alias scx-update='bash /opt/smart-converge-x/scripts/deploy/update.sh'" >> /root/.bashrc
source /root/.bashrc
# 之后: scx-restart
```

## 日常一键更新重启（含迁移）

代码推到 `main` 后，需要库迁移 + 全量构建时：

```bash
bash /opt/smart-converge-x/scripts/deploy/update.sh
```

流程：`git pull` → 数据库迁移 → 后端打包 → 前端构建并同步 Nginx → 重启 `scx-boot` → 健康检查。

常用开关：

```bash
# 只更新后端
SCX_SKIP_FRONTEND=true bash /opt/smart-converge-x/scripts/deploy/update.sh

# 跳过库迁移
SCX_RUN_DB_MIGRATE=false bash /opt/smart-converge-x/scripts/deploy/update.sh
```

---

## XXL-JOB 调度中心（一键）

SCX 执行器（`scx-boot`）会向调度中心注册；中心单独以 systemd 运行。

```bash
# 首次安装：在受控构建机本地构建 3.4.1 JAR，再通过受控通道上传并校验摘要。
# 生产机禁止下载源码或执行 Maven；不要把摘要或凭据写入仓库/聊天记录。
bash /opt/smart-converge-x/scripts/deploy/install-xxl-job.sh \
  --jar /secure/staging/xxl-job-admin-3.4.1.jar \
  --jar-sha256 '<构建产物 SHA-256>'

# 日常启停
bash /opt/smart-converge-x/scripts/deploy/start-xxl-job.sh start
bash /opt/smart-converge-x/scripts/deploy/start-xxl-job.sh restart
bash /opt/smart-converge-x/scripts/deploy/start-xxl-job.sh status
bash /opt/smart-converge-x/scripts/deploy/start-xxl-job.sh logs
```

| 项 | 值 |
| --- | --- |
| 控制台 | `http://127.0.0.1:8080/xxl-job-admin` |
| 默认账号 | 首次登录后由授权操作人从外部密钥管理取用并立即改密 |
| accessToken | 仅从 `/etc/scx/scx.env` 或外部密钥管理注入 |
| 数据源 | 与 SCX 相同 MySQL 库 `smart_converge_x`（`xxl_job_*` 表） |
| systemd | `xxl-job-admin.service` |

安装后请在受控窗口重启 scx-boot，使执行器连上调度中心。Publisher 与 Scheduler
必须先保持关闭，完成 Broker/Topic、Outbox 和 Redis 恢复对账后再逐项开启：

```bash
systemctl restart scx-boot
```

### RocketMQ 受控恢复（仅 42.81.102.181）

安装脚本默认只校验固定的 RocketMQ 5.3.1 本地压缩包、写入配置和 systemd unit，
不会启动或 enable NameServer/Broker，也不会下载源码。安装前必须由基础设施负责人带外
确认 SSH 主机身份；禁止使用 `StrictHostKeyChecking=accept-new` 或未知指纹。

```bash
# 生产机仅在授权窗口执行，且压缩包 SHA-256 必须与脚本固定值一致
bash /opt/smart-converge-x/scripts/deploy/install-rocketmq.sh \
  /secure/staging/rocketmq-all-5.3.1-bin-release.zip

# 完成配置差异复核后，显式启动并验证本机 Topic
bash /opt/smart-converge-x/scripts/deploy/install-rocketmq.sh \
  /secure/staging/rocketmq-all-5.3.1-bin-release.zip --start
```

`--start` 只负责 NameServer/Broker 和固定 `scx_tracking_events` Topic；Publisher、
XXL-JOB Scheduler、SCX executor 必须由恢复 runbook 按顺序单独开启。Topic 自动创建已
关闭，NameServer/Broker 仅绑定 `127.0.0.1`，systemd 也拒绝非本机网络。安装脚本支持
重复执行；配置漂移时默认失败，只有完成人工复核后才允许 `--force-config`，且会保留
旧配置备份。

---

## 运维命令

```bash
bash /opt/smart-converge-x/scripts/deploy/scxctl.sh status
bash /opt/smart-converge-x/scripts/deploy/scxctl.sh restart
bash /opt/smart-converge-x/scripts/deploy/scxctl.sh logs
bash /opt/smart-converge-x/scripts/deploy/scxctl.sh health
bash /opt/smart-converge-x/scripts/deploy/scxctl.sh version
```

或直接：

```bash
systemctl status scx-boot
journalctl -u scx-boot -f
```

---

## 回滚

```bash
cd /opt/smart-converge-x
git log --oneline -20
git checkout <旧 commit>
SCX_RUN_DB_MIGRATE=false bash scripts/deploy/update.sh
# 注意：已执行的 DB 迁移不可随意回退，需按 runbook 前向修复
```

---

## 新机 / 重置机：一键环境初始化

服务器重装后，先装齐运行本项目所需工具链（不写入弱默认密钥、默认不启动业务）：

```bash
# 上传脚本后，以 root 执行
bash scripts/deploy/bootstrap-env.sh

# 单机全栈：本机 MySQL + Redis + 克隆代码
SCX_GIT_URL='git@gitea.smartcute.cn:karl419/smart-converge-x.git' \
  bash scripts/deploy/bootstrap-env.sh --with-mysql --with-redis --clone

# 填好 /etc/scx/scx.env 后部署应用
bash scripts/deploy/bootstrap-env.sh --deploy -y
# 或继续用既有：
# bash /opt/smart-converge-x/scripts/deploy/install.sh
```

`bootstrap-env.sh` 安装内容：OpenJDK 17、Maven、Node 20、pnpm 10.12.4、Nginx、Git、mysql-client、redis-cli，以及 `/opt/smart-converge-x`、`/var/www/scx`、`/var/log/scx`、`/etc/scx` 目录。  
RocketMQ / XXL-JOB 仍用 `install-rocketmq.sh` / `install-xxl-job.sh` 单独安装。

---

## 脚本清单

| 文件 | 作用 |
| --- | --- |
| `../bt-deploy/00,01,03-*.sh` | **线上主力（对标浩荣）**：同步 / 打包 / 一键部署（启停用宝塔） |
| `baota-deploy.sh` | 兼容入口：宝塔 + bash 更新/重启/健康检查 |
| `bootstrap-env.sh` | 新机/重置机一键环境初始化（工具链 + 可选本机 MySQL/Redis/clone/deploy） |
| `install.sh` | 首次装依赖、clone、迁移、构建、systemd、启动 |
| `update.sh` | 一键拉取、构建、迁移、重启（检测到宝塔时不覆盖站点 conf） |
| `restart-all.sh` | 重启 scx-boot / xxl / nginx（宝塔优先 reload） |
| `scxctl.sh` | start/stop/restart/status/logs/health/version |
| `scx-boot.service` | systemd 单元 |
| `nginx-scx-baota-rewrite.conf` | 宝塔伪静态（标准） |
| `nginx-scx.conf` | 纯系统 Nginx 完整站点（备用） |
| `env.example` | `/etc/scx/scx.env` 模板 |
| `common.sh` | 公共函数 |
