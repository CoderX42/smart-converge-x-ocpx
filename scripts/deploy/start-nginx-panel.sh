#!/usr/bin/env bash
# 启动 / 安装 SCX Nginx 可视化面板
# 用法：
#   bash /opt/smart-converge-x/scripts/deploy/start-nginx-panel.sh install
#   bash /opt/smart-converge-x/scripts/deploy/start-nginx-panel.sh start|stop|status|url
set -euo pipefail

SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
PANEL_ENV="/etc/scx/nginx-panel.env"
cmd="${1:-status}"

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

[[ "$(id -u)" -eq 0 ]] || die "请使用 root"

case "$cmd" in
  install)
    mkdir -p /etc/scx /var/log/scx
    if [[ ! -f "$PANEL_ENV" ]]; then
      token="$(openssl rand -hex 16 2>/dev/null || head -c 16 /dev/urandom | xxd -p)"
      cat > "$PANEL_ENV" <<EOF
# SCX Nginx 面板
SCX_NGINX_PANEL_HOST=127.0.0.1
SCX_NGINX_PANEL_PORT=9088
SCX_NGINX_PANEL_TOKEN=${token}
SCX_NGINX_CONF=/etc/nginx/conf.d/scx.conf
SCX_NGINX_TEMPLATE=${SCX_HOME}/scripts/deploy/nginx-scx.conf
EOF
      chmod 600 "$PANEL_ENV"
      log "已生成 $PANEL_ENV （含随机 token）"
    fi
    cp "${SCX_HOME}/scripts/deploy/scx-nginx-panel.service" /etc/systemd/system/
    chmod +x "${SCX_HOME}/scripts/deploy/scx-nginx-panel.py"
    systemctl daemon-reload
    systemctl enable --now scx-nginx-panel
    log "面板已启动"
    log "本机: http://127.0.0.1:9088/"
    log "访问令牌仅保存在 $PANEL_ENV；按受控方式读取后放入浏览器 URL fragment，脚本不输出令牌"
    log "推荐用 SSH 隧道（勿对公网开放 9088）："
    log "  ssh -N -L 9088:127.0.0.1:9088 root@服务器IP"
    log "  浏览器打开: http://127.0.0.1:9088/#token=<受控令牌>"
    ;;
  start)
    systemctl start scx-nginx-panel
    systemctl is-active --quiet scx-nginx-panel && log "started" || die "start failed"
    ;;
  stop)
    systemctl stop scx-nginx-panel
    log "stopped"
    ;;
  status)
    systemctl status scx-nginx-panel --no-pager || true
    ss -lntp 2>/dev/null | grep 9088 || true
    ;;
  url)
    if [[ -f "$PANEL_ENV" ]]; then
      echo "http://127.0.0.1:9088/"
    else
      die "未 install，请先: $0 install"
    fi
    ;;
  *)
    cat <<EOF
用法: $0 {install|start|stop|status|url}

  install  安装 systemd、生成 token、启动面板
  start    启动
  stop     停止
  status   状态
  url      打印不含 token 的本机访问地址
EOF
    exit 1
    ;;
esac
