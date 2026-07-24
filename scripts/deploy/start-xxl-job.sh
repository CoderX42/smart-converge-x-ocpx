#!/usr/bin/env bash
# XXL-JOB Admin 一键启停
# 用法：
#   bash /opt/smart-converge-x/scripts/deploy/start-xxl-job.sh start|stop|restart|status|logs|install
set -euo pipefail

SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
cmd="${1:-}"

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

case "$cmd" in
  install)
    exec bash "${SCX_HOME}/scripts/deploy/install-xxl-job.sh" "${@:2}"
    ;;
  start)
    [[ "$(id -u)" -eq 0 ]] || die "请使用 root"
    systemctl start xxl-job-admin
    systemctl is-active --quiet xxl-job-admin || die "启动失败"
    log "xxl-job-admin started"
    curl -sI --max-time 5 http://127.0.0.1:8080/xxl-job-admin/toLogin | head -3 || true
    ;;
  stop)
    [[ "$(id -u)" -eq 0 ]] || die "请使用 root"
    systemctl stop xxl-job-admin
    log "xxl-job-admin stopped"
    ;;
  restart)
    [[ "$(id -u)" -eq 0 ]] || die "请使用 root"
    systemctl restart xxl-job-admin
    sleep 2
    systemctl is-active --quiet xxl-job-admin || die "重启失败，见 journalctl -u xxl-job-admin -n 50"
    log "xxl-job-admin restarted"
    curl -sI --max-time 5 http://127.0.0.1:8080/xxl-job-admin/toLogin | head -3 || true
    ;;
  status)
    systemctl status xxl-job-admin --no-pager || true
    ss -lntp 2>/dev/null | grep ':8080' || true
    ;;
  logs)
    journalctl -u xxl-job-admin -f
    ;;
  *)
    cat <<EOF
用法: $0 {install|start|stop|restart|status|logs}

  install   首次安装（下载构建 + 写配置 + systemd + 启动）
  start     启动
  stop      停止
  restart   重启
  status    状态
  logs      跟踪日志

控制台: http://127.0.0.1:8080/xxl-job-admin  (admin / 123456)
EOF
    exit 1
    ;;
esac
