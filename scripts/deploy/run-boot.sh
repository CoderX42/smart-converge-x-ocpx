#!/usr/bin/env bash
# scx-boot 启动入口（宝塔 Java 项目 / systemd 均可）
#
# 生产配置写在 jar 内 application-prod.yml（源文件：
#   scx-backend/scx-boot/src/main/resources/application-prod.yml）
# 不再依赖 /etc/scx/scx.env。
#
# 宝塔启动命令示例:
#   /bin/bash /opt/smart-converge-x/scripts/deploy/run-boot.sh
# 或面板直接:
#   java -Xms512m -Xmx1024m -jar /opt/smart-converge-x/build/backend/scx-boot.jar --spring.profiles.active=prod
#
set -euo pipefail

SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
SCX_PROFILE="${SCX_PROFILE:-prod}"
SCX_JAVA_OPTS="${SCX_JAVA_OPTS:--Xms512m -Xmx1024m -Dfile.encoding=UTF-8}"

# 可选：若仍存在 env 文件则加载（兼容旧部署，非必须）
ENV_FILE="${SCX_ENV_FILE:-/etc/scx/scx.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  unset XXL_JOB_PORT
fi

if [[ -z "${SCX_JAR:-}" ]]; then
  if [[ -f "${SCX_HOME}/build/backend/scx-boot.jar" ]]; then
    SCX_JAR="${SCX_HOME}/build/backend/scx-boot.jar"
  else
    SCX_JAR="${SCX_HOME}/scx-backend/scx-boot/target/scx-boot.jar"
  fi
fi

[[ -f "$SCX_JAR" ]] || { echo "[run-boot] JAR not found: $SCX_JAR" >&2; exit 1; }

echo "[run-boot] profile=${SCX_PROFILE} jar=${SCX_JAR}"
# shellcheck disable=SC2086
exec java $SCX_JAVA_OPTS -jar "$SCX_JAR" --spring.profiles.active="$SCX_PROFILE"
