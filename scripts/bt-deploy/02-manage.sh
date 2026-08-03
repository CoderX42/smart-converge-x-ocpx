#!/usr/bin/env bash
# SCX 宝塔运行态检查与 Nginx 管理入口；Java 进程始终由宝塔 Java 项目管理器管理。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
bt_enable_error_trap
bt_init_paths

usage() {
  cat <<'EOF'
用法：bash scripts/bt-deploy/02-manage.sh <status|health|rtb-health|nginx-test|nginx-reload|logs>

  status        显示构建产物、当前提交和本机健康检查
  health        请求 http://127.0.0.1:9999/boot/actuator/health
  rtb-health    请求 http://127.0.0.1:10000/rtb/actuator/health
  nginx-test    校验 Nginx 配置
  nginx-reload  先校验再重载 Nginx（需要服务器相应权限）
  logs          输出宝塔日志目录位置，不读取或打印敏感配置
EOF
}

health() {
  bt_require_command curl
  local response
  if response="$(curl --fail --silent --show-error --max-time 10 http://127.0.0.1:9999/boot/actuator/health)"; then
    bt_ok "后端健康检查通过：${response}"
  else
    bt_die '后端健康检查失败；请在宝塔 Java 项目管理器确认项目状态和日志。'
  fi
}

rtb_health() {
  bt_require_command curl
  local response
  if response="$(curl --fail --silent --show-error --max-time 10 http://127.0.0.1:10000/rtb/actuator/health)"; then
    bt_ok "OCPX 数据面健康检查通过：${response}"
  else
    bt_die 'OCPX 数据面健康检查失败；请在宝塔 Java 项目管理器确认 scx-rtb 状态和日志。'
  fi
}

nginx_binary() {
  if command -v nginx >/dev/null 2>&1; then
    command -v nginx
  elif [[ -x /www/server/nginx/sbin/nginx ]]; then
    printf '%s\n' /www/server/nginx/sbin/nginx
  else
    return 1
  fi
}

nginx_test() {
  local binary
  binary="$(nginx_binary)" || bt_die '未找到 Nginx 可执行文件。'
  "$binary" -t
  bt_ok 'Nginx 配置校验通过。'
}

case "${1:-status}" in
  status)
    bt_assert_repository
    bt_log "源码提交：$(git -C "${SCX_HOME}" rev-parse --short HEAD) $(git -C "${SCX_HOME}" log -1 --pretty=%s)"
    [[ -f "${SCX_BUILD_DIR}/backend/scx-boot.jar" ]] \
      && bt_ok "后端产物：${SCX_BUILD_DIR}/backend/scx-boot.jar" \
      || bt_warn '未找到后端构建产物。'
    [[ -f "${SCX_BUILD_DIR}/backend/scx-rtb.jar" ]] \
      && bt_ok "数据面产物：${SCX_BUILD_DIR}/backend/scx-rtb.jar" \
      || bt_warn '未找到 scx-rtb 数据面构建产物。'
    [[ -f "${SCX_WEB_ROOT}/index.html" ]] \
      && bt_ok "前端站点：${SCX_WEB_ROOT}" \
      || bt_warn '宝塔站点根未找到 index.html。'
    if curl --silent --fail --max-time 5 http://127.0.0.1:9999/boot/actuator/health >/dev/null; then
      bt_ok '后端健康检查：UP'
    else
      bt_warn '后端健康检查未通过；请在宝塔 Java 项目管理器检查重启状态。'
    fi
    if curl --silent --fail --max-time 5 http://127.0.0.1:10000/rtb/actuator/health >/dev/null; then
      bt_ok 'OCPX 数据面健康检查：UP'
    else
      bt_warn 'OCPX 数据面健康检查未通过；未部署或未启动时属预期。'
    fi
    ;;
  health) health ;;
  rtb-health) rtb_health ;;
  nginx-test) nginx_test ;;
  nginx-reload)
    nginx_test
    if command -v systemctl >/dev/null 2>&1; then
      systemctl reload nginx
    elif command -v service >/dev/null 2>&1; then
      service nginx reload
    else
      bt_die '未找到 systemctl/service，无法重载 Nginx。'
    fi
    bt_ok 'Nginx 已重载。'
    ;;
  logs)
    bt_log "宝塔 Java 项目日志请在面板查看；推荐日志目录：${SCX_LOG_DIR}"
    bt_log "后端运行态检查：bash ${SCX_HOME}/scripts/bt-deploy/02-manage.sh health"
    ;;
  -h|--help) usage ;;
  *) usage >&2; bt_die "未知管理命令：$1" ;;
esac
