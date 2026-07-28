#!/usr/bin/env bash
# SCX 宝塔 + bash 部署 / 重启入口（后续线上标准方式）
#
# 约定：
#   - 反向代理与站点由【宝塔面板】管理（网站根目录 + 伪静态 + 可选 SSL）
#   - 代码拉取、构建、迁移、后端/XXL 重启由【本脚本 / bash】完成
#   - 禁止在 bash 里覆盖宝塔站点 conf（不写 /www/server/panel/vhost/nginx/*）
#   - 禁止把 9999 对公网开放
#
# 宝塔侧一次性配置（面板操作）：
#   1. 新建站点，根目录建议：/www/wwwroot/scx  或  /var/www/scx
#   2. 伪静态粘贴：scripts/deploy/nginx-scx-baota-rewrite.conf
#   3. 反向代理也可在伪静态里（已含 /api → 127.0.0.1:9999/）
#   4. SSL 在宝塔申请/上传
#
# 用法（服务器 root）：
#   bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh status
#   bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh restart
#   bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh reload-nginx
#   bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh update          # pull + 迁移 + 构建 + 重启
#   bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh update --skip-frontend
#   bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh deploy-frontend # 仅构建并同步静态
#   bash /opt/smart-converge-x/scripts/deploy/baota-deploy.sh health
#
# 环境变量（可写在 /etc/scx/scx.env）：
#   SCX_HOME              默认 /opt/smart-converge-x
#   SCX_WEB_ROOT          前端静态目录，默认自动探测宝塔站点根
#   SCX_BAOTA_SITE_ROOT   显式指定宝塔网站根，如 /www/wwwroot/scx
#   SCX_BRANCH            默认 main
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCX_ENV_FILE="${SCX_ENV_FILE:-/etc/scx/scx.env}"

