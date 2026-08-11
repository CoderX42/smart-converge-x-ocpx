#!/usr/bin/env bash
# SCX 宝塔日常一键发布：安全同步 -> 全量打包/前端发布 -> 宝塔重启 boot/engine -> 健康检查。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
bt_enable_error_trap
bt_init_paths

usage() {
  cat <<'EOF'
用法：bash scripts/bt-deploy/03-deploy_all.sh [选项]

执行安全同步、全量打包、前端发布，并通过宝塔依次重启 boot/engine 和验证健康状态。

选项：
  --branch <name>         同步分支（默认 main）
  --discard-local         显式丢弃服务器已跟踪的本地修改
  --no-publish            不同步前端到宝塔站点根
  --skip-xxl              不构建 xxl-job-admin
  --no-restart            只打包发布，不重启 boot/engine
  --boot-project <name>   宝塔管理端项目名（默认 scx-boot）
  --engine-project <name> 宝塔引擎项目名（默认 scx-ocpx-engine）
  --timeout <seconds>     每个项目重启健康检查超时（默认 180）
EOF
}

sync_args=(); deploy_args=(all); restart_args=(restart); do_restart=true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --branch)
      [[ $# -ge 2 ]] || bt_die '--branch 缺少分支名'
      SCX_BRANCH="$2"; sync_args+=(--branch "$2"); deploy_args+=(--branch "$2"); shift 2 ;;
    --discard-local) sync_args+=(--discard-local); shift ;;
    --no-publish|--skip-xxl) deploy_args+=("$1"); shift ;;
    --no-restart) do_restart=false; shift ;;
    --boot-project|--engine-project|--timeout)
      [[ $# -ge 2 ]] || bt_die "$1 缺少参数值"
      restart_args+=("$1" "$2"); shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) bt_die "未知参数：$1" ;;
  esac
done
bt_log '步骤 1/3：同步代码'
SCX_HOME="$SCX_HOME" SCX_BRANCH="$SCX_BRANCH" bash "${SCRIPT_DIR}/00-sync_repos.sh" "${sync_args[@]}"
bt_log '步骤 2/3：全量打包和前端发布'
SCX_HOME="$SCX_HOME" SCX_BRANCH="$SCX_BRANCH" SCX_SUPPRESS_RESTART_HINT=1 \
  bash "${SCRIPT_DIR}/01-deploy.sh" --no-pull "${deploy_args[@]}"
if [[ "$do_restart" == true ]]; then
  bt_log '步骤 3/3：通过宝塔重启 boot/engine 并验证健康状态'
  SCX_HOME="$SCX_HOME" bash "${SCRIPT_DIR}/02-manage.sh" "${restart_args[@]}"
  bt_ok '一键同步、构建发布、前端更新与 boot/engine 重启全部完成。'
else
  bt_warn '步骤 3/3：已按 --no-restart 跳过 boot/engine 重启；新 JAR 尚未生效。'
  bt_print_baota_restart_hint
fi
