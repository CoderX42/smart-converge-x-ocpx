# SCX 服务器部署指南

目标机示例：`root@42.81.102.181`  
仓库：`git@gitea.smartcute.cn:karl419/smart-converge-x.git`

## 架构

```text
浏览器
  │  :80
  ▼
Nginx  ──静态──▶ /var/www/scx          (前端 dist)
  │
  └── /api/boot/* 代理 ──▶ 127.0.0.1:9999/boot/*   (scx-boot.jar, systemd)
                           │
              MySQL / Redis / RocketMQ（application-prod.yml）
```

| 组件 | 默认 |
| --- | --- |
| 代码目录 | `/opt/smart-converge-x` |
| 环境变量 | `/etc/scx/scx.env` |
| 前端静态 | `/var/www/scx` |
| 后端服务 | `scx-boot.service` |
| 后端端口 | `9999`，context-path `/boot` |
| 日志 | `journalctl -u scx-boot` 与 `/var/log/scx/` |

## 部署前准备

### 1. 依赖中间件（必须先就绪）

当前 `application-prod.yml` 直连内网：

- MySQL：`mysqle379be1ffea9.rds.ivolces.com:3306` / `ocpxuser`，库名 `smart_converge_x`
- Redis：`redis-cnlfagupdyxgrkgqy.redis.ivolces.com:6379` / `ocpxuser`
- RocketMQ NameServer：仍为 `127.0.0.1:9876`（若本机未装 MQ，相关异步能力会受影响）

### 单独初始化数据库

部署机须能访问内网 RDS，并安装 `mysql` 客户端：

```bash
# 仓库已 clone 到 /opt/smart-converge-x 时
bash /opt/smart-converge-x/scripts/deploy/db-init.sh
```

脚本会：连通性检查 → 创建库（若有权限）→ `init-database.sh` 建表/迁移 → `verify-database.sh` 校验。

也可手动：

```bash
export MYSQL_HOST=mysqle379be1ffea9.rds.ivolces.com
export MYSQL_PORT=3306
export MYSQL_USERNAME=ocpxuser
export MYSQL_PASSWORD='OqS5ZRBU1wBuEqLeHAQ3'
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

### 4. 部署方式说明

本项目在 **`42.81.102.181` 使用纯系统部署**，不依赖宝塔：

| 组件 | 方式 |
| --- | --- |
| 后端 | `systemd` 单元 `scx-boot.service` |
| 前端 | 构建产物同步到 `/var/www/scx` |
| 反向代理 | 系统 Nginx：`/etc/nginx/conf.d/scx.conf` |
| 更新 | `scripts/deploy/update.sh` |

Nginx 配置文件：

| 文件 | 用途 |
| --- | --- |
| `scripts/deploy/nginx-scx.conf` | 完整 `server {}`，拷到 `/etc/nginx/conf.d/scx.conf`（本机推荐） |
| `scripts/deploy/nginx-scx-baota-rewrite.conf` | 宝塔「伪静态」粘贴用（**不要**用其它商城项目的伪静态） |
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

---

## 首次部署（在 42.81.102.181 上）

```bash
# 1) 登录
ssh root@42.81.102.181

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

在服务器 `root@42.81.102.181` 执行：

```bash
# 仅重启（不重新构建）——最常用
bash /opt/smart-converge-x/scripts/deploy/restart-all.sh

# 拉代码后重启
bash /opt/smart-converge-x/scripts/deploy/restart-all.sh --pull

# 拉代码 + 构建后端/前端/xxl-job + 重启（完整热更）
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
# 首次安装（下载源码构建 + 写配置 + 启动，需 Maven/JDK）
bash /opt/smart-converge-x/scripts/deploy/install-xxl-job.sh

# 日常启停
bash /opt/smart-converge-x/scripts/deploy/start-xxl-job.sh start
bash /opt/smart-converge-x/scripts/deploy/start-xxl-job.sh restart
bash /opt/smart-converge-x/scripts/deploy/start-xxl-job.sh status
bash /opt/smart-converge-x/scripts/deploy/start-xxl-job.sh logs
```

| 项 | 值 |
| --- | --- |
| 控制台 | `http://127.0.0.1:8080/xxl-job-admin` |
| 默认账号 | `admin` / `123456` |
| accessToken | `scx-prod-xxl-token`（与 `application-prod.yml` 一致） |
| 数据源 | 与 SCX 相同 MySQL 库 `smart_converge_x`（`xxl_job_*` 表） |
| systemd | `xxl-job-admin.service` |

安装后请重启 scx-boot，使执行器连上调度中心：

```bash
systemctl restart scx-boot
```

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

## 脚本清单

| 文件 | 作用 |
| --- | --- |
| `install.sh` | 首次装依赖、clone、迁移、构建、systemd、Nginx、启动 |
| `update.sh` | 一键拉取、构建、迁移、重启 |
| `scxctl.sh` | start/stop/restart/status/logs/health/version |
| `scx-boot.service` | systemd 单元 |
| `nginx-scx.conf` | Nginx 站点（`/api` → `/boot`） |
| `env.example` | `/etc/scx/scx.env` 模板 |
| `common.sh` | 公共函数 |
