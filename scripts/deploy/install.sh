#!/usr/bin/env bash
# SCX 首次部署脚本（在目标服务器以 root 执行）
# 用法：
#   curl 或 scp 本脚本到服务器后：
#   bash install.sh
#   或仓库已存在时：
#   bash /opt/smart-converge-x/scripts/deploy/install.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 若 common.sh 尚未落盘（首次裸机），先用内联默认值；clone 后再 source
if [[ -f "$SCRIPT_DIR/common.sh" ]]; then
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/common.sh"
else
  SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
  SCX_GIT_URL="${SCX_GIT_URL:-git@gitea.smartcute.cn:karl419/smart-converge-x.git}"
  SCX_BRANCH="${SCX_BRANCH:-main}"
  SCX_LOG_DIR="${SCX_LOG_DIR:-/var/log/scx}"
  SCX_WEB_ROOT="${SCX_WEB_ROOT:-/var/www/scx}"
  log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
  die() { log "ERROR: $*"; exit 1; }
  require_root() { [[ "$(id -u)" -eq 0 ]] || die "请使用 root 执行"; }
  ensure_dirs() { mkdir -p "$SCX_HOME" "$SCX_LOG_DIR" "$SCX_WEB_ROOT" /etc/scx; }
fi

require_root

install_os_packages() {
  log "安装系统依赖 (OpenJDK 17 / Maven / Nginx / Git / rsync / curl / mysql-client) ..."
  if command -v apt-get >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -y
    apt-get install -y \
      ca-certificates curl git rsync unzip tar \
      openjdk-17-jdk maven \
      nginx mysql-client \
      gnupg
  elif command -v yum >/dev/null 2>&1; then
    yum install -y epel-release || true
    yum install -y \
      ca-certificates curl git rsync unzip tar \
      java-17-openjdk-devel maven \
      nginx mysql \
      which
  else
    die "不支持的包管理器，请手动安装 JDK17 / Maven / Nginx / Git / Node20 / pnpm"
  fi
}

install_node() {
  if command -v node >/dev/null 2>&1; then
    local major
    major="$(node -v | sed 's/^v//' | cut -d. -f1)"
    if [[ "$major" -ge 20 ]]; then
      log "Node.js 已安装: $(node -v)"
    else
      log "Node.js 版本过低 ($(node -v))，升级到 20.x ..."
      curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
      apt-get install -y nodejs || yum install -y nodejs
    fi
  else
    log "安装 Node.js 20 ..."
    if command -v apt-get >/dev/null 2>&1; then
      curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
      apt-get install -y nodejs
    else
      # CentOS/RHEL 简易安装：使用 NodeSource 或 nvm 需按环境调整
      curl -fsSL https://rpm.nodesource.com/setup_20.x | bash -
      yum install -y nodejs
    fi
  fi
  corepack enable
  corepack prepare pnpm@10.12.4 --activate
  log "pnpm: $(pnpm -v)"
}

setup_ssh_for_gitea() {
  if [[ ! -f /root/.ssh/id_ed25519 && ! -f /root/.ssh/id_rsa ]]; then
    log "生成部署用 SSH 密钥 /root/.ssh/id_ed25519 ..."
    ssh-keygen -t ed25519 -N "" -f /root/.ssh/id_ed25519 -C "scx-deploy@$(hostname)"
    log "==== 请将以下公钥添加到 Gitea 用户 SSH Keys 或仓库部署密钥 ===="
    cat /root/.ssh/id_ed25519.pub
    log "================================================================"
    log "添加完成后重新执行本脚本。若仓库已是 HTTPS 可忽略。"
    if ! ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -T git@gitea.smartcute.cn 2>&1 | head -5; then
      log "SSH 尚未能访问 Gitea（可能未添加公钥），继续尝试 clone 将可能失败"
    fi
  else
    mkdir -p /root/.ssh
    chmod 700 /root/.ssh
    ssh-keyscan -t rsa,ecdsa,ed25519 gitea.smartcute.cn >> /root/.ssh/known_hosts 2>/dev/null || true
    sort -u /root/.ssh/known_hosts -o /root/.ssh/known_hosts
  fi
}

