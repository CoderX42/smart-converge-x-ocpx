#!/usr/bin/env bash
# 一键：同步仓库中的 Nginx 配置到系统并重载
# 用法（服务器 root）：
#   bash /opt/smart-converge-x/scripts/deploy/update-nginx.sh
#   bash /opt/smart-converge-x/scripts/deploy/update-nginx.sh --pull   # 先 git pull 再更新
set -euo pipefail

SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
SRC="${SCX_HOME}/scripts/deploy/nginx-scx.conf"
DST="/etc/nginx/conf.d/scx.conf"
DO_PULL=false

for arg in "$@"; do
  case "$arg" in
    --pull|-p) DO_PULL=true ;;
    -h|--help)
      cat <<EOF
用法: $0 [--pull]

  --pull   更新前 git pull origin main（需仓库已在 SCX_HOME）
  默认将 ${SRC} 安装为 ${DST}，nginx -t 通过后 reload。
EOF
      exit 0
      ;;
  esac
done

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

[[ "$(id -u)" -eq 0 ]] || die "请使用 root 执行"
command -v nginx >/dev/null 2>&1 || die "未安装 nginx，请先: apt-get install -y nginx 或 yum install -y nginx"

if [[ "$DO_PULL" == "true" ]]; then
  [[ -d "${SCX_HOME}/.git" ]] || die "不是 git 仓库: ${SCX_HOME}"
  log "git pull ${SCX_HOME} ..."
  git -C "$SCX_HOME" fetch --all --prune
  git -C "$SCX_HOME" pull --ff-only origin main || git -C "$SCX_HOME" pull --ff-only
fi

[[ -f "$SRC" ]] || die "缺少配置源文件: $SRC （请先 clone/pull 仓库）"

mkdir -p /etc/nginx/conf.d /var/www/scx /var/log/nginx

# 备份旧配置
if [[ -f "$DST" ]]; then
  bak="${DST}.bak.$(date +%Y%m%d%H%M%S)"
  cp -a "$DST" "$bak"
  log "已备份: $bak"
fi

cp -a "$SRC" "$DST"
log "已安装: $DST"

# 去掉常见默认站，避免抢 80
rm -f /etc/nginx/sites-enabled/default 2>/dev/null || true
rm -f /etc/nginx/conf.d/default.conf 2>/dev/null || true

log "nginx -t ..."
if ! nginx -t; then
  if [[ -n "${bak:-}" && -f "${bak:-}" ]]; then
    log "校验失败，回滚配置"
    cp -a "$bak" "$DST"
    nginx -t || true
  fi
  die "nginx 配置校验失败"
fi

if systemctl is-enabled nginx >/dev/null 2>&1 || systemctl list-unit-files | grep -q '^nginx.service'; then
  systemctl enable nginx >/dev/null 2>&1 || true
  if systemctl is-active --quiet nginx; then
    log "reload nginx"
    systemctl reload nginx
  else
    log "start nginx"
    systemctl start nginx
  fi
else
  log "systemctl 无 nginx 单元，尝试 nginx -s reload"
  if pgrep -x nginx >/dev/null 2>&1; then
    nginx -s reload
  else
    nginx
  fi
fi

log "======== Nginx 已更新并重载 ========"
log "配置: $DST"
log "测试: curl -sI http://127.0.0.1/ | head -5"
systemctl is-active nginx 2>/dev/null && log "状态: active" || log "状态: 请手动检查 nginx"
curl -sI --max-time 5 http://127.0.0.1/ 2>/dev/null | head -5 || log "本机 HTTP 探测失败（若前端未部署可忽略）"
