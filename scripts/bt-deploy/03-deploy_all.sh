#!/usr/bin/env bash
# SCX 宝塔日常一键发布：安全同步 -> 全量打包 -> 前端发布 -> 运行态提示。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
bt_enable_error_trap
bt_init_paths

usage() {
  cat <<'EOF'
用法：bash scripts/bt-deploy/03-deploy_all.sh [--branch <branch>] [--discard-local] [--no-publish] [--skip-xxl]

执行安全同步、全量打包与前端发布。后端重启必须在宝塔 Java 项目管理器中完成，随后使用 02-manage.sh health 验证。
EOF
}

sync_args=(); deploy_args=(all)
while [[ $# -gt 0 ]]; do
  case "$1" in
    --branch)
      [[ $# -ge 2 ]] || bt_die '--branch 缺少分支名'
      SCX_BRANCH="$2"; sync_args+=(--branch "$2"); deploy_args+=(--branch "$2"); shift 2 ;;
    --discard-local) sync_args+=(--discard-local); shift ;;
    --no-publish|--skip-xxl) deploy_args+=("$1"); shift ;;
    -h|--help) usage; exit 0 ;;
    *) bt_die "未知参数：$1" ;;
  esac
done
bt_log '步骤 1/3：同步代码'
SCX_HOME="$SCX_HOME" SCX_BRANCH="$SCX_BRANCH" bash "${SCRIPT_DIR}/00-sync_repos.sh" "${sync_args[@]}"
bt_log '步骤 2/3：全量打包和前端发布'
SCX_HOME="$SCX_HOME" SCX_BRANCH="$SCX_BRANCH" bash "${SCRIPT_DIR}/01-deploy.sh" --no-pull "${deploy_args[@]}"
bt_log '步骤 3/3：输出宝塔重启与验证指引'
bt_print_baota_restart_hint
