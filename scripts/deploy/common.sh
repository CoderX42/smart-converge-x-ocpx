#!/usr/bin/env bash
# 公共函数与默认配置，供 install.sh / update.sh / scxctl.sh 引用
set -euo pipefail

SCX_ENV_FILE="${SCX_ENV_FILE:-/etc/scx/scx.env}"

if [[ -f "$SCX_ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  set -a
  source "$SCX_ENV_FILE"
  set +a
fi

SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
SCX_BRANCH="${SCX_BRANCH:-main}"
SCX_GIT_URL="${SCX_GIT_URL:-git@gitea.smartcute.cn:karl419/smart-converge-x.git}"
SCX_PROFILE="${SCX_PROFILE:-prod}"
SCX_JAVA_OPTS="${SCX_JAVA_OPTS:--Xms512m -Xmx1024m -Dfile.encoding=UTF-8}"
SCX_JAR="${SCX_JAR:-${SCX_HOME}/scx-backend/scx-boot/target/scx-boot.jar}"
SCX_LOG_DIR="${SCX_LOG_DIR:-/var/log/scx}"
SCX_HEALTH_URL="${SCX_HEALTH_URL:-http://127.0.0.1:9999/boot/actuator/health}"
SCX_WEB_ROOT="${SCX_WEB_ROOT:-/var/www/scx}"
SCX_RUN_DB_MIGRATE="${SCX_RUN_DB_MIGRATE:-true}"
SCX_SKIP_FRONTEND="${SCX_SKIP_FRONTEND:-false}"
SCX_SKIP_BACKEND="${SCX_SKIP_BACKEND:-false}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"

log()  { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
die()  { log "ERROR: $*"; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "缺少命令: $1"; }

require_root() {
  [[ "$(id -u)" -eq 0 ]] || die "请使用 root 执行（当前 uid=$(id -u)）"
}

ensure_dirs() {
  mkdir -p "$SCX_HOME" "$SCX_LOG_DIR" "$SCX_WEB_ROOT" /etc/scx
}

wait_health() {
  local url="${1:-$SCX_HEALTH_URL}"
  local retries="${2:-40}"
  local i
  for ((i = 1; i <= retries; i++)); do
    if curl -fsS --max-time 3 "$url" >/dev/null 2>&1; then
      log "健康检查通过: $url"
      return 0
    fi
    sleep 3
  done
  die "健康检查失败: $url （已重试 ${retries} 次）"
}

git_sync() {
  need git
  if [[ ! -d "$SCX_HOME/.git" ]]; then
    log "首次克隆仓库 -> $SCX_HOME"
    git clone -b "$SCX_BRANCH" "$SCX_GIT_URL" "$SCX_HOME"
  else
    log "拉取最新代码 ($SCX_BRANCH)"
    git -C "$SCX_HOME" fetch --all --prune
    git -C "$SCX_HOME" checkout "$SCX_BRANCH"
    git -C "$SCX_HOME" pull --ff-only origin "$SCX_BRANCH"
  fi
  log "当前版本: $(git -C "$SCX_HOME" rev-parse --short HEAD) $(git -C "$SCX_HOME" log -1 --pretty=%s)"
}

build_backend() {
  if [[ "$SCX_SKIP_BACKEND" == "true" ]]; then
    log "跳过后端构建 (SCX_SKIP_BACKEND=true)"
    return 0
  fi
  need mvn
  need java
  log "构建后端 scx-boot ..."
  (
    cd "$SCX_HOME/scx-backend"
    mvn -pl scx-boot -am -DskipTests package
  )
  [[ -f "$SCX_JAR" ]] || die "未找到 JAR: $SCX_JAR"
  log "后端构建完成: $SCX_JAR"
}

build_frontend() {
  if [[ "$SCX_SKIP_FRONTEND" == "true" ]]; then
    log "跳过前端构建 (SCX_SKIP_FRONTEND=true)"
    return 0
  fi
  need node
  need pnpm
  log "构建前端 web-ele ..."
  (
    cd "$SCX_HOME/scx-fronted"
    corepack enable >/dev/null 2>&1 || true
    pnpm install --frozen-lockfile
    pnpm build:ele
  )
  local dist="$SCX_HOME/scx-fronted/apps/web-ele/dist"
  [[ -d "$dist" ]] || die "前端产物不存在: $dist"
  log "同步前端静态资源 -> $SCX_WEB_ROOT"
  rsync -a --delete "$dist"/ "$SCX_WEB_ROOT"/
  log "前端发布完成"
}

db_migrate() {
  if [[ "$SCX_RUN_DB_MIGRATE" != "true" ]]; then
    log "跳过数据库迁移 (SCX_RUN_DB_MIGRATE=$SCX_RUN_DB_MIGRATE)"
    return 0
  fi
  need mysql
  [[ -n "${MYSQL_PASSWORD:-}" ]] || die "MYSQL_PASSWORD 未设置，请写入 $SCX_ENV_FILE"
  log "执行数据库迁移 (host=$MYSQL_HOST db=smart_converge_x) ..."
  (
    cd "$SCX_HOME/scx-backend/db/smart-converge-x"
    export MYSQL_HOST MYSQL_PORT MYSQL_USERNAME MYSQL_PASSWORD
    # 已有库升级：跳过 baseline，按迁移登记表前向执行
    if mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
      -e "USE smart_converge_x; SELECT 1 FROM scx_schema_migration LIMIT 1;" >/dev/null 2>&1; then
      SCX_SKIP_BASELINE=true SCX_MIGRATION_BASELINE_VERSION=auto ./init-database.sh
    else
      ./init-database.sh
    fi
  )
  log "数据库迁移完成"
}

restart_backend() {
  # 用 unit 文件判断，避免 pipefail + grep -q 提前关闭管道导致误判
  if [[ -f /etc/systemd/system/scx-boot.service ]] \
    || systemctl cat scx-boot.service >/dev/null 2>&1; then
    log "重启 scx-boot.service"
    systemctl daemon-reload
    systemctl restart scx-boot
    systemctl is-active --quiet scx-boot || die "scx-boot 启动失败，请查看 journalctl -u scx-boot -n 100"
  else
    die "未安装 scx-boot.service，请先运行 install.sh"
  fi
}

reload_nginx() {
  if command -v nginx >/dev/null 2>&1; then
    nginx -t
    systemctl reload nginx || systemctl restart nginx
    log "Nginx 已重载"
  else
    log "未安装 nginx，跳过重载"
  fi
}
