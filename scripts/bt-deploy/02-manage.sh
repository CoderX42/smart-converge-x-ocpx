#!/usr/bin/env bash
# SCX 宝塔运行态检查与管理入口；Java 生命周期通过宝塔项目管理能力操作。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
bt_enable_error_trap
bt_init_paths

usage() {
  cat <<'EOF'
用法：bash scripts/bt-deploy/02-manage.sh <命令> [选项]

  status        显示构建产物、当前提交和本机健康检查
  health        请求 http://127.0.0.1:9999/boot/actuator/health
  restart       通过宝塔重启 scx-boot，并等待健康检查
  boot-restart  仅通过宝塔重启 scx-boot
  nginx-test    校验 Nginx 配置
  nginx-reload  先校验再重载 Nginx（需要服务器相应权限）
  logs          输出宝塔日志目录位置，不读取或打印敏感配置

重启选项：
  --boot-project <name>   宝塔管理端项目名（默认 scx-boot）
  --timeout <seconds>     每个项目等待新 PID 和健康检查的秒数（默认 180）
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

manage_command="${1:-status}"
[[ $# -eq 0 ]] || shift
while [[ $# -gt 0 ]]; do
  case "$1" in
    --boot-project)
      [[ $# -ge 2 ]] || bt_die '--boot-project 缺少项目名'
      SCX_BT_BOOT_PROJECT="$2"; shift 2 ;;
    --timeout)
      [[ $# -ge 2 ]] || bt_die '--timeout 缺少秒数'
      SCX_RESTART_TIMEOUT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) bt_die "未知管理参数：$1" ;;
  esac
done

case "$manage_command" in
  status)
    bt_assert_repository
    bt_log "源码提交：$(git -C "${SCX_HOME}" rev-parse --short HEAD) $(git -C "${SCX_HOME}" log -1 --pretty=%s)"
    [[ -f "${SCX_BUILD_DIR}/backend/scx-boot.jar" ]] \
      && bt_ok "后端产物：${SCX_BUILD_DIR}/backend/scx-boot.jar" \
      || bt_warn '未找到后端构建产物。'
    [[ -f "${SCX_WEB_ROOT}/index.html" ]] \
      && bt_ok "前端站点：${SCX_WEB_ROOT}" \
      || bt_warn '宝塔站点根未找到 index.html。'
    if curl --silent --fail --max-time 5 http://127.0.0.1:9999/boot/actuator/health >/dev/null; then
      bt_ok '后端健康检查：UP'
    else
      bt_warn '后端健康检查未通过；请在宝塔 Java 项目管理器检查重启状态。'
    fi
    ;;
  health) health ;;
  restart) bt_restart_scx_java ;;
  boot-restart)
    bt_restart_java_project \
      '管理端 scx-boot' "$SCX_BT_BOOT_PROJECT" \
      "${SCX_BUILD_DIR}/backend/scx-boot.jar" \
      'http://127.0.0.1:9999/boot/actuator/health'
    ;;
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
  *) usage >&2; bt_die "未知管理命令：$manage_command" ;;
esac
