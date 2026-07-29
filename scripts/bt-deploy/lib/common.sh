#!/usr/bin/env bash
# Shared helpers for the SCX Baota deployment entry points.

if [[ "${SCX_BT_COMMON_LOADED:-}" == '1' ]]; then
  return 0
fi
readonly SCX_BT_COMMON_LOADED=1

readonly BT_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly BT_SCRIPT_DIR="$(cd "${BT_LIB_DIR}/.." && pwd)"
readonly BT_REPO_GUESS="$(cd "${BT_SCRIPT_DIR}/../.." && pwd)"

bt_log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
bt_ok() { printf '[%s] OK  %s\n' "$(date '+%F %T')" "$*"; }
bt_warn() { printf '[%s] WARN %s\n' "$(date '+%F %T')" "$*" >&2; }
bt_die() { printf '[%s] ERROR %s\n' "$(date '+%F %T')" "$*" >&2; exit 1; }

bt_on_error() {
  local exit_code="$1" line="$2" command="$3"
  printf '[%s] ERROR command failed (exit=%s, line=%s): %s\n' \
    "$(date '+%F %T')" "$exit_code" "$line" "$command" >&2
  exit "$exit_code"
}

bt_enable_error_trap() { trap 'bt_on_error "$?" "$LINENO" "$BASH_COMMAND"' ERR; }
bt_require_command() { command -v "$1" >/dev/null 2>&1 || bt_die "缺少命令：$1"; }

bt_init_paths() {
  if [[ -d "${BT_REPO_GUESS}/scx-backend" ]]; then
    SCX_HOME="${SCX_HOME:-${BT_REPO_GUESS}}"
  else
    SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
  fi
  SCX_BRANCH="${SCX_BRANCH:-${BRANCH:-main}}"
  SCX_BUILD_DIR="${SCX_BUILD_DIR:-${SCX_HOME}/build}"
  SCX_BACKUP_DIR="${SCX_BACKUP_DIR:-${SCX_HOME}/backup}"
  SCX_BACKUP_KEEP="${SCX_BACKUP_KEEP:-5}"
  # Keep the old variable name working for existing Baota shell environments.
  SCX_WEB_ROOT="${SCX_WEB_ROOT:-${SCX_BAOTA_SITE_ROOT:-/www/wwwroot/scx}}"
  SCX_LOG_DIR="${SCX_LOG_DIR:-/www/wwwlogs/scx}"
  export SCX_HOME SCX_BRANCH SCX_BUILD_DIR SCX_BACKUP_DIR SCX_BACKUP_KEEP SCX_WEB_ROOT SCX_LOG_DIR
}

bt_assert_repository() {
  [[ -d "${SCX_HOME}/.git" ]] || bt_die "不是 Git 仓库：${SCX_HOME}；请先执行 00-sync_repos.sh"
  [[ -d "${SCX_HOME}/scx-backend" ]] || bt_die "缺少后端目录：${SCX_HOME}/scx-backend"
  [[ -d "${SCX_HOME}/scx-fronted" ]] || bt_die "缺少前端目录：${SCX_HOME}/scx-fronted"
}

bt_detect_java() {
  local candidate java_version
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    candidate="$JAVA_HOME"
  else
    candidate=''
    for candidate in /www/server/java/jdk-17* /www/server/java/jdk-21* /usr/lib/jvm/java-17* /usr/lib/jvm/java-21*; do
      [[ -x "${candidate}/bin/java" ]] && break
      candidate=''
    done
    if [[ -z "$candidate" ]] && command -v java >/dev/null 2>&1; then
      candidate="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
    fi
  fi
  [[ -n "${candidate:-}" && -x "${candidate}/bin/java" ]] || bt_die '未检测到 JDK 17+；请先在宝塔安装 Java 17。'
  java_version="$(${candidate}/bin/java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
  [[ "${java_version:-0}" -ge 17 ]] || bt_die "JDK 版本过低：${java_version:-unknown}（需要 17+）"
  JAVA_HOME="$candidate"
  export JAVA_HOME PATH="${JAVA_HOME}/bin:${PATH}"
  bt_ok "JDK ${java_version}：${JAVA_HOME}"
}

bt_prepare_node() {
  bt_require_command node
  bt_require_command corepack
  corepack enable >/dev/null 2>&1 || bt_die 'corepack enable 失败；请检查 Node.js 安装。'
  bt_require_command pnpm
  bt_ok "Node $(node --version)，pnpm $(pnpm --version)"
}

bt_release_id() { date '+%Y%m%d_%H%M%S'; }

bt_prune_backups() {
  local keep="${SCX_BACKUP_KEEP:-5}"
  local backup_dir="${SCX_BACKUP_DIR}"
  local index=0 path name

  if [[ ! "$keep" =~ ^[1-9][0-9]*$ ]]; then
    bt_warn "SCX_BACKUP_KEEP 无效：${keep}；跳过备份清理。"
    return 0
  fi
  [[ -d "$backup_dir" ]] || return 0

  # 只处理部署脚本生成的时间戳目录，避免误删 backup 下的其他内容。
  while IFS= read -r path; do
    name="${path##*/}"
    [[ "$name" =~ ^[0-9]{8}_[0-9]{6}$ ]] || continue
    index=$((index + 1))
    (( index <= keep )) && continue
    if rm -rf -- "$path"; then
      bt_log "已清理旧发布备份：${path}"
    else
      bt_warn "清理旧发布备份失败：${path}"
    fi
  done < <(find "$backup_dir" -mindepth 1 -maxdepth 1 -type d -print | sort -r)
}

bt_print_baota_restart_hint() {
  cat <<EOF
后端 JAR 已就绪：${SCX_BUILD_DIR}/backend/scx-boot.jar
请在宝塔「Java 项目管理器」中重启对应 SCX 项目；本脚本不会绕过宝塔直接管理 Java 进程。
重启后执行：bash ${SCX_HOME}/scripts/bt-deploy/02-manage.sh health
EOF
}
