#!/usr/bin/env bash
# =============================================================================
#  SCX 宝塔机：一键同步 Gitea 代码（单仓 monorepo）
#  - 默认代码目录: /opt/smart-converge-x
#  - 目录不存在: git clone
#  - 目录存在: fetch + reset --hard origin/<branch>（丢弃服务器本地改动）
#
#  使用:
#    bash 00-sync_repos.sh
#    BRANCH=main bash 00-sync_repos.sh
#    SCX_HOME=/www/wwwroot/smart-converge-x bash 00-sync_repos.sh
#
#  环境变量:
#    SCX_HOME / SYNC_DIR   代码根目录
#    BRANCH                默认 main
#    SCX_GIT_URL           默认见下方
#    GITEA_HOST / GITEA_PORT  SSH 探测用（可选）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 若脚本已在仓库内，优先以仓库根为默认 SCX_HOME
_REPO_GUESS="$(cd "${SCRIPT_DIR}/../.." && pwd)"
if [[ -d "${_REPO_GUESS}/scx-backend" ]]; then
  DEFAULT_HOME="${_REPO_GUESS}"
else
  DEFAULT_HOME="/opt/smart-converge-x"
fi

SCX_HOME="${SCX_HOME:-${SYNC_DIR:-$DEFAULT_HOME}}"
BRANCH="${BRANCH:-main}"
SCX_GIT_URL="${SCX_GIT_URL:-git@gitea.smartcute.cn:karl419/smart-converge-x.git}"

# SSH 探测（兼容 gitea.smartcute.cn 标准 22，或内网 IP:2222）
GITEA_HOST="${GITEA_HOST:-gitea.smartcute.cn}"
GITEA_PORT="${GITEA_PORT:-22}"
GITEA_USER="${GITEA_USER:-git}"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}[✔]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
fail() { echo -e "${RED}[✘]${NC} $1"; }

ssh_probe() {
  local out
  if [[ "$GITEA_PORT" == "22" ]]; then
    out="$(ssh -T -o ConnectTimeout=10 -o BatchMode=yes -o StrictHostKeyChecking=accept-new \
      "${GITEA_USER}@${GITEA_HOST}" </dev/null 2>&1 || true)"
  else
    out="$(ssh -T -p "${GITEA_PORT}" -o ConnectTimeout=10 -o BatchMode=yes -o StrictHostKeyChecking=accept-new \
      "${GITEA_USER}@${GITEA_HOST}" </dev/null 2>&1 || true)"
  fi
  if echo "$out" | grep -qiE 'successfully authenticated|Hi |authenticated'; then
    ok "SSH 鉴权正常 (${GITEA_HOST}:${GITEA_PORT})"
    return 0
  fi
  warn "SSH 鉴权提示未通过，请确认服务器已配置 Gitea 公钥"
  warn "探测输出: $(echo "$out" | head -2 | tr '\n' ' ')"
  return 0
}

sync_repo() {
  echo "------------------------------------------------------------"
  echo ">>> smart-converge-x  branch=${BRANCH}"
  echo ">>> url=${SCX_GIT_URL}"
  echo ">>> dir=${SCX_HOME}"
  echo "------------------------------------------------------------"

  mkdir -p "$(dirname "$SCX_HOME")"

  if [[ -d "${SCX_HOME}/.git" ]]; then
    cd "$SCX_HOME"
    local current
    current="$(git symbolic-ref --short HEAD 2>/dev/null || echo "")"
    if [[ "$current" != "$BRANCH" ]]; then
      warn "当前分支 ${current:-detached} ≠ 目标 ${BRANCH}，切换中..."
      git fetch origin "$BRANCH" --prune || true
      git checkout "$BRANCH" 2>/dev/null || git checkout -b "$BRANCH" "origin/${BRANCH}"
    fi

    git remote set-url origin "$SCX_GIT_URL"
    if git fetch origin "$BRANCH" --prune; then
      local remote_sha
      remote_sha="$(git rev-parse "origin/${BRANCH}")"
      # 清理可能被宝塔 chattr 的文件，避免 reset 失败
      find "$SCX_HOME" -name '.user.ini' -exec chattr -i {} \; 2>/dev/null || true
      git reset --hard "origin/${BRANCH}"
      ok "已同步至 origin/${BRANCH} (${remote_sha})"
      git log -1 --oneline
    else
      fail "fetch 失败"
      return 1
    fi
  else
    if [[ -n "$(ls -A "$SCX_HOME" 2>/dev/null || true)" ]]; then
      fail "${SCX_HOME} 非空且不是 git 仓库，请清空或改 SCX_HOME"
      return 1
    fi
    if git clone -b "$BRANCH" "$SCX_GIT_URL" "$SCX_HOME"; then
      ok "克隆完成 -> ${SCX_HOME}"
      git -C "$SCX_HOME" log -1 --oneline
    else
      fail "克隆失败（检查公钥 / 仓库地址 / 端口）"
      return 1
    fi
  fi
  echo ""
}

main() {
  echo "============================================================"
  echo " SCX Gitea 仓库同步"
  echo " Host : ${GITEA_HOST}:${GITEA_PORT}"
  echo " Dir  : ${SCX_HOME}"
  echo " URL  : ${SCX_GIT_URL}"
  echo " Branch: ${BRANCH}"
  echo " Time : $(date '+%F %T')"
  echo "============================================================"
  echo ""

  command -v git >/dev/null 2>&1 || { fail "缺少 git"; exit 1; }
  ssh_probe
  sync_repo

  echo "============================================================"
  ok "同步完成"
  echo "  打包: bash ${SCX_HOME}/scripts/bt-deploy/01-deploy.sh --no-pull"
  echo "  一键: bash ${SCX_HOME}/scripts/bt-deploy/03-deploy_all.sh"
  echo "  重启: 请在宝塔 Java 项目管理器中操作"
  echo "============================================================"
}

main "$@"
