#!/usr/bin/env bash
# SCX 运维快捷命令
# 用法：bash scxctl.sh {start|stop|restart|status|logs|health|version}
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

cmd="${1:-}"
case "$cmd" in
  start)
    require_root
    systemctl start scx-boot
    wait_health
    ;;
  stop)
    require_root
    systemctl stop scx-boot
    log "已停止 scx-boot"
    ;;
  restart)
    require_root
    restart_backend
    wait_health
    ;;
  status)
    systemctl status scx-boot --no-pager || true
    ;;
  logs)
    journalctl -u scx-boot -f
    ;;
  health)
    curl -fsS "$SCX_HEALTH_URL" | tee /dev/stderr; echo
    ;;
  version)
    if [[ -d "$SCX_HOME/.git" ]]; then
      git -C "$SCX_HOME" log -1 --pretty=format:'%h %ci %s%n'
    else
      die "未找到仓库: $SCX_HOME"
    fi
    ;;
  *)
    cat <<EOF
用法: $0 {start|stop|restart|status|logs|health|version}

  start    启动后端
  stop     停止后端
  restart  重启后端并健康检查
  status   systemd 状态
  logs     跟踪 journal 日志
  health   请求 actuator/health
  version  当前部署 git 版本
EOF
    exit 1
    ;;
esac
