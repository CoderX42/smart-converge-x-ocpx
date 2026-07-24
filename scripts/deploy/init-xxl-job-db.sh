#!/usr/bin/env bash
# 在云 MySQL 中初始化独立库 xxl_job（官方 tables_xxl_job.sql）
# 用法（建议在能访问内网 RDS 的服务器上执行）：
#   bash /opt/smart-converge-x/scripts/deploy/init-xxl-job-db.sh
set -euo pipefail

SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
if [[ -f /etc/scx/scx.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source /etc/scx/scx.env
  set +a
fi

MYSQL_HOST="${MYSQL_HOST:-mysqle379be1ffea9.rds.ivolces.com}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-ocpxuser}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-OqS5ZRBU1wBuEqLeHAQ3}"
SQL_FILE="${1:-${SCX_HOME}/xxl-job/doc/db/tables_xxl_job.sql}"

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

[[ -f "$SQL_FILE" ]] || die "SQL 文件不存在: $SQL_FILE"
command -v mysql >/dev/null 2>&1 || die "需要 mysql 客户端"
export MYSQL_PWD="$MYSQL_PASSWORD"

log "连接 ${MYSQL_USERNAME}@${MYSQL_HOST}:${MYSQL_PORT}"
mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
  --connect-timeout=10 -e "SELECT VERSION();" >/dev/null

log "导入 $SQL_FILE （CREATE DATABASE xxl_job + 表结构）"
mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
  --default-character-set=utf8mb4 < "$SQL_FILE"

log "校验"
mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" -e "
SHOW TABLES FROM xxl_job;
SELECT id, username, role FROM xxl_job.xxl_job_user;
SELECT id, app_name, title FROM xxl_job.xxl_job_group;
"
log "完成。库名: xxl_job ；控制台默认账号 admin（密码见官方文档，当前种子为 SHA256 哈希）"
