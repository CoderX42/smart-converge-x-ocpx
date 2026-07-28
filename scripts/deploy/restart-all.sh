#!/usr/bin/env bash
# SCX 线上服务一键重启（后端 + XXL-JOB + Nginx/前端静态）
#
# 用法（服务器 root）：
#   bash /opt/smart-converge-x/scripts/deploy/restart-all.sh
#   bash /opt/smart-converge-x/scripts/deploy/restart-all.sh --build   # 先 git pull + 构建后端/前端再重启
#   bash /opt/smart-converge-x/scripts/deploy/restart-all.sh --pull    # 仅 pull 后重启（不构建）
#
# 说明：
#   - 后端：systemctl restart scx-boot
#   - XXL-JOB：systemctl restart xxl-job-admin（未安装则跳过并提示）
#   - 前端：静态文件在 /var/www/scx，由 Nginx 托管；默认 reload nginx
#           --build 时会重新构建后端/前端；XXL-JOB JAR 禁止在生产机编译，必须单独上传校验
set -euo pipefail

SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
SCX_ENV_FILE="${SCX_ENV_FILE:-/etc/scx/scx.env}"
DO_PULL=false
DO_BUILD=false

for arg in "$@"; do
  case "$arg" in
    --pull|-p) DO_PULL=true ;;
    --build|-b) DO_BUILD=true; DO_PULL=true ;;
    -h|--help)
      cat <<'EOF'
用法: restart-all.sh [--pull] [--build]

  (无参数)   仅重启：scx-boot + xxl-job-admin + nginx reload
  --pull     先 git pull，再重启
  --build    先 pull，再构建后端 jar / 前端 dist，再重启（不构建 XXL-JOB）

单独组件：
  systemctl restart scx-boot
  systemctl restart xxl-job-admin
  systemctl reload nginx
  bash /opt/smart-converge-x/scripts/deploy/update-nginx.sh
EOF
      exit 0
      ;;
  esac
done

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
die() { log "ERROR: $*"; exit 1; }
ok()  { log "OK  $*"; }
warn(){ log "WARN $*"; }

[[ "$(id -u)" -eq 0 ]] || die "请使用 root 执行"

