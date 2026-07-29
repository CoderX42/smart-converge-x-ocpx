#!/usr/bin/env bash
# 本地/受控构建机推送入口；不会提交代码或绕过 Git 审核。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
bt_enable_error_trap
bt_init_paths

usage() {
  cat <<'EOF'
用法：bash scripts/bt-deploy/04-push_to_gitea.sh [--branch <branch>]

仅推送当前已提交的分支；工作区有未提交改动时拒绝执行。该脚本不适用于服务器部署流程。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --branch)
      [[ $# -ge 2 ]] || bt_die '--branch 缺少分支名'
      SCX_BRANCH="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) bt_die "未知参数：$1" ;;
  esac
done

bt_assert_repository
bt_require_command git
[[ -z "$(git -C "${SCX_HOME}" status --porcelain)" ]] \
  || bt_die '工作区存在未提交改动；请先完成审查与提交。'
current_branch="$(git -C "${SCX_HOME}" branch --show-current)"
[[ -n "$current_branch" ]] || bt_die '当前为 detached HEAD，拒绝推送。'
[[ "$current_branch" == "$SCX_BRANCH" ]] \
  || bt_die "当前分支为 ${current_branch}，指定分支为 ${SCX_BRANCH}；请显式切换后再推送。"
git -C "${SCX_HOME}" push origin "${SCX_BRANCH}"
bt_ok "已推送 origin/${SCX_BRANCH}：$(git -C "${SCX_HOME}" rev-parse --short HEAD)"
