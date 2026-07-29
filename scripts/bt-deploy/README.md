# SCX 宝塔运维脚本（对标 haorong `bt-deploy-scripts`）

与浩荣项目习惯对齐：**00 同步 + 03 一键部署** 为日常主力。  
服务启停由 **宝塔 Java 项目管理器 / 面板** 完成，本目录不提供 manage 脚本。

## 配置规范（与 AGENTS.md §6.4 一致）

- **scx-boot / xxl-job 配置直写**在仓库 yml、properties 中，**不要**使用 `${SCX_*}`、`${XXL_JOB_*}` 等环境变量占位。
- **不要**要求启动前 `source /etc/scx/scx.env`；宝塔可直接 `java -jar`。
- 改 `application-prod.yml` 或 `xxl-job-admin/.../application.properties` 后，必须用本目录脚本**重新打包**再重启。

| 脚本 | 作用 | 常用 |
| --- | --- | --- |
| `00-sync_repos.sh` | 从 Gitea 同步 monorepo（clone / `reset --hard`） | ⭐ 常用 |
| `01-deploy.sh` | scx-boot + **xxl-job-admin** + 前端构建，并发布站点根 | 打包 |
| `03-deploy_all.sh` | **00 → 01** 一键同步+打包（不自动重启服务） | ⭐ 常用 |

## 路径约定

| 项 | 默认 | 说明 |
| --- | --- | --- |
| 代码根 `SCX_HOME` | `/opt/smart-converge-x` | 也可 `/www/wwwroot/smart-converge-x` |
| 站点根 `SCX_WEB_ROOT` | `/www/wwwroot/scx` | 宝塔「网站根目录」指这里 |
| 产物 | `${SCX_HOME}/build/backend/` | `scx-boot.jar`、`xxl-job-admin.jar`；前端：`build/frontend/web-ele` |
| 配置 | `/etc/scx/scx.env` 或宝塔 Java 项目环境变量 | 数据源 / Redis / 密钥 |
| 分支 | `main` | `BRANCH=xxx` 可覆盖 |

## 宝塔面板一次性配置

1. **网站**：根目录 = `SCX_WEB_ROOT`（如 `/www/wwwroot/scx`）
2. **伪静态**：粘贴仓库 `scripts/deploy/nginx-scx-baota-rewrite.conf`
3. **生产配置文件**（必做，否则 prod 起不来）：
   ```bash
   mkdir -p /etc/scx
   cp /opt/smart-converge-x/scripts/deploy/env.example /etc/scx/scx.env
   chmod 600 /etc/scx/scx.env
   nano /etc/scx/scx.env   # 填入真实 MySQL/Redis/密钥等
   ```
4. **Java 项目 scx-boot**（推荐用启动脚本，不要裸 `java -jar`）：
   - 启动命令：`/bin/bash /opt/smart-converge-x/scripts/deploy/run-boot.sh`
   - 或「项目配置 → 环境变量」里逐项填入 `SCX_*`（与 `scx.env` 相同）
   - 缺变量时日志会报：`Missing required production configuration: SCX_...`
5. **不要**对公网放行 `9999`

## 日常命令

```bash
cd /opt/smart-converge-x/scripts/bt-deploy

# ⭐ 只同步 Gitea
bash 00-sync_repos.sh

# ⭐ 同步 + 打包 + 发布前端
bash 03-deploy_all.sh

# 仅打包后端（含 xxl-job）/ 仅 xxl / 仅前端
bash 01-deploy.sh --no-pull backend
bash 01-deploy.sh --no-pull xxl
bash 01-deploy.sh --no-pull frontend
bash 01-deploy.sh --no-pull --skip-xxl backend   # 只要 scx-boot

# 打包完成后：在宝塔 Java 项目管理器中分别重启 scx-boot、xxl-job-admin
# JAR:
#   /opt/smart-converge-x/build/backend/scx-boot.jar
#   /opt/smart-converge-x/build/backend/xxl-job-admin.jar
```

### 环境变量示例

```bash
export SCX_HOME=/opt/smart-converge-x
export SCX_WEB_ROOT=/www/wwwroot/scx
export SCX_GIT_URL='git@gitea.smartcute.cn:karl419/smart-converge-x.git'
export BRANCH=main

bash 03-deploy_all.sh
```

## 与浩荣脚本对照

| haorong | SCX | 说明 |
| --- | --- | --- |
| 00 / 01 / 03 | 00 / 01 / 03 | 常用流程一致 |
| 02-manage | **不提供** | 启停交给宝塔面板 |
| 03 不自动重启 | 03 不自动重启 | 打包后面板点重启 |

## 首次上机

```bash
# 配置 Gitea 部署公钥后：
bash /opt/smart-converge-x/scripts/bt-deploy/00-sync_repos.sh
# 配置宝塔 Java 项目 + 网站伪静态 + 环境变量
bash /opt/smart-converge-x/scripts/bt-deploy/03-deploy_all.sh
# 面板重启 Java 项目
```