if [[ -f "$SCX_ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$SCX_ENV_FILE"
  set +a
fi

SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
SCX_BRANCH="${SCX_BRANCH:-main}"
SCX_JAR="${SCX_JAR:-${SCX_HOME}/scx-backend/scx-boot/target/scx-boot.jar}"
SCX_LOG_DIR="${SCX_LOG_DIR:-/var/log/scx}"
SCX_HEALTH_URL="${SCX_HEALTH_URL:-http://127.0.0.1:9999/boot/actuator/health}"
SCX_RUN_DB_MIGRATE="${SCX_RUN_DB_MIGRATE:-true}"
SCX_SKIP_FRONTEND="${SCX_SKIP_FRONTEND:-false}"
SCX_SKIP_BACKEND="${SCX_SKIP_BACKEND:-false}"

log()  { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
ok()   { log "OK  $*"; }
warn() { log "WARN $*"; }
die()  { log "ERROR: $*"; exit 1; }

require_root() {
  [[ "$(id -u)" -eq 0 ]] || die "请使用 root 执行"
}

usage() {
  sed -n '2,35p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

# ---------- Baota detection ----------
is_baota() {
  [[ -d /www/server/panel ]] || command -v bt >/dev/null 2>&1
}

detect_web_root() {
  if [[ -n "${SCX_WEB_ROOT:-}" ]]; then
    printf '%s\n' "$SCX_WEB_ROOT"
    return 0
  fi
  if [[ -n "${SCX_BAOTA_SITE_ROOT:-}" ]]; then
    printf '%s\n' "$SCX_BAOTA_SITE_ROOT"
    return 0
  fi
  # 常见宝塔站点根
  local candidates=(
    /www/wwwroot/scx
    /www/wwwroot/smart-converge-x
    /www/wwwroot/42.81.102.181
    /var/www/scx
  )
  local d
  for d in "${candidates[@]}"; do
    if [[ -d "$d" ]]; then
      printf '%s\n' "$d"
      return 0
    fi
  done
  # 若只有一个 wwwroot 子目录且含 index.html 线索，不盲目选
  if [[ -d /www/wwwroot ]]; then
    local count
    count="$(find /www/wwwroot -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l | tr -d ' ')"
    if [[ "$count" == "1" ]]; then
      find /www/wwwroot -mindepth 1 -maxdepth 1 -type d | head -1
      return 0
    fi
  fi
  printf '%s\n' "/www/wwwroot/scx"
}

reload_nginx_baota() {
  log "重载 Nginx（宝塔优先）..."
  if command -v bt >/dev/null 2>&1; then
    # 部分版本: bt restart nginx / bt reload
    if bt reload 2>/dev/null; then
      ok "bt reload"
      return 0
    fi
    if bt restart nginx 2>/dev/null; then
      ok "bt restart nginx"
      return 0
    fi
  fi
  if [[ -x /etc/init.d/nginx ]]; then
    /etc/init.d/nginx reload 2>/dev/null || /etc/init.d/nginx restart
    ok "/etc/init.d/nginx reload|restart"
    return 0
  fi
  if [[ -x /www/server/nginx/sbin/nginx ]]; then
    /www/server/nginx/sbin/nginx -t
    /www/server/nginx/sbin/nginx -s reload
    ok "宝塔 nginx -s reload"
    return 0
  fi
  if command -v nginx >/dev/null 2>&1; then
    nginx -t
    systemctl reload nginx 2>/dev/null || nginx -s reload
    ok "system nginx reload"
    return 0
  fi
  die "未找到可重载的 Nginx（宝塔或系统）"
}

print_baota_checklist() {
  local web_root
  web_root="$(detect_web_root)"
  cat <<EOF

======== 宝塔面板侧检查清单（一次性）========
1. 网站根目录指向前端 dist 根（含 index.html / css / js / jse）:
     当前脚本同步目标: ${web_root}
     可用环境变量覆盖: SCX_WEB_ROOT 或 SCX_BAOTA_SITE_ROOT

2. 网站 → 伪静态 → 粘贴仓库文件内容:
     ${SCX_HOME}/scripts/deploy/nginx-scx-baota-rewrite.conf
   关键:
     - /api → http://127.0.0.1:9999/   （只剥 /api，不要再拼 /boot/）
     - 静态 try_files \$uri =404，禁止 css/js 回退 index.html

3. SSL / 域名在宝塔申请；不要对公网放行 9999

4. 后端仍由 systemd 管理:
     systemctl status scx-boot
     配置: /etc/scx/scx.env

5. bash 日常命令:
     bash ${SCX_HOME}/scripts/deploy/baota-deploy.sh update
     bash ${SCX_HOME}/scripts/deploy/baota-deploy.sh restart
===========================================
EOF
}

# ---------- app ops ----------
ensure_dirs() {
  local web_root
  web_root="$(detect_web_root)"
  mkdir -p "$SCX_HOME" "$SCX_LOG_DIR" "$web_root" /etc/scx
}

git_pull() {
  [[ -d "$SCX_HOME/.git" ]] || die "不是 git 仓库: $SCX_HOME （先 clone）"
  log "git pull ($SCX_BRANCH) ..."
  git -C "$SCX_HOME" fetch --all --prune
  git -C "$SCX_HOME" checkout "$SCX_BRANCH"
  git -C "$SCX_HOME" pull --ff-only origin "$SCX_BRANCH"
  ok "版本 $(git -C "$SCX_HOME" rev-parse --short HEAD) $(git -C "$SCX_HOME" log -1 --pretty=%s)"
}

db_migrate() {
  if [[ "$SCX_RUN_DB_MIGRATE" != "true" ]]; then
    warn "跳过数据库迁移 SCX_RUN_DB_MIGRATE=$SCX_RUN_DB_MIGRATE"
    return 0
  fi
  command -v mysql >/dev/null 2>&1 || die "缺少 mysql 客户端"
  [[ -n "${MYSQL_HOST:-}" && -n "${MYSQL_USERNAME:-}" && -n "${MYSQL_PASSWORD:-}" ]] \
    || die "MYSQL_HOST/USERNAME/PASSWORD 未设置，请写入 $SCX_ENV_FILE"
  log "数据库迁移 ..."
  (
    cd "$SCX_HOME/scx-backend/db/smart-converge-x"
    export MYSQL_HOST MYSQL_PORT="${MYSQL_PORT:-3306}" MYSQL_USERNAME MYSQL_PASSWORD
    export MYSQL_PWD="$MYSQL_PASSWORD"
    if mysql --host="$MYSQL_HOST" --port="${MYSQL_PORT:-3306}" --user="$MYSQL_USERNAME" \
      --connect-timeout=10 \
      -e "USE smart_converge_x; SELECT 1 FROM scx_schema_migration LIMIT 1;" >/dev/null 2>&1; then
      SCX_SKIP_BASELINE=true SCX_MIGRATION_BASELINE_VERSION=auto ./init-database.sh
    else
      ./init-database.sh
    fi
  )
  ok "迁移完成"
}

build_backend() {
  if [[ "$SCX_SKIP_BACKEND" == "true" ]]; then
    warn "跳过后端构建"
    return 0
  fi
  command -v mvn >/dev/null 2>&1 || die "需要 mvn"
  log "构建后端 ..."
  (
    cd "$SCX_HOME/scx-backend"
    mvn -pl scx-boot -am -DskipTests package
  )
  [[ -f "$SCX_JAR" ]] || die "未找到 JAR: $SCX_JAR"
  ok "JAR $SCX_JAR"
}

build_frontend() {
  if [[ "$SCX_SKIP_FRONTEND" == "true" ]]; then
    warn "跳过前端构建"
    return 0
  fi
  command -v pnpm >/dev/null 2>&1 || die "需要 pnpm"
  local web_root
  web_root="$(detect_web_root)"
  log "构建前端 -> $web_root ..."
  (
    cd "$SCX_HOME/scx-fronted"
    corepack enable >/dev/null 2>&1 || true
    pnpm install --frozen-lockfile
    pnpm build:ele
  )
  local dist="$SCX_HOME/scx-fronted/apps/web-ele/dist"
  [[ -d "$dist" ]] || die "前端产物不存在: $dist"
  mkdir -p "$web_root"
  rsync -a --delete "$dist"/ "$web_root"/
  ok "前端已同步到 $web_root"
}

restart_backend() {
  if systemctl cat scx-boot.service >/dev/null 2>&1 \
    || [[ -f /etc/systemd/system/scx-boot.service ]]; then
    systemctl daemon-reload
    systemctl restart scx-boot
    systemctl is-active --quiet scx-boot || die "scx-boot 启动失败，见 journalctl -u scx-boot -n 80"
    ok "scx-boot restarted"
  else
    die "未安装 scx-boot.service。请先: cp $SCX_HOME/scripts/deploy/scx-boot.service /etc/systemd/system/ && systemctl enable --now scx-boot"
  fi
}

restart_xxl() {
  if systemctl cat xxl-job-admin.service >/dev/null 2>&1 \
    || [[ -f /etc/systemd/system/xxl-job-admin.service ]]; then
    systemctl restart xxl-job-admin || warn "xxl-job-admin 重启失败"
    ok "xxl-job-admin restart attempted"
  else
    warn "未安装 xxl-job-admin，跳过"
  fi
}

wait_health() {
  local i
  for ((i = 1; i <= 40; i++)); do
    if curl -fsS --max-time 3 "$SCX_HEALTH_URL" >/dev/null 2>&1; then
      ok "健康检查通过: $SCX_HEALTH_URL"
      return 0
    fi
    sleep 3
  done
  die "健康检查失败: $SCX_HEALTH_URL"
}

cmd_status() {
  echo "baota_panel: $(is_baota && echo yes || echo no)"
  echo "web_root:    $(detect_web_root)"
  echo "scx_home:    $SCX_HOME"
  if [[ -d "$SCX_HOME/.git" ]]; then
    echo "git:         $(git -C "$SCX_HOME" rev-parse --short HEAD 2>/dev/null) $(git -C "$SCX_HOME" log -1 --pretty=%s 2>/dev/null)"
  fi
  for s in scx-boot xxl-job-admin nginx; do
    printf 'service %-16s %s\n' "$s" "$(systemctl is-active "$s" 2>/dev/null || echo n/a)"
  done
  if [[ -x /www/server/nginx/sbin/nginx ]]; then
    echo "baota_nginx: present"
  fi
  curl -sS -m 3 "$SCX_HEALTH_URL" 2>&1 || true
  echo
  print_baota_checklist
}

cmd_restart() {
  restart_backend
  restart_xxl
  reload_nginx_baota
  wait_health
}

cmd_update() {
  ensure_dirs
  git_pull
  # 同步 systemd 单元（不碰宝塔 nginx vhost）
  if [[ -f "$SCX_HOME/scripts/deploy/scx-boot.service" ]]; then
    cp "$SCX_HOME/scripts/deploy/scx-boot.service" /etc/systemd/system/scx-boot.service
    systemctl daemon-reload
  fi
  db_migrate
  build_backend
  build_frontend
  restart_backend
  restart_xxl
  reload_nginx_baota
  wait_health
  ok "update 完成 $(git -C "$SCX_HOME" rev-parse --short HEAD)"
}

cmd_deploy_frontend() {
  SCX_SKIP_BACKEND=true
  SCX_RUN_DB_MIGRATE=false
  build_frontend
  reload_nginx_baota
}

main() {
  require_root
  local cmd="${1:-}"
  shift || true

  for a in "$@"; do
    case "$a" in
      --skip-frontend) SCX_SKIP_FRONTEND=true ;;
      --skip-backend) SCX_SKIP_BACKEND=true ;;
      --skip-migrate) SCX_RUN_DB_MIGRATE=false ;;
      --help|-h) usage ;;
    esac
  done

  case "$cmd" in
    status)          cmd_status ;;
    restart)         cmd_restart ;;
    reload-nginx|nginx) reload_nginx_baota ;;
    update)          cmd_update ;;
    deploy-frontend|frontend) cmd_deploy_frontend ;;
    health)          wait_health; curl -sS "$SCX_HEALTH_URL"; echo ;;
    checklist)       print_baota_checklist ;;
    -h|--help|help|"") usage ;;
    *) die "未知命令: $cmd （见 --help）" ;;
  esac
}

main "$@"
