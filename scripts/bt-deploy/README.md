# SCX 宝塔运维脚本

本目录是项目唯一的部署脚本入口，命名与参考项目 `bt-deploy-scripts` 一致：`00` 同步、`01` 打包发布、`02` 管理、`03` 全量发布、`04` 推送。旧的 `scripts/deploy/` systemd、环境文件和独立 Nginx 方案已移除；Java 重启调用宝塔面板自带的项目重启入口，不另建 systemd 服务或直接管理进程。

## 约定

- 代码目录：`SCX_HOME`，默认脚本所在仓库；服务器常用 `/opt/smart-converge-x`。
- 构建产物：
  - 后端：`${SCX_HOME}/build/backend/scx-boot.jar`、`scx-ocpx-engine.jar`、`xxl-job-admin.jar`
  - 前端：`${SCX_HOME}/build/frontend/`（管理端静态站根，含 `index.html`；**不再**使用 `web-ele` 子目录）
  - 源码工程仍在 `scx-fronted/apps/web-ele`（UI 库变体目录名，仅构建入口，不是部署产物名）
- 宝塔网站根：`SCX_WEB_ROOT`，默认 `/www/wwwroot/scx`；兼容旧变量 `SCX_BAOTA_SITE_ROOT`。
- 备份目录：`${SCX_HOME}/backup/<时间戳>/`。每次激活新构建产物及发布前端时都会保存旧副本。
- 自动清理：每次部署成功后默认保留最近 5 个发布备份；可通过 `SCX_BACKUP_KEEP=10` 调整保留数量。仅清理符合 `YYYYMMDD_HHMMSS` 命名格式的发布目录。
- 运行配置仍以仓库内 `application-*.yml` / XXL properties 的字面量为准；脚本不读取或生成 `/etc/scx/scx.env`。
- 宝塔 Java 项目名默认是 `scx-boot` 与 `scx-ocpx-engine`；名称不同时可通过命令参数或 `SCX_BT_BOOT_PROJECT`、`SCX_BT_ENGINE_PROJECT` 覆盖。

## 日常发布

```bash
cd /opt/smart-converge-x
bash scripts/bt-deploy/03-deploy_all.sh
```

这一条命令会依次完成：安全快进同步 → 构建 boot/engine/XXL-JOB/前端 → 发布前端静态文件 → 通过宝塔重启 boot → 等待 boot 健康 → 通过宝塔重启 engine → 等待 engine 健康。前端为 Nginx 静态文件，发布成功后立即生效，不存在单独的前端进程需要重启。

若宝塔中的两个项目名是 `boot` 和 `engine`，使用：

```bash
bash scripts/bt-deploy/03-deploy_all.sh --boot-project boot --engine-project engine
```

`00-sync_repos.sh` 默认只允许干净工作区的 fast-forward 同步，防止服务器改动被静默覆盖。确认可丢弃已跟踪本地改动后才可使用：

```bash
bash scripts/bt-deploy/03-deploy_all.sh --discard-local
```

## 分步操作

```bash
# 同步指定分支
bash scripts/bt-deploy/00-sync_repos.sh --branch main

# 只构建后端；修改 application-prod.yml 后使用此流程并在宝塔重启项目
bash scripts/bt-deploy/01-deploy.sh --no-pull backend

# 仅调用宝塔重启 boot + engine，并等待两个健康检查通过
bash scripts/bt-deploy/02-manage.sh restart

# 只构建独立 OCPX 数据面（不会自动启动，也不会授权真实外发）
bash scripts/bt-deploy/01-deploy.sh --no-pull engine

# 只构建并同步前端
bash scripts/bt-deploy/01-deploy.sh --no-pull frontend

# 不同步网站根，仅检查产物
bash scripts/bt-deploy/01-deploy.sh --no-pull --no-publish all

# Nginx 配置检查与重载（仅前端站点配置变更后需要）
bash scripts/bt-deploy/02-manage.sh nginx-test
bash scripts/bt-deploy/02-manage.sh nginx-reload

# 特殊情况下只发布不重启；脚本会明确提示新 JAR 尚未生效
bash scripts/bt-deploy/03-deploy_all.sh --no-restart
```

把 [templates/nginx-scx-baota-rewrite.conf](templates/nginx-scx-baota-rewrite.conf) 粘贴到宝塔站点的“伪静态”配置。不要将 `/api/` 的 `proxy_pass` 改为带 `/boot/` 的路径，否则会产生 `/boot/boot/...`。模板只将公网 `/ocpx/open-api/` 转发到本机 `10000`，并原样保留 `/ocpx` 路径用于 HMAC 验签；`/ocpx/actuator` 不对公网暴露。

前端单独发布只需要 Node.js、pnpm 和站点目录写权限，不要求本机安装 Maven/JDK；脚本会优先使用 `rsync`，未安装时回退到 `cp`。如果宝塔站点根不是默认目录，请显式设置 `SCX_WEB_ROOT`，并确保宝塔网站根与该目录一致。

## 边界

- 脚本会构建、备份构建产物、发布前端静态文件，并调用 `/www/server/panel/script/restart_project.py` 让宝塔重启 Java 项目；不会直接执行 `kill`、`java -jar` 或创建平行的 systemd 服务。
- 自动重启要求宝塔内置 `restart_project.py` 与 `btpython`（或面板 Python）可用；脚本会确认目标 JAR 对应的新 PID 已出现，再等待健康检查，项目名错误、双进程或超时都会令发布失败。
- `scx-ocpx-engine` 必须在宝塔中作为独立 Java 项目运行，JAR 为 `build/backend/scx-ocpx-engine.jar`，端口 10000，健康检查 `/ocpx/actuator/health`。
- 数据库迁移、备份、真实渠道 UAT、容量和回滚演练必须按发布 Runbook 在授权窗口执行，构建成功不代表生产验收通过。
- `04-push_to_gitea.sh` 仅用于受控构建机或本地已审查提交的推送，不是服务器发布步骤。
