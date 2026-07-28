#!/usr/bin/env bash
# SCX 服务器一键环境初始化（重置机 / 新机）
#
# 目标：装齐「能构建并运行本项目」所需的系统依赖与目录骨架。
# 默认不写入真实密钥、不启动业务、不覆盖已有 /etc/scx/scx.env。
#
# 用法（root）：
#   # 仅基础运行时（JDK17 / Maven / Node20 / pnpm / Nginx / 客户端工具）
#   bash bootstrap-env.sh
#
#   # 本机再装 MySQL + Redis（适合全栈单机；生产也可用云 RDS/Redis，跳过这两项）
#   bash bootstrap-env.sh --with-mysql --with-redis
#
#   # 克隆代码 + 生成 env 模板（仍需你填真实凭据）
#   bash bootstrap-env.sh --clone
#
#   # 填好 /etc/scx/scx.env 后，继续构建并安装 systemd + Nginx 站点
#   bash bootstrap-env.sh --deploy
#
#   # 组合
#   SCX_GIT_URL='git@gitea.smartcute.cn:karl419/smart-converge-x.git' \
#     bash bootstrap-env.sh --with-mysql --with-redis --clone
#
# 环境变量：
#   SCX_HOME          默认 /opt/smart-converge-x
#   SCX_GIT_URL       默认 git@gitea.smartcute.cn:karl419/smart-converge-x.git
#   SCX_BRANCH        默认 main
#   SCX_WEB_ROOT      默认 /var/www/scx
#   SCX_LOG_DIR       默认 /var/log/scx
#   NODE_MAJOR        默认 20
#   PNPM_VERSION      默认 10.12.4
#   MYSQL_ROOT_PASSWORD  仅 --with-mysql 时可选；未设则生成随机密码写入 /root/.scx-bootstrap-secrets（chmod 600）
#
set -euo pipefail

SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
SCX_GIT_URL="${SCX_GIT_URL:-git@gitea.smartcute.cn:karl419/smart-converge-x.git}"
SCX_BRANCH="${SCX_BRANCH:-main}"
SCX_WEB_ROOT="${SCX_WEB_ROOT:-/var/www/scx}"
SCX_LOG_DIR="${SCX_LOG_DIR:-/var/log/scx}"
SCX_ENV_FILE="${SCX_ENV_FILE:-/etc/scx/scx.env}"
NODE_MAJOR="${NODE_MAJOR:-20}"
PNPM_VERSION="${PNPM_VERSION:-10.12.4}"

WITH_MYSQL=false
WITH_REDIS=false
DO_CLONE=false
DO_DEPLOY=false
SKIP_NODE=false
ASSUME_YES=false

log()  { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
ok()   { log "OK  $*"; }
warn() { log "WARN $*"; }
die()  { log "ERROR: $*"; exit 1; }

usage() {
  sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

for arg in "$@"; do
  case "$arg" in
    --with-mysql) WITH_MYSQL=true ;;
    --with-redis) WITH_REDIS=true ;;
    --clone) DO_CLONE=true ;;
    --deploy) DO_DEPLOY=true ;;
    --skip-node) SKIP_NODE=true ;;
    -y|--yes) ASSUME_YES=true ;;
    -h|--help) usage ;;
    *) die "未知参数: $arg （见 --help）" ;;
  esac
done

require_root() {
  [[ "$(id -u)" -eq 0 ]] || die "请使用 root 执行（当前 uid=$(id -u)）"
}

detect_os() {
  if command -v apt-get >/dev/null 2>&1; then
    PKG=apt
  elif command -v dnf >/dev/null 2>&1; then
    PKG=dnf
  elif command -v yum >/dev/null 2>&1; then
    PKG=yum
  else
    die "不支持的包管理器（需要 apt / dnf / yum）"
  fi
  log "包管理器: $PKG"
  if [[ -f /etc/os-release ]]; then
    # shellcheck disable=SC1091
    . /etc/os-release
    log "系统: ${PRETTY_NAME:-unknown}"
  fi
}

