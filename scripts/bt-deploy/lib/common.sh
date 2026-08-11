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
  SCX_BT_BOOT_PROJECT="${SCX_BT_BOOT_PROJECT:-scx-boot}"
  SCX_BT_ENGINE_PROJECT="${SCX_BT_ENGINE_PROJECT:-scx-ocpx-engine}"
  SCX_RESTART_TIMEOUT="${SCX_RESTART_TIMEOUT:-180}"
  SCX_BAOTA_RESTART_SCRIPT="${SCX_BAOTA_RESTART_SCRIPT:-/www/server/panel/script/restart_project.py}"
  export SCX_HOME SCX_BRANCH SCX_BUILD_DIR SCX_BACKUP_DIR SCX_BACKUP_KEEP SCX_WEB_ROOT SCX_LOG_DIR
  export SCX_BT_BOOT_PROJECT SCX_BT_ENGINE_PROJECT SCX_RESTART_TIMEOUT SCX_BAOTA_RESTART_SCRIPT
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

bt_validate_project_name() {
  local project_name="$1"
  [[ "$project_name" =~ ^[A-Za-z0-9._-]+$ ]] \
    || bt_die "宝塔 Java 项目名不安全或为空：${project_name:-<empty>}"
}

bt_baota_python() {
  if command -v btpython >/dev/null 2>&1; then
    command -v btpython
  elif [[ -x /www/server/panel/pyenv/bin/python3 ]]; then
    printf '%s\n' /www/server/panel/pyenv/bin/python3
  elif [[ -x /www/server/panel/pyenv/bin/python ]]; then
    printf '%s\n' /www/server/panel/pyenv/bin/python
  else
    return 1
  fi
}

bt_find_jar_pids() {
  local jar_path="$1" cmdline_file process_cmdline pid
  [[ -d /proc ]] || return 0

  for cmdline_file in /proc/[0-9]*/cmdline; do
    [[ -r "$cmdline_file" ]] || continue
    process_cmdline="$(tr '\0' ' ' < "$cmdline_file" 2>/dev/null || true)"
    [[ "$process_cmdline" == *"$jar_path"* ]] || continue
    pid="${cmdline_file#/proc/}"
    printf '%s\n' "${pid%/cmdline}"
  done
}

bt_get_single_jar_pid() {
  local jar_path="$1" component="$2" pid_list pid_count
  pid_list="$(bt_find_jar_pids "$jar_path" | sed '/^$/d')"
  [[ -n "$pid_list" ]] || return 0

  pid_count="$(printf '%s\n' "$pid_list" | wc -l | tr -d '[:space:]')"
  [[ "$pid_count" == '1' ]] \
    || bt_die "${component} 检测到 ${pid_count} 个使用目标 JAR 的进程，拒绝自动重启：${pid_list//$'\n'/,}"
  printf '%s\n' "$pid_list"
}

bt_wait_for_restarted_health() {
  local component="$1" jar_path="$2" old_pid="$3" health_url="$4" timeout="$5"
  local started_at now current_pid response
  started_at="$(date +%s)"

  while true; do
    current_pid="$(bt_get_single_jar_pid "$jar_path" "$component")"
    if [[ -n "$current_pid" && ( -z "$old_pid" || "$current_pid" != "$old_pid" ) ]]; then
      if response="$(curl --fail --silent --show-error --max-time 10 "$health_url" 2>/dev/null)"; then
        bt_ok "${component} 已由宝塔重启：PID ${current_pid}，健康检查 ${response}"
        return 0
      fi
    fi

    now="$(date +%s)"
    if (( now - started_at >= timeout )); then
      bt_die "${component} 重启后 ${timeout}s 内未出现新进程并通过健康检查；请到宝塔 Java 项目管理器查看项目日志。"
    fi
    sleep 2
  done
}

bt_restart_java_project() {
  local component="$1" project_name="$2" jar_path="$3" health_url="$4"
  local panel_python old_pid restart_output

  bt_validate_project_name "$project_name"
  [[ "$SCX_RESTART_TIMEOUT" =~ ^[1-9][0-9]*$ ]] \
    || bt_die "SCX_RESTART_TIMEOUT 必须为正整数：${SCX_RESTART_TIMEOUT}"
  [[ -f "$jar_path" ]] || bt_die "${component} 目标 JAR 不存在：${jar_path}"
  [[ -f "$SCX_BAOTA_RESTART_SCRIPT" ]] \
    || bt_die "未找到宝塔项目重启入口：${SCX_BAOTA_RESTART_SCRIPT}；请先升级/修复宝塔面板，或使用 --no-restart。"
  panel_python="$(bt_baota_python)" \
    || bt_die '未找到宝塔 Python 运行时（btpython 或 panel/pyenv/bin/python）。'
  bt_require_command curl

  old_pid="$(bt_get_single_jar_pid "$jar_path" "$component")"
  bt_log "通过宝塔 Java 项目管理器重启 ${component}：${project_name}（旧 PID：${old_pid:-未运行}）"
  if ! restart_output="$("$panel_python" "$SCX_BAOTA_RESTART_SCRIPT" java "$project_name" 2>&1)"; then
    [[ -z "$restart_output" ]] || printf '%s\n' "$restart_output" >&2
    bt_die "宝塔重启 ${component} 命令执行失败。"
  fi
  [[ -z "$restart_output" ]] || printf '%s\n' "$restart_output"
  if printf '%s\n' "$restart_output" | grep -Eiq 'failed|error|失败|不存在|does not exist'; then
    bt_die "宝塔未能重启 ${component}；请核对项目名 ${project_name} 和宝塔项目配置。"
  fi

  bt_wait_for_restarted_health "$component" "$jar_path" "$old_pid" "$health_url" "$SCX_RESTART_TIMEOUT"
}

bt_restart_scx_java() {
  bt_restart_java_project \
    '管理端 scx-boot' "$SCX_BT_BOOT_PROJECT" \
    "${SCX_BUILD_DIR}/backend/scx-boot.jar" \
    'http://127.0.0.1:9999/boot/actuator/health'
  bt_restart_java_project \
    '数据面 scx-ocpx-engine' "$SCX_BT_ENGINE_PROJECT" \
    "${SCX_BUILD_DIR}/backend/scx-ocpx-engine.jar" \
    'http://127.0.0.1:10000/ocpx/actuator/health'
}

bt_print_baota_restart_hint() {
  cat <<EOF
后端 JAR 已就绪：${SCX_BUILD_DIR}/backend/scx-boot.jar
引擎 JAR 已就绪：${SCX_BUILD_DIR}/backend/scx-ocpx-engine.jar
使用宝塔项目管理能力重启并验证：bash ${SCX_HOME}/scripts/bt-deploy/02-manage.sh restart
日常一键发布入口会自动完成上述重启；如项目名不同，请设置 SCX_BT_BOOT_PROJECT / SCX_BT_ENGINE_PROJECT。
EOF
}