if [[ -f "$SCX_ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$SCX_ENV_FILE"
  set +a
fi

SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
SCX_WEB_ROOT="${SCX_WEB_ROOT:-/var/www/scx}"
SCX_HEALTH_URL="${SCX_HEALTH_URL:-http://127.0.0.1:9999/boot/actuator/health}"
SCX_JAR="${SCX_JAR:-${SCX_HOME}/scx-backend/scx-boot/target/scx-boot.jar}"

# ---------- pull / build ----------
if [[ "$DO_PULL" == "true" ]]; then
  [[ -d "${SCX_HOME}/.git" ]] || die "不是 git 仓库: $SCX_HOME"
  log "git pull ..."
  git -C "$SCX_HOME" fetch --all --prune
  git -C "$SCX_HOME" pull --ff-only origin main || git -C "$SCX_HOME" pull --ff-only
  log "版本: $(git -C "$SCX_HOME" rev-parse --short HEAD) $(git -C "$SCX_HOME" log -1 --pretty=%s)"
fi

if [[ "$DO_BUILD" == "true" ]]; then
  command -v mvn >/dev/null 2>&1 || die "需要 mvn"
  log "构建后端 scx-boot ..."
  (
    cd "${SCX_HOME}/scx-backend"
    mvn -pl scx-boot -am -DskipTests package
  )
  [[ -f "$SCX_JAR" ]] || die "未找到 $SCX_JAR"

  if command -v pnpm >/dev/null 2>&1; then
    log "构建前端 web-ele ..."
    (
      cd "${SCX_HOME}/scx-fronted"
      corepack enable >/dev/null 2>&1 || true
      pnpm install --frozen-lockfile
      pnpm build:ele
    )
    dist="${SCX_HOME}/scx-fronted/apps/web-ele/dist"
    [[ -d "$dist" ]] || die "前端产物不存在: $dist"
    mkdir -p "$SCX_WEB_ROOT"
    rsync -a --delete "${dist}/" "${SCX_WEB_ROOT}/"
    ok "前端已同步到 $SCX_WEB_ROOT"
  else
    warn "未安装 pnpm，跳过前端构建（仅重启 Nginx）"
  fi

fi

unit_exists() {
  # 兼容不同 systemctl 输出；以 unit 文件或 cat 为准
  local name="$1"
  [[ -f "/etc/systemd/system/${name}.service" ]] \
    || [[ -f "/lib/systemd/system/${name}.service" ]] \
    || [[ -f "/usr/lib/systemd/system/${name}.service" ]] \
    || systemctl cat "${name}.service" >/dev/null 2>&1
}

# ---------- restart backend ----------
if unit_exists scx-boot; then
  log "重启 scx-boot ..."
  systemctl daemon-reload
  systemctl restart scx-boot
  sleep 2
  systemctl is-active --quiet scx-boot || die "scx-boot 启动失败，见: journalctl -u scx-boot -n 50"
  ok "scx-boot active"
else
  die "未找到 scx-boot.service，请先 install.sh"
fi

# ---------- restart xxl-job ----------
if unit_exists xxl-job-admin; then
  log "重启 xxl-job-admin ..."
  systemctl daemon-reload
  systemctl restart xxl-job-admin
  sleep 2
  if systemctl is-active --quiet xxl-job-admin; then
    ok "xxl-job-admin active"
  else
    warn "xxl-job-admin 重启后未 active，见: journalctl -u xxl-job-admin -n 50"
  fi
else
  warn "未安装 xxl-job-admin.service，跳过"
  warn "首次安装: bash ${SCX_HOME}/scripts/deploy/install-xxl-job.sh"
  warn "生产 JAR 必须由构建机上传并通过 --jar-sha256 校验，禁止在此机下载或构建"
fi

# ---------- nginx / frontend ----------
if command -v nginx >/dev/null 2>&1; then
  log "重载 Nginx（前端静态）..."
  nginx -t
  if systemctl is-active --quiet nginx; then
    systemctl reload nginx
  else
    systemctl start nginx
  fi
  ok "nginx reloaded"
else
  warn "未安装 nginx"
fi

# ---------- health ----------
log "健康检查 ..."
boot_ok=false
for i in $(seq 1 30); do
  if curl -fsS --max-time 3 "$SCX_HEALTH_URL" >/dev/null 2>&1; then
    boot_ok=true
    break
  fi
  sleep 2
done
if [[ "$boot_ok" == "true" ]]; then
  ok "后端 health: $SCX_HEALTH_URL"
else
  warn "后端 health 超时: $SCX_HEALTH_URL"
fi

if curl -fsS --max-time 3 -o /dev/null -w '' http://127.0.0.1/ 2>/dev/null; then
  ok "前端入口: http://127.0.0.1/ -> $(curl -sI --max-time 3 http://127.0.0.1/ | head -1 | tr -d '\r')"
else
  warn "前端 HTTP 探测失败"
fi

if systemctl is-active --quiet xxl-job-admin 2>/dev/null; then
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8080/ 2>/dev/null || echo 000)"
  ok "xxl-job-admin HTTP $code (http://127.0.0.1:8080/)"
fi

log "======== 重启完成 ========"
log "状态速览:"
printf '  %-16s %s\n' scx-boot "$(systemctl is-active scx-boot 2>&1)"
printf '  %-16s %s\n' xxl-job-admin "$(systemctl is-active xxl-job-admin 2>&1)"
printf '  %-16s %s\n' nginx "$(systemctl is-active nginx 2>&1)"
log "版本: $(git -C "$SCX_HOME" rev-parse --short HEAD 2>/dev/null || echo n/a)"