ensure_dirs() {
  mkdir -p "$SCX_HOME" "$SCX_LOG_DIR" "$SCX_WEB_ROOT" /etc/scx /root/.ssh
  chmod 700 /root/.ssh
  chmod 750 /etc/scx
  ok "目录: $SCX_HOME $SCX_LOG_DIR $SCX_WEB_ROOT /etc/scx"
}

apt_install_base() {
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y \
    ca-certificates curl wget git rsync unzip tar gnupg lsb-release \
    build-essential \
    openjdk-17-jdk maven \
    nginx \
    mysql-client \
    redis-tools \
    net-tools iproute2 procps jq
}

yum_install_base() {
  if [[ "$PKG" == "dnf" ]]; then
    dnf install -y epel-release || true
    dnf install -y \
      ca-certificates curl wget git rsync unzip tar gnupg \
      java-17-openjdk-devel maven \
      nginx \
      mysql \
      redis \
      which procps-ng jq || true
    # redis package may pull server; client tools vary by distro
    dnf install -y redis || true
  else
    yum install -y epel-release || true
    yum install -y \
      ca-certificates curl wget git rsync unzip tar gnupg \
      java-17-openjdk-devel maven \
      nginx \
      mysql \
      which procps-ng || true
  fi
}

install_base_packages() {
  log "安装基础运行时（JDK17 / Maven / Nginx / Git / mysql-client / redis-cli）..."
  case "$PKG" in
    apt) apt_install_base ;;
    dnf|yum) yum_install_base ;;
  esac

  command -v java >/dev/null 2>&1 || die "java 未安装成功"
  command -v mvn >/dev/null 2>&1 || die "mvn 未安装成功"
  command -v nginx >/dev/null 2>&1 || die "nginx 未安装成功"
  command -v git >/dev/null 2>&1 || die "git 未安装成功"
  command -v curl >/dev/null 2>&1 || die "curl 未安装成功"

  java -version 2>&1 | head -1 | sed 's/^/[java] /'
  mvn -v 2>&1 | head -1 | sed 's/^/[mvn] /'
  nginx -v 2>&1 | sed 's/^/[nginx] /'
  ok "基础包安装完成"
}

install_node_pnpm() {
  if [[ "$SKIP_NODE" == true ]]; then
    warn "跳过 Node/pnpm (--skip-node)"
    return 0
  fi

  local need_node=true
  if command -v node >/dev/null 2>&1; then
    local major
    major="$(node -v | sed 's/^v//' | cut -d. -f1)"
    if [[ "$major" -ge "$NODE_MAJOR" ]]; then
      need_node=false
      log "Node.js 已满足: $(node -v)"
    else
      log "Node.js 过低 ($(node -v))，升级到 ${NODE_MAJOR}.x"
    fi
  fi

  if [[ "$need_node" == true ]]; then
    log "安装 Node.js ${NODE_MAJOR}.x ..."
    case "$PKG" in
      apt)
        curl -fsSL "https://deb.nodesource.com/setup_${NODE_MAJOR}.x" | bash -
        apt-get install -y nodejs
        ;;
      dnf)
        curl -fsSL "https://rpm.nodesource.com/setup_${NODE_MAJOR}.x" | bash -
        dnf install -y nodejs
        ;;
      yum)
        curl -fsSL "https://rpm.nodesource.com/setup_${NODE_MAJOR}.x" | bash -
        yum install -y nodejs
        ;;
    esac
  fi

  command -v node >/dev/null 2>&1 || die "node 未安装成功"
  command -v corepack >/dev/null 2>&1 || die "corepack 不可用（请确认 Node ${NODE_MAJOR}+）"

  corepack enable
  corepack prepare "pnpm@${PNPM_VERSION}" --activate
  command -v pnpm >/dev/null 2>&1 || die "pnpm 未激活成功"

  ok "Node $(node -v) / npm $(npm -v 2>/dev/null || echo n/a) / pnpm $(pnpm -v)"
}

random_secret() {
  # 32 bytes hex
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 16
  else
    head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n'
  fi
}

