#!/usr/bin/env bash
# SCX 一键更新并重启
# 用法（在服务器上）：
#   bash /opt/smart-converge-x/scripts/deploy/update.sh
#   SCX_SKIP_FRONTEND=true bash /opt/smart-converge-x/scripts/deploy/update.sh   # 仅后端
#   SCX_RUN_DB_MIGRATE=false bash .../update.sh                                  # 跳过迁移
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

require_root
ensure_dirs

log "======== SCX 一键更新开始 ========"
log "HOME=$SCX_HOME branch=$SCX_BRANCH profile=$SCX_PROFILE"

git_sync

# 使用最新脚本中的 common（可选）
# shellcheck disable=SC1091
source "$SCX_HOME/scripts/deploy/common.sh"
if [[ -f /etc/scx/scx.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source /etc/scx/scx.env
  set +a
fi

db_migrate
build_backend
build_frontend

# 同步 systemd / nginx 模板（若仓库有变更）
if [[ -f "$SCX_HOME/scripts/deploy/scx-boot.service" ]]; then
  cp "$SCX_HOME/scripts/deploy/scx-boot.service" /etc/systemd/system/scx-boot.service
  systemctl daemon-reload
fi
if [[ -f "$SCX_HOME/scripts/deploy/nginx-scx.conf" ]]; then
  if [[ -d /etc/nginx/conf.d ]]; then
    cp "$SCX_HOME/scripts/deploy/nginx-scx.conf" /etc/nginx/conf.d/scx.conf
  fi
fi

restart_backend
reload_nginx
wait_health

log "======== 更新完成 ========"
log "版本: $(git -C "$SCX_HOME" rev-parse --short HEAD)"
log "健康: curl -s $SCX_HEALTH_URL"
log "日志: journalctl -u scx-boot -n 50 --no-pager"
