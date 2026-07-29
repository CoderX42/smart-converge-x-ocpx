#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
profiles=(
  "${repo_root}/scx-backend/scx-boot/src/main/resources/application-dev.yml"
  "${repo_root}/scx-backend/scx-boot/src/main/resources/application-prod.yml"
)
deploy_scripts=(
  "${repo_root}"/scripts/deploy/*.sh
)
nacos_profile="${repo_root}/scx-backend/db/nacos/application.properties"
nginx_panel="${repo_root}/scripts/deploy/scx-nginx-panel.py"

dev_profile="${profiles[0]}"
prod_profile="${profiles[1]}"

# dev / prod 均允许 yml 直写配置，不再强制 ${SCX_*} 环境占位
# 仅校验关键配置键存在，避免误删字段
direct_keys=(
  'url: jdbc:mysql'
  'name-server:'
  'adminAddresses:'
  'accessToken:'
  'channel-master-key:'
  'device-hmac-key:'
)
for profile in "$dev_profile" "$prod_profile"; do
  for key in "${direct_keys[@]}"; do
    if ! grep -Fq "$key" "$profile"; then
      echo "Secret injection check failed: $(basename "$profile") missing direct config key fragment '$key'." >&2
      exit 1
    fi
  done
  if ! grep -Fq 'fail-on-unresolved-placeholders: false' "$profile"; then
    echo "Secret injection check failed: $(basename "$profile") should set fail-on-unresolved-placeholders: false for direct yml config." >&2
    exit 1
  fi
done

for script in "${deploy_scripts[@]}"; do
  if grep -Eq '^(MYSQL_HOST|MYSQL_USERNAME|MYSQL_PASSWORD|XXL_JOB_ACCESS_TOKEN)=.*\$\{[^}]+:-' "$script"; then
    echo "Secret injection check failed: deployment connection or credential variable has an inline default in $(basename "$script")." >&2
    exit 1
  fi
  if grep -Eq '^(MYSQL_HOST|MYSQL_USERNAME|MYSQL_PASSWORD|XXL_JOB_ACCESS_TOKEN)="[^$]' "$script"; then
    echo "Secret injection check failed: deployment connection or credential value is assigned inline in $(basename "$script")." >&2
    exit 1
  fi
  if grep -Eq 'log .*[$](\{)?(MYSQL_PASSWORD|XXL_JOB_ACCESS_TOKEN)' "$script"; then
    echo "Secret injection check failed: deployment script may log a sensitive value in $(basename "$script")." >&2
    exit 1
  fi
done

for placeholder in \
  'db.url.0=${NACOS_DB_URL}' \
  'db.user=${NACOS_DB_USERNAME}' \
  'db.password=${NACOS_DB_PASSWORD}' \
  'nacos.core.auth.plugin.nacos.token.secret.key=${NACOS_AUTH_TOKEN_SECRET}'; do
  if ! grep -Fq "$placeholder" "$nacos_profile"; then
    echo "Secret injection check failed: Nacos runtime placeholder is missing." >&2
    exit 1
  fi
done

if grep -Fq '?token=' "$nginx_panel"; then
  echo "Secret injection check failed: Nginx panel token must not be transported in a query string." >&2
  exit 1
fi

echo "Secret injection check passed: dev/prod application-*.yml use direct values (no required env placeholders)."

# === BASE-03-FIX-MERGE: 增补 Nacos identity / xxl-job-admin / install.sh / xxl-job sample ===

xxl_job_admin_props="${repo_root}/xxl-job/xxl-job-admin/src/main/resources/application.properties"
xxl_job_samples=(
  "${repo_root}/xxl-job/xxl-job-executor-samples/xxl-job-executor-sample-springboot/src/main/resources/application.properties"
  "${repo_root}/xxl-job/xxl-job-executor-samples/xxl-job-executor-sample-springboot-ai/src/main/resources/application.properties"
  "${repo_root}/xxl-job/xxl-job-executor-samples/xxl-job-executor-sample-frameless/src/main/resources/xxl-job-executor.properties"
)
install_script="${repo_root}/scripts/deploy/install.sh"

# 1. Nacos server.identity 必须用 ${NACOS_AUTH_SERVER_IDENTITY_KEY/VALUE} 占位，不能内联
if grep -Eq '^nacos\.core\.auth\.server\.identity\.(key|value)=[^$\{[:space:]]+' "$nacos_profile"; then
  echo "Secret injection check failed: Nacos server.identity has inline value in $nacos_profile." >&2
  exit 1
fi
if ! grep -Fq 'nacos.core.auth.server.identity.key=${NACOS_AUTH_SERVER_IDENTITY_KEY}' "$nacos_profile"; then
  echo "Secret injection check failed: Nacos server.identity.key must use \${NACOS_AUTH_SERVER_IDENTITY_KEY} placeholder." >&2
  exit 1
fi
if ! grep -Fq 'nacos.core.auth.server.identity.value=${NACOS_AUTH_SERVER_IDENTITY_VALUE}' "$nacos_profile"; then
  echo "Secret injection check failed: Nacos server.identity.value must use \${NACOS_AUTH_SERVER_IDENTITY_VALUE} placeholder." >&2
  exit 1
fi

# 2. xxl-job-admin：yml/properties 直写配置（不再强制环境变量占位）
xxl_job_direct_keys=(
  'spring.datasource.url=jdbc:mysql'
  'spring.datasource.username='
  'spring.datasource.password='
  'xxl.job.accessToken='
  'xxl-sso.token.key='
)
for key in "${xxl_job_direct_keys[@]}"; do
  if ! grep -Fq "$key" "$xxl_job_admin_props"; then
    echo "Secret injection check failed: xxl-job-admin missing direct config key fragment '$key'." >&2
    exit 1
  fi
done
if grep -E '\$\{XXL_JOB_|\$\{XXL_SSO_' "$xxl_job_admin_props" >/dev/null; then
  echo "Secret injection check failed: xxl-job-admin still contains XXL_* environment placeholders." >&2
  exit 1
fi

# 3. install.sh 禁止再内联 6 字符 MySQL 弱默认（如 123456/root:123456/六位数字）
if grep -Eq 'MYSQL_PASSWORD=.{1,8}$' "$install_script"; then
  echo "Secret injection check failed: scripts/deploy/install.sh still has a weak inline MYSQL_PASSWORD (length<=8)." >&2
  exit 1
fi
if grep -Eq '^[A-Z_]+_PASSWORD=[A-Za-z0-9]{1,8}$' "$install_script"; then
  echo "Secret injection check failed: scripts/deploy/install.sh has a weak inline password default (length<=8)." >&2
  exit 1
fi

# 4. xxl-job sample：accessToken 直写即可；禁止仍使用 REPLACE_WITH_* 环境占位残留
for sample in "${xxl_job_samples[@]}"; do
  if [[ -f "$sample" ]]; then
    if grep -Eq '\$\{XXL_JOB_ACCESS_TOKEN|REPLACE_WITH_PROD_TOKEN' "$sample"; then
      echo "Secret injection check failed: $sample still uses env/placeholder accessToken form." >&2
      exit 1
    fi
    if ! grep -Eq 'xxl\.job\.admin\.accessToken=' "$sample"; then
      echo "Secret injection check failed: $sample missing xxl.job.admin.accessToken." >&2
      exit 1
    fi
  fi
done

echo "BASE-03-FIX-MERGE checks passed: Nacos uses env injection; xxl-job uses direct properties config."