install_local_mysql() {
  [[ "$WITH_MYSQL" == true ]] || return 0
  log "安装本机 MySQL Server ..."

  case "$PKG" in
    apt)
      export DEBIAN_FRONTEND=noninteractive
      apt-get install -y mysql-server
      systemctl enable --now mysql || systemctl enable --now mysqld || true
      ;;
    dnf)
      dnf install -y mysql-server || dnf install -y community-mysql-server
      systemctl enable --now mysqld || systemctl enable --now mysql || true
      ;;
    yum)
      yum install -y mysql-server || true
      systemctl enable --now mysqld || systemctl enable --now mysql || true
      ;;
  esac

  command -v mysql >/dev/null 2>&1 || die "mysql 客户端仍不可用"

  local root_pass="${MYSQL_ROOT_PASSWORD:-}"
  local secrets_file="/root/.scx-bootstrap-secrets"
  if [[ -z "$root_pass" ]]; then
    root_pass="$(random_secret)"
    umask 077
    {
      echo "# generated by bootstrap-env.sh $(date -Iseconds)"
      echo "MYSQL_ROOT_PASSWORD=${root_pass}"
    } >> "$secrets_file"
    chmod 600 "$secrets_file"
    warn "已生成 MySQL root 密码并追加写入 ${secrets_file}"
  fi

  # 尽量用 socket 无密先设 root 密码（Ubuntu auth_socket / 新装场景）
  if mysql --protocol=socket -uroot -e "SELECT 1" >/dev/null 2>&1; then
    mysql --protocol=socket -uroot <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${root_pass}';
CREATE DATABASE IF NOT EXISTS smart_converge_x DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'scx'@'localhost' IDENTIFIED BY '${root_pass}';
CREATE USER IF NOT EXISTS 'scx'@'127.0.0.1' IDENTIFIED BY '${root_pass}';
GRANT ALL PRIVILEGES ON smart_converge_x.* TO 'scx'@'localhost';
GRANT ALL PRIVILEGES ON smart_converge_x.* TO 'scx'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
    {
      echo "MYSQL_HOST=127.0.0.1"
      echo "MYSQL_PORT=3306"
      echo "MYSQL_USERNAME=scx"
      echo "MYSQL_PASSWORD=${root_pass}"
      echo "MYSQL_DATABASE=smart_converge_x"
    } >> "$secrets_file"
    chmod 600 "$secrets_file"
    ok "本机 MySQL 已创建库 smart_converge_x 与用户 scx（凭据见 ${secrets_file}）"
  else
    warn "无法无密连接本机 MySQL root；请手动建库后把 MYSQL_* 写入 ${SCX_ENV_FILE}"
    warn "参考: CREATE DATABASE smart_converge_x DEFAULT CHARSET utf8mb4;"
  fi
}

install_local_redis() {
  [[ "$WITH_REDIS" == true ]] || return 0
  log "安装本机 Redis Server ..."

  case "$PKG" in
    apt)
      export DEBIAN_FRONTEND=noninteractive
      apt-get install -y redis-server
      # 默认只监听本机
      if [[ -f /etc/redis/redis.conf ]]; then
        sed -i 's/^supervised no/supervised systemd/' /etc/redis/redis.conf || true
        if ! grep -qE '^bind 127.0.0.1' /etc/redis/redis.conf; then
          sed -i 's/^bind .*/bind 127.0.0.1 -::1/' /etc/redis/redis.conf || true
        fi
      fi
      systemctl enable --now redis-server || systemctl enable --now redis || true
      ;;
    dnf|yum)
      if [[ "$PKG" == "dnf" ]]; then dnf install -y redis; else yum install -y redis; fi
      systemctl enable --now redis || true
      ;;
  esac

  if command -v redis-cli >/dev/null 2>&1 && redis-cli -h 127.0.0.1 ping 2>/dev/null | grep -q PONG; then
    ok "本机 Redis 可用 (127.0.0.1:6379)"
    local secrets_file="/root/.scx-bootstrap-secrets"
    umask 077
    {
      echo "SCX_REDIS_HOST=127.0.0.1"
      echo "SCX_REDIS_PORT=6379"
      echo "SCX_REDIS_PASSWORD="
      echo "SCX_REDIS_DATABASE=0"
    } >> "$secrets_file"
    chmod 600 "$secrets_file"
  else
    warn "Redis 安装后未能 PING 成功，请检查 systemctl status redis"
  fi
}

