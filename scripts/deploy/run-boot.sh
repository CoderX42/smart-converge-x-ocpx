#!/usr/bin/env bash
# systemd ExecStart 入口：正确展开 JAVA_OPTS
set -euo pipefail

ENV_FILE="${SCX_ENV_FILE:-/etc/scx/scx.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

# XXL_JOB_PORT belongs to the standalone admin process. Spring Boot's relaxed
# environment binding would otherwise map it to xxl.job.port and override the
# executor-specific SCX_XXL_JOB_EXECUTOR_PORT value.
unset XXL_JOB_PORT

SCX_HOME="${SCX_HOME:-/opt/smart-converge-x}"
SCX_PROFILE="${SCX_PROFILE:-prod}"
SCX_JAVA_OPTS="${SCX_JAVA_OPTS:--Xms512m -Xmx1024m -Dfile.encoding=UTF-8}"
SCX_JAR="${SCX_JAR:-${SCX_HOME}/scx-backend/scx-boot/target/scx-boot.jar}"

[[ -f "$SCX_JAR" ]] || { echo "JAR not found: $SCX_JAR" >&2; exit 1; }

# shellcheck disable=SC2086
exec java $SCX_JAVA_OPTS -jar "$SCX_JAR" --spring.profiles.active="$SCX_PROFILE"
