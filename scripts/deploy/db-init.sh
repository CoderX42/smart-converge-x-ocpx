#!/usr/bin/env bash
# 在部署服务器上初始化 / 升级 smart_converge_x 库表
#
# 用法（在服务器上，已 clone 仓库后）：
#   bash /opt/smart-converge-x/scripts/deploy/db-init.sh
#
# 或显式传参：
#   MYSQL_HOST=xxx MYSQL_USERNAME=xxx MYSQL_PASSWORD=xxx \
#     bash scripts/deploy/db-init.sh
#
# 依赖：本机已安装 mysql 客户端，且能访问内网 RDS。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DB_DIR="$REPO_ROOT/scx-backend/db/smart-converge-x"

# 优先从受限配置文件读取，再由显式环境变量覆盖；不提供连接或凭据默认值。
if [[ -f /etc/scx/scx.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source /etc/scx/scx.env
  set +a
fi

MYSQL_HOST="${MYSQL_HOST:?MYSQL_HOST 未设置}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:?MYSQL_USERNAME 未设置}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:?MYSQL_PASSWORD 未设置}"
MYSQL_DATABASE="${MYSQL_DATABASE:-smart_converge_x}"

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

command -v mysql >/dev/null 2>&1 || die "未安装 mysql 客户端。Debian/Ubuntu: apt-get install -y mysql-client"

[[ -d "$DB_DIR" ]] || die "数据库目录不存在: $DB_DIR"
[[ -x "$DB_DIR/init-database.sh" || -f "$DB_DIR/init-database.sh" ]] || die "缺少 init-database.sh"

export MYSQL_PWD="$MYSQL_PASSWORD"

log "探测 MySQL 连通性: $MYSQL_USERNAME@$MYSQL_HOST:$MYSQL_PORT"
mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
  --connect-timeout=10 \
  -e "SELECT VERSION() AS version, USER() AS user, @@hostname AS host;" \
  || die "无法连接 MySQL，请确认：1) 服务器在内网 2) 安全组/白名单放行本机 3) 账号密码正确"

log "检查/创建数据库 $MYSQL_DATABASE ..."
# 部分 RDS 账号无 CREATE 权限；有则创建，无则要求库已存在
if mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
  -e "CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" 2>/dev/null; then
  log "数据库已就绪（CREATE 成功或已存在）"
else
  log "当前账号可能无 CREATE DATABASE 权限，检查库是否已存在 ..."
  exists="$(mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
    --batch --skip-column-names \
    -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${MYSQL_DATABASE}';")"
  [[ "$exists" == "1" ]] || die "库 ${MYSQL_DATABASE} 不存在且无法创建。请用高权限账号先创建数据库并授权给 ${MYSQL_USERNAME}"
  log "库 ${MYSQL_DATABASE} 已存在"
fi

# 已有迁移历史 → 只做前向升级；全新库 → 跑 baseline + 全量迁移
history_count="$(mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
  --batch --skip-column-names \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name='scx_schema_migration';" 2>/dev/null || echo 0)"

if [[ "$history_count" == "1" ]]; then
  row_count="$(mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
    --batch --skip-column-names \
    -e "SELECT COUNT(*) FROM \`${MYSQL_DATABASE}\`.scx_schema_migration;" 2>/dev/null || echo 0)"
else
  row_count=0
fi

export MYSQL_HOST MYSQL_PORT MYSQL_USERNAME MYSQL_PASSWORD

cd "$DB_DIR"
if [[ "${row_count:-0}" -gt 0 ]]; then
  log "检测到已有迁移记录 (${row_count} 条)，执行前向升级 ..."
  SCX_SKIP_BASELINE=true SCX_MIGRATION_BASELINE_VERSION=auto bash ./init-database.sh
else
  table_count="$(mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
    --batch --skip-column-names \
    -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}';")"
  if [[ "${table_count:-0}" -gt 0 ]]; then
    log "库内已有 ${table_count} 张表但无迁移历史，按已有 schema 自动 baseline 后前向升级 ..."
    SCX_SKIP_BASELINE=true SCX_MIGRATION_BASELINE_VERSION=auto bash ./init-database.sh
  else
    log "空库，执行全量基线 + 业务迁移 (init-database.sh) ..."
    bash ./init-database.sh
  fi
fi

log "校验关键表 ..."
bash ./verify-database.sh || die "verify-database.sh 校验失败，请检查输出"

log "======== 数据库初始化完成 ========"
log "库: ${MYSQL_DATABASE}@${MYSQL_HOST}"
mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
  --batch --skip-column-names \
  -e "SELECT version, script_name, applied_at FROM \`${MYSQL_DATABASE}\`.scx_schema_migration ORDER BY applied_at DESC LIMIT 10;" \
  || true
log "默认管理员已创建；首次登录后必须立即修改默认密码"
