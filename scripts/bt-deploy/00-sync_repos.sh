#!/usr/bin/env bash
# SCX 宝塔仓库同步入口。默认安全快进同步；仅 --discard-local 会覆盖已跟踪改动。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
bt_enable_error_trap
bt_init_paths

usage() {
  cat <<'EOF'
用法：bash scripts/bt-deploy/00-sync_repos.sh [--branch <branch>] [--discard-local]

默认仅执行 fetch + fast-forward；检测到未提交改动、分支不一致或无法快进时立即停止。
--discard-local 仅在已核对服务器本地改动可丢弃时使用，会执行 git reset --hard origin/<branch>。
EOF
}

discard_local=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --branch) [[ $# -ge 2 ]] || bt_die '--branch 缺少分支名'; SCX_BRANCH="$2"; shift 2 ;;
    --discard-local) discard_local=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) bt_die "未知参数：$1" ;;
  esac
done

bt_require_command git
if [[ ! -d "${SCX_HOME}/.git" ]]; then
  [[ ! -e "${SCX_HOME}" || -z "$(find "${SCX_HOME}" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]] \
    || bt_die "${SCX_HOME} 非空且不是 Git 仓库，拒绝覆盖。"
  SCX_GIT_URL="${SCX_GIT_URL:-ssh://git@gitlab.smartcute.cn:2232/karl419/scx.git}"
  mkdir -p "$(dirname "${SCX_HOME}")"
  bt_log "克隆 ${SCX_GIT_URL} (${SCX_BRANCH}) -> ${SCX_HOME}"
  git clone --branch "${SCX_BRANCH}" --single-branch "${SCX_GIT_URL}" "${SCX_HOME}"
  bt_ok "仓库克隆完成：$(git -C "${SCX_HOME}" rev-parse --short HEAD)"
  exit 0
fi

bt_log "同步仓库：${SCX_HOME}，目标分支：${SCX_BRANCH}"
git -C "${SCX_HOME}" fetch origin "${SCX_BRANCH}" --prune
git -C "${SCX_HOME}" show-ref --verify --quiet "refs/remotes/origin/${SCX_BRANCH}" \
  || bt_die "远程不存在分支 origin/${SCX_BRANCH}"
current_branch="$(git -C "${SCX_HOME}" branch --show-current)"
[[ "$current_branch" == "$SCX_BRANCH" ]] \
  || bt_die "当前分支为 ${current_branch:-detached}，目标为 ${SCX_BRANCH}；请人工切换并确认。"
if [[ "$discard_local" == true ]]; then
  bt_warn "按显式请求覆盖已跟踪本地改动：git reset --hard origin/${SCX_BRANCH}"
  git -C "${SCX_HOME}" reset --hard "origin/${SCX_BRANCH}"
else
  [[ -z "$(git -C "${SCX_HOME}" status --porcelain)" ]] \
    || bt_die "服务器工作区存在未提交改动；已拒绝覆盖。核对后可显式添加 --discard-local。"
  git -C "${SCX_HOME}" merge --ff-only "origin/${SCX_BRANCH}" \
    || bt_die "无法 fast-forward 到 origin/${SCX_BRANCH}；请人工审查分叉提交。"
fi
bt_ok "已同步至 $(git -C "${SCX_HOME}" rev-parse --short HEAD)：$(git -C "${SCX_HOME}" log -1 --pretty=%s)"
