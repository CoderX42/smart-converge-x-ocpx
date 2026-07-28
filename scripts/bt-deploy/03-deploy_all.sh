#!/usr/bin/env bash
# =============================================================================
#  SCX 一键部署（宝塔日常最常用，对标 haorong 03-deploy_all.sh）
#
#  流程:
#    [1] 同步代码   00-sync_repos.sh
#    [2] 打包构建   01-deploy.sh --no-pull  （后端 jar + 前端 dist + 发布到站点根）
#    [3] 不自动启停服务（由宝塔 Java 项目管理器 / 面板重启）
#
#  使用:
#    bash 03-deploy_all.sh
#    bash 03-deploy_all.sh --skip-frontend  # 仅后端（含 xxl-job-admin）
#    bash 03-deploy_all.sh --skip-backend   # 仅前端
#    bash 03-deploy_all.sh --skip-xxl       # 全量时跳过 xxl-job 打包
#    SCX_HOME=/opt/smart-converge-x SCX_WEB_ROOT=/www/wwwroot/scx bash 03-deploy_all.sh
#
#  日志: /www/wwwlogs/scx/deploy-*.log （无 /www/wwwlogs 则用 /var/log/scx/）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_REPO_GUESS="$(cd "${SCRIPT_DIR}/../.." && pwd)"
if [[ -d "${_REPO_GUESS}/scx-backend" ]]; then
  DEFAULT_HOME="${_REPO_GUESS}"
else
  DEFAULT_HOME="/opt/smart-converge-x"
fi

SCX_HOME="${SCX_HOME:-$DEFAULT_HOME}"
SCX_WEB_ROOT="${SCX_WEB_ROOT:-${SCX_BAOTA_SITE_ROOT:-/www/wwwroot/scx}}"
LOG_DIR="${LOG_DIR:-}"
if [[ -z "$LOG_DIR" ]]; then
  if [[ -d /www/wwwlogs ]]; then
    LOG_DIR="/www/wwwlogs/scx"
  else
    LOG_DIR="/var/log/scx"
  fi
fi
mkdir -p "$LOG_DIR"
LOG_FILE="${LOG_DIR}/deploy-$(date +%Y%m%d_%H%M%S).log"

TARGET="all"
SKIP_XXL=0

for arg in "$@"; do
  case "$arg" in
    --skip-frontend) TARGET="backend" ;;
    --skip-backend) TARGET="frontend" ;;
    --skip-xxl) SKIP_XXL=1 ;;
    -h|--help)
      sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "未知参数: $arg"; exit 1
      ;;
  esac
done

DEPLOY_ARGS=(--no-pull)
if [[ "$TARGET" != "all" ]]; then
  DEPLOY_ARGS+=("$TARGET")
fi
if [[ $SKIP_XXL -eq 1 ]]; then
  DEPLOY_ARGS+=(--skip-xxl)
fi

exec > >(tee -a "$LOG_FILE") 2>&1

echo "============================================================"
echo " SCX 一键部署（宝塔模式）    $(date '+%F %T')"
echo " SCX_HOME : ${SCX_HOME}"
echo " WEB_ROOT : ${SCX_WEB_ROOT}"
echo " 日志     : ${LOG_FILE}"
echo " 构建范围 : ${TARGET}"
echo "============================================================"

export SCX_HOME SCX_WEB_ROOT

echo ""
echo "[1/2] 同步代码"
bash "${SCRIPT_DIR}/00-sync_repos.sh"
# 同步后优先使用仓库内最新脚本
if [[ -d "${SCX_HOME}/scripts/bt-deploy" ]]; then
  SCRIPT_DIR="${SCX_HOME}/scripts/bt-deploy"
fi

echo ""
echo "[2/2] 打包构建并发布前端"
bash "${SCRIPT_DIR}/01-deploy.sh" "${DEPLOY_ARGS[@]}"

echo ""
echo "============================================================"
echo " 部署完成 $(date '+%F %T')"
echo " scx-boot JAR : ${SCX_HOME}/build/backend/scx-boot.jar"
echo " xxl-job JAR  : ${SCX_HOME}/build/backend/xxl-job-admin.jar"
echo " 前端站点根   : ${SCX_WEB_ROOT}"
echo ""
echo " 下一步（服务由宝塔管理，脚本不自动重启）："
echo "  → 宝塔 → Java 项目管理器 → 重启 scx-boot"
echo "  → 宝塔 → Java 项目管理器 → 重启 xxl-job-admin"
echo "  → 前端站点根已同步，必要时在宝塔重载 Nginx"
echo "  → 健康: curl -s http://127.0.0.1:9999/boot/actuator/health"
echo " 完整日志: ${LOG_FILE}"
echo "============================================================"
