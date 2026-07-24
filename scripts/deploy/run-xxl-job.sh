#!/usr/bin/env bash
# systemd ExecStart：启动 xxl-job-admin
set -euo pipefail

ENV_FILE="${SCX_ENV_FILE:-/etc/scx/scx.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

XXL_HOME="${XXL_JOB_HOME:-/opt/xxl-job-admin}"
XXL_JAR="${XXL_JOB_JAR:-${XXL_HOME}/xxl-job-admin.jar}"
XXL_JAVA_OPTS="${XXL_JOB_JAVA_OPTS:--Xms256m -Xmx512m -Dfile.encoding=UTF-8}"
XXL_CONFIG="${XXL_HOME}/application.properties"

[[ -f "$XXL_JAR" ]] || { echo "xxl-job-admin jar not found: $XXL_JAR" >&2; exit 1; }
[[ -f "$XXL_CONFIG" ]] || { echo "config not found: $XXL_CONFIG (run install-xxl-job.sh first)" >&2; exit 1; }

# shellcheck disable=SC2086
exec java $XXL_JAVA_OPTS \
  -Dspring.config.location="file:${XXL_CONFIG}" \
  -jar "$XXL_JAR"