setup_deploy_ssh_key() {
  if [[ ! -f /root/.ssh/id_ed25519 && ! -f /root/.ssh/id_rsa ]]; then
    log "生成部署 SSH 密钥 /root/.ssh/id_ed25519 ..."
    ssh-keygen -t ed25519 -N "" -f /root/.ssh/id_ed25519 -C "scx-deploy@$(hostname -s 2>/dev/null || hostname)"
  fi
  if [[ -f /root/.ssh/id_ed25519.pub ]]; then
    log "==== 服务器部署公钥（加入 Gitea Deploy Key / 用户 SSH Key）===="
    cat /root/.ssh/id_ed25519.pub
    log "================================================================"
  fi
}

clone_repo() {
  [[ "$DO_CLONE" == true ]] || return 0

  if [[ -d "$SCX_HOME/.git" ]]; then
    log "仓库已存在: $SCX_HOME ($(git -C "$SCX_HOME" rev-parse --short HEAD 2>/dev/null || echo unknown))"
    return 0
  fi

  if [[ -n "$(ls -A "$SCX_HOME" 2>/dev/null || true)" ]]; then
    die "$SCX_HOME 非空且不是 git 仓库，请清空或设置 SCX_HOME"
  fi

  setup_deploy_ssh_key

  # 尝试写入常见 Gitea host key（失败不阻断；HTTPS clone 可跳过）
  if [[ "$SCX_GIT_URL" == git@* || "$SCX_GIT_URL" == ssh://* ]]; then
    local host
    host="$(printf '%s' "$SCX_GIT_URL" | sed -E 's#^git@([^:]+):.*#\1#; s#^ssh://[^@]+@([^:/]+).*#\1#')"
    if [[ -n "$host" ]]; then
      ssh-keyscan -t rsa,ecdsa,ed25519 "$host" >> /root/.ssh/known_hosts 2>/dev/null || true
      sort -u /root/.ssh/known_hosts -o /root/.ssh/known_hosts || true
    fi
  fi

  log "克隆 $SCX_GIT_URL ($SCX_BRANCH) -> $SCX_HOME"
  if ! git clone -b "$SCX_BRANCH" "$SCX_GIT_URL" "$SCX_HOME"; then
    die "git clone 失败。若是 SSH 私有库，请先把上方公钥加入 Gitea 后重试；或改用 HTTPS: SCX_GIT_URL=https://..."
  fi
  ok "代码: $(git -C "$SCX_HOME" log -1 --oneline)"
}

install_env_template() {
  if [[ -f "$SCX_ENV_FILE" ]]; then
    log "保留已有 $SCX_ENV_FILE"
    return 0
  fi

  local example=""
  if [[ -f "$SCX_HOME/scripts/deploy/env.example" ]]; then
    example="$SCX_HOME/scripts/deploy/env.example"
  elif [[ -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.example" ]]; then
    example="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/env.example"
  fi

  if [[ -n "$example" ]]; then
    cp "$example" "$SCX_ENV_FILE"
  else
    # 最小可编辑模板（无弱密码默认值）
    cat > "$SCX_ENV_FILE" <<'EOF'
SCX_HOME=/opt/smart-converge-x
SCX_BRANCH=main
SCX_PROFILE=prod
SCX_JAVA_OPTS=-Xms512m -Xmx1024m -Dfile.encoding=UTF-8
SCX_JAR=/opt/smart-converge-x/scx-backend/scx-boot/target/scx-boot.jar
SCX_LOG_DIR=/var/log/scx
SCX_HEALTH_URL=http://127.0.0.1:9999/boot/actuator/health
SCX_WEB_ROOT=/var/www/scx

SCX_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/smart_converge_x?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia%2FShanghai&allowMultiQueries=true
SCX_DATASOURCE_USERNAME=replace-me
SCX_DATASOURCE_PASSWORD=replace-me
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_USERNAME=replace-me
MYSQL_PASSWORD=replace-me
MYSQL_DATABASE=smart_converge_x

SCX_REDIS_HOST=127.0.0.1
SCX_REDIS_PORT=6379
SCX_REDIS_USERNAME=
SCX_REDIS_PASSWORD=replace-me
SCX_REDIS_DATABASE=0

ROCKETMQ_NAME_SERVER=127.0.0.1:9876
SCX_ROCKETMQ_PRODUCER_GROUP=producer_group

SCX_XXL_JOB_ADMIN_ADDRESSES=http://127.0.0.1:8080
SCX_XXL_JOB_ACCESS_TOKEN=replace-me
SCX_XXL_JOB_EXECUTOR_PORT=9009
SCX_ENCODE_KEY=replace-me
SCX_TRACKING_CHANNEL_MASTER_KEY=replace-with-base64-32-byte-key
SCX_TRACKING_DEVICE_HMAC_KEY=replace-with-base64-at-least-32-byte-key
SCX_TRACKING_OUTBOX_PUBLISHER_ENABLED=false
SCX_TRACKING_OUTBOX_SCHEDULER_ENABLED=false

SCX_RUN_DB_MIGRATE=true
SCX_SKIP_FRONTEND=false
SCX_SKIP_BACKEND=false
EOF
  fi
  chmod 600 "$SCX_ENV_FILE"
  ok "已生成 env 模板: $SCX_ENV_FILE （请填入真实凭据，禁止提交到 Git）"
}

# 若本机装了 MySQL/Redis 且 secrets 文件有值，尝试把非 replace 值合并进 scx.env 的空/占位字段
merge_local_secrets_hint() {
  local secrets_file="/root/.scx-bootstrap-secrets"
  [[ -f "$secrets_file" && -f "$SCX_ENV_FILE" ]] || return 0
  warn "本机中间件凭据已写入 ${secrets_file}；请人工合并到 ${SCX_ENV_FILE}（脚本不自动覆写已有 env）"
}

verify_toolchain() {
  log "======== 工具链自检 ========"
  local failed=0
  check_cmd() {
    if command -v "$1" >/dev/null 2>&1; then
      ok "$1: $($2)"
    else
      warn "缺少: $1"
      failed=1
    fi
  }
  check_cmd java  "java -version 2>&1 | head -1"
  check_cmd mvn   "mvn -v 2>&1 | head -1"
  check_cmd git   "git --version"
  check_cmd nginx "nginx -v 2>&1"
  check_cmd curl  "curl --version | head -1"
  check_cmd rsync "rsync --version | head -1"
  if [[ "$SKIP_NODE" != true ]]; then
    check_cmd node "node -v"
    check_cmd pnpm "pnpm -v"
  fi
  if command -v mysql >/dev/null 2>&1; then ok "mysql client: $(mysql --version)"; else warn "缺少 mysql 客户端"; failed=1; fi
  if command -v redis-cli >/dev/null 2>&1; then ok "redis-cli: $(redis-cli --version)"; else warn "缺少 redis-cli"; fi

  [[ $failed -eq 0 ]] || die "工具链自检未通过"
}

maybe_deploy() {
  [[ "$DO_DEPLOY" == true ]] || return 0

  [[ -d "$SCX_HOME/.git" ]] || die "--deploy 需要先 --clone 或已有 $SCX_HOME 仓库"
  [[ -f "$SCX_ENV_FILE" ]] || die "缺少 $SCX_ENV_FILE"

  # 拒绝占位符启动
  local bad=0
  local k
  for k in SCX_DATASOURCE_URL SCX_DATASOURCE_USERNAME SCX_DATASOURCE_PASSWORD \
           SCX_REDIS_HOST SCX_ENCODE_KEY ROCKETMQ_NAME_SERVER; do
    local val
    val="$(grep -E "^${k}=" "$SCX_ENV_FILE" | head -1 | cut -d= -f2- || true)"
    if [[ -z "$val" || "$val" == replace* || "$val" == *mysql-host* || "$val" == *redis-host* || "$val" == *rocketmq-host* ]]; then
      warn "env 仍是占位/空: $k"
      bad=1
    fi
  done
  [[ $bad -eq 0 ]] || die "请先编辑 $SCX_ENV_FILE 填入真实配置后再 --deploy"

  if [[ "$ASSUME_YES" != true ]]; then
    read -r -p "将执行迁移/构建/安装 systemd+Nginx 并启动 scx-boot，确认? [y/N] " ans
    [[ "${ans:-}" == "y" || "${ans:-}" == "Y" ]] || die "用户取消 --deploy"
  fi

  local installer="$SCX_HOME/scripts/deploy/install.sh"
  [[ -f "$installer" ]] || die "缺少 $installer"
  log "调用仓库 install.sh 完成应用部署 ..."
  # install.sh 内有交互 read；用 yes 回车跳过依赖确认（用户已通过 env 检查）
  if [[ "$ASSUME_YES" == true ]]; then
    printf '\n' | bash "$installer"
  else
    bash "$installer"
  fi
}

print_next_steps() {
  cat <<EOF

======== SCX 环境初始化完成 ========

已具备:
  - OpenJDK 17 + Maven（后端构建/运行）
  - Node ${NODE_MAJOR}+ / pnpm ${PNPM_VERSION}（前端构建）
  - Nginx（静态 + 反代）
  - Git / curl / rsync / mysql-client / redis-cli
  - 目录: ${SCX_HOME}  ${SCX_WEB_ROOT}  ${SCX_LOG_DIR}  /etc/scx

中间件说明:
  - MySQL / Redis: 可用云 RDS/云 Redis（生产推荐），或本脚本 --with-mysql --with-redis
  - RocketMQ: 需单独安装（仓库 scripts/deploy/install-rocketmq.sh，需提供官方 zip + SHA 校验）
  - XXL-JOB: 需单独安装（scripts/deploy/install-xxl-job.sh）

下一步建议:
  1) 编辑配置:
       nano ${SCX_ENV_FILE}
     必填: SCX_DATASOURCE_*  MYSQL_*  SCX_REDIS_*  ROCKETMQ_NAME_SERVER
           SCX_ENCODE_KEY  SCX_TRACKING_CHANNEL_MASTER_KEY  SCX_TRACKING_DEVICE_HMAC_KEY
           SCX_XXL_JOB_ACCESS_TOKEN

  2) 若尚未拉代码:
       bash $0 --clone
     或:
       git clone -b ${SCX_BRANCH} ${SCX_GIT_URL} ${SCX_HOME}

  3) 依赖就绪后一键部署应用:
       bash ${SCX_HOME}/scripts/deploy/install.sh
     或（env 已填真实值）:
       bash $0 --deploy -y

  4) 日常运维（线上标准：宝塔 + bash）:
       # 宝塔：建站 + 伪静态粘贴 nginx-scx-baota-rewrite.conf
       bash ${SCX_HOME}/scripts/deploy/baota-deploy.sh status
       bash ${SCX_HOME}/scripts/deploy/baota-deploy.sh update
       bash ${SCX_HOME}/scripts/deploy/baota-deploy.sh restart
       bash ${SCX_HOME}/scripts/deploy/scxctl.sh logs

本机中间件若由本脚本安装，凭据可能在:
  /root/.scx-bootstrap-secrets   (chmod 600，勿外泄)

====================================
EOF
}

main() {
  log "======== SCX bootstrap-env 开始 ========"
  require_root
  detect_os
  ensure_dirs
  install_base_packages
  install_node_pnpm
  install_local_mysql
  install_local_redis
  clone_repo
  install_env_template
  merge_local_secrets_hint
  verify_toolchain
  maybe_deploy
  print_next_steps
  log "======== SCX bootstrap-env 结束 ========"
}

main "$@"