install_env_file() {
  if [[ ! -f /etc/scx/scx.env ]]; then
    if [[ -f "$SCX_HOME/scripts/deploy/env.example" ]]; then
      cp "$SCX_HOME/scripts/deploy/env.example" /etc/scx/scx.env
    elif [[ -f "$SCRIPT_DIR/env.example" ]]; then
      cp "$SCRIPT_DIR/env.example" /etc/scx/scx.env
    else
      # env.example 缺失时，拒绝继续：禁止生成任何内联弱默认凭据
      die "未找到 scripts/deploy/env.example；请先 clone 完整仓库或补齐 env.example；禁止由本脚本生成 MYSQL_PASSWORD 等占位"
    fi
    chmod 600 /etc/scx/scx.env
    log "已生成 /etc/scx/scx.env ，请确认 MYSQL_PASSWORD 等配置与 application-prod.yml 一致"
  else
    log "保留已有 /etc/scx/scx.env"
  fi
  # 强制：env 文件存在不等于凭据已设置；启动期会再次校验
  if [[ -f /etc/scx/scx.env ]]; then
    local required=(
      MYSQL_HOST MYSQL_PORT MYSQL_USERNAME MYSQL_PASSWORD
      SCX_DATASOURCE_URL SCX_DATASOURCE_USERNAME SCX_DATASOURCE_PASSWORD
      SCX_REDIS_HOST SCX_REDIS_PASSWORD
      SCX_ENCODE_KEY
      ROCKETMQ_NAME_SERVER
    )
    local missing=()
    local v
    for v in "${required[@]}"; do
      if ! grep -qE "^${v}=" /etc/scx/scx.env; then
        missing+=("$v")
      fi
    done
    if [[ ${#missing[@]} -gt 0 ]]; then
      log "WARN: /etc/scx/scx.env 缺少以下必需变量：${missing[*]}"
      log "WARN: 仅生成示例文件。生产启动前必须由部署负责人显式填入真实凭据。"
    fi
  fi
}

install_systemd() {
  local unit_src="$SCX_HOME/scripts/deploy/scx-boot.service"
  [[ -f "$unit_src" ]] || die "缺少 $unit_src"
  cp "$unit_src" /etc/systemd/system/scx-boot.service
  systemctl daemon-reload
  systemctl enable scx-boot
  log "已安装并 enable scx-boot.service"
}

install_nginx_site() {
  local conf_src="$SCX_HOME/scripts/deploy/nginx-scx.conf"
  [[ -f "$conf_src" ]] || die "缺少 $conf_src"
  if [[ -d /etc/nginx/conf.d ]]; then
    cp "$conf_src" /etc/nginx/conf.d/scx.conf
  elif [[ -d /etc/nginx/sites-available ]]; then
    cp "$conf_src" /etc/nginx/sites-available/scx
    ln -sfn /etc/nginx/sites-available/scx /etc/nginx/sites-enabled/scx
    rm -f /etc/nginx/sites-enabled/default
  else
    die "未找到 nginx 配置目录"
  fi
  nginx -t
  systemctl enable nginx
  systemctl restart nginx
  log "Nginx 站点已配置"
}

main() {
  log "======== SCX 首次部署开始 ========"
  ensure_dirs
  install_os_packages
  install_node
  setup_ssh_for_gitea

  # clone（若目录为空或非 git）
  if [[ ! -d "$SCX_HOME/.git" ]]; then
    if [[ -n "$(ls -A "$SCX_HOME" 2>/dev/null || true)" && ! -d "$SCX_HOME/.git" ]]; then
      die "$SCX_HOME 非空且不是 git 仓库，请清空或更换 SCX_HOME"
    fi
    log "克隆 $SCX_GIT_URL -> $SCX_HOME"
    git clone -b "$SCX_BRANCH" "$SCX_GIT_URL" "$SCX_HOME"
  fi

  # 重新加载仓库内 common.sh
  # shellcheck disable=SC1091
  source "$SCX_HOME/scripts/deploy/common.sh"

  install_env_file
  # 重新 source 用户环境
  # shellcheck disable=SC1091
  source /etc/scx/scx.env
  set -a; source /etc/scx/scx.env; set +a

  log "请确认依赖服务已就绪：MySQL / Redis / RocketMQ（application-prod.yml 中的地址）"
  read -r -p "依赖已就绪则回车继续，否则 Ctrl+C 退出 ... " _

  db_migrate
  build_backend
  build_frontend
  install_systemd
  install_nginx_site
  restart_backend
  wait_health

  log "======== 部署完成 ========"
  log "前端: http://$(hostname -I 2>/dev/null | awk '{print $1}')/"
  log "健康: curl -s $SCX_HEALTH_URL"
  log "日志: journalctl -u scx-boot -f"
  log "后续更新: bash $SCX_HOME/scripts/deploy/update.sh"
}

main "$@"
