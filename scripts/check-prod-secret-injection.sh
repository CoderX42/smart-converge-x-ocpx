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

required_placeholders=(
  'host: ${SCX_REDIS_HOST}'
  'password: ${SCX_REDIS_PASSWORD}'
  'url: ${SCX_DATASOURCE_URL}'
  'username: ${SCX_DATASOURCE_USERNAME}'
  'password: ${SCX_DATASOURCE_PASSWORD}'
  'name-server: ${ROCKETMQ_NAME_SERVER}'
  'adminAddresses: ${SCX_XXL_JOB_ADMIN_ADDRESSES}'
  'accessToken: ${SCX_XXL_JOB_ACCESS_TOKEN}'
  'key: ${SCX_ENCODE_KEY}'
  'channel-master-key: ${SCX_TRACKING_CHANNEL_MASTER_KEY'
  'device-hmac-key: ${SCX_TRACKING_DEVICE_HMAC_KEY'
)

# === FIX-SECRET-CHECK-DEV: 区分 dev 与 prod 占位符匹配 ===
# dev profile 允许 ${VAR:default} 形式（sandbox 模式可空默认）
# dev_required 模式以 "${VAR" 开头（不强制闭合 }），允许 dev 写法
#   例如：dev yml 中 `name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}` 仍能匹配 `${ROCKETMQ_NAME_SERVER`
dev_required=(
  'host: ${SCX_REDIS_HOST'
  'password: ${SCX_REDIS_PASSWORD'
  'url: ${SCX_DATASOURCE_URL'
  'username: ${SCX_DATASOURCE_USERNAME'
  'password: ${SCX_DATASOURCE_PASSWORD'
  'name-server: ${ROCKETMQ_NAME_SERVER'
  'key: ${SCX_ENCODE_KEY'
  'channel-master-key: ${SCX_TRACKING_CHANNEL_MASTER_KEY'
  'device-hmac-key: ${SCX_TRACKING_DEVICE_HMAC_KEY'
)

# prod profile 必须 ${VAR} 形式（无 default），任何冒号后默认必须失败
#   prod_required 模式以 "${VAR}" 闭合（无冒号），确保生产配置 fail-closed
prod_required=(
  'host: ${SCX_REDIS_HOST}'
  'password: ${SCX_REDIS_PASSWORD}'
  'url: ${SCX_DATASOURCE_URL}'
  'username: ${SCX_DATASOURCE_USERNAME}'
  'password: ${SCX_DATASOURCE_PASSWORD}'
  'name-server: ${ROCKETMQ_NAME_SERVER}'
  'key: ${SCX_ENCODE_KEY}'
  'channel-master-key: ${SCX_TRACKING_CHANNEL_MASTER_KEY}'
  'device-hmac-key: ${SCX_TRACKING_DEVICE_HMAC_KEY}'
)

dev_profile="${profiles[0]}"
prod_profile="${profiles[1]}"

# 1. dev profile 检查（接受 ${VAR:default} 形式）
for placeholder in "${dev_required[@]}"; do
  if ! grep -Fq "$placeholder" "$dev_profile"; then
    echo "Secret injection check failed: dev profile missing required environment placeholder '$placeholder'." >&2
    exit 1
  fi
done

# 2. prod profile 严格检查（要求 ${VAR} 无 default）
for placeholder in "${prod_required[@]}"; do
  if ! grep -Fq "$placeholder" "$prod_profile"; then
    echo "Secret injection check failed: prod profile missing required environment placeholder '$placeholder'." >&2
    exit 1
  fi
done

# 3. prod profile 禁止 ${VAR:default} 形式（防止用空默认绕过）
#    对每个 prod 必填占位符，确保文件中是 ${VAR} 而非 ${VAR:default}
for placeholder_pattern in \
  'password: ${SCX_REDIS_PASSWORD}' \
  'password: ${SCX_DATASOURCE_PASSWORD}' \
  'name-server: ${ROCKETMQ_NAME_SERVER}' \
  'adminAddresses: ${SCX_XXL_JOB_ADMIN_ADDRESSES}' \
  'accessToken: ${SCX_XXL_JOB_ACCESS_TOKEN}' \
  'key: ${SCX_ENCODE_KEY}' \
  'channel-master-key: ${SCX_TRACKING_CHANNEL_MASTER_KEY}' \
  'device-hmac-key: ${SCX_TRACKING_DEVICE_HMAC_KEY}'; do
  placeholder_var="$(echo "$placeholder_pattern" | grep -oE '\$\{[A-Z0-9_]+\}')"
  if grep -F "$placeholder_var:" "$prod_profile" >/dev/null 2>&1; then
    echo "Secret injection check failed: prod profile uses default value for $placeholder_var (must be required without default)." >&2
    exit 1
  fi
done

# 4. dev profile 禁止明文敏感值（保留原有 inline 检查）
#    dev 可有 ${VAR:default} 占位，但禁止 password=plain-text
if grep -E '^[[:space:]]*(password|accessToken|key|sandbox-signing-key|channel-master-key|device-hmac-key):' "$dev_profile" \
    | grep -Ev '\$\{[A-Z0-9_]+' >/dev/null; then
  echo "Secret injection check failed: inline sensitive configuration exists in $(basename "$dev_profile")." >&2
  exit 1
fi

# 5. prod profile 同样禁止明文
if grep -E '^[[:space:]]*(password|accessToken|key|sandbox-signing-key|channel-master-key|device-hmac-key):' "$prod_profile" \
    | grep -Ev '\$\{[A-Z0-9_]+' >/dev/null; then
  echo "Secret injection check failed: inline sensitive configuration exists in $(basename "$prod_profile")." >&2
  exit 1
fi

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

prod_profile="${profiles[1]}"
prod_required=(
  'adminAddresses: ${SCX_XXL_JOB_ADMIN_ADDRESSES}'
  'accessToken: ${SCX_XXL_JOB_ACCESS_TOKEN}'
  'key: ${SCX_ENCODE_KEY}'
  'channel-master-key: ${SCX_TRACKING_CHANNEL_MASTER_KEY}'
  'device-hmac-key: ${SCX_TRACKING_DEVICE_HMAC_KEY}'
)
for placeholder in "${prod_required[@]}"; do
  if ! grep -Fq "$placeholder" "$prod_profile"; then
    echo "Secret injection check failed: production placeholder must be required and have no default." >&2
    exit 1
  fi
done

echo "Production secret injection check passed: application profiles and deployment scripts use environment injection."

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

# 2. xxl-job-admin 必须用 ${XXL_JOB_DB_URL/USERNAME/PASSWORD} 与 ${XXL_JOB_MAIL_PASSWORD} 等占位
xxl_job_required_placeholders=(
  'spring.datasource.url=${XXL_JOB_DB_URL}'
  'spring.datasource.username=${XXL_JOB_DB_USERNAME}'
  'spring.datasource.password=${XXL_JOB_DB_PASSWORD}'
  'spring.mail.username=${XXL_JOB_MAIL_USERNAME}'
  'spring.mail.password=${XXL_JOB_MAIL_PASSWORD}'
  'xxl.job.accessToken=${XXL_JOB_ACCESS_TOKEN}'
  'xxl-sso.token.key=${XXL_SSO_TOKEN_KEY}'
)
for placeholder in "${xxl_job_required_placeholders[@]}"; do
  if ! grep -Fq "$placeholder" "$xxl_job_admin_props"; then
    echo "Secret injection check failed: xxl-job-admin missing placeholder '$placeholder'." >&2
    exit 1
  fi
done
# xxl-job-admin 禁止再内联 spring.datasource.password / spring.mail.password / xxl.job.accessToken / xxl-sso.token.key
if grep -E '^(spring\.datasource\.password|spring\.mail\.password|xxl\.job\.accessToken|xxl-sso\.token\.key)=[^$\{[:space:]]+' "$xxl_job_admin_props"; then
  echo "Secret injection check failed: xxl-job-admin still has inline sensitive value (datasource password / mail password / accessToken / sso token)." >&2
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

# 4. xxl-job sample 禁止 default_token / xxxxxx 等可被误用的占位
for sample in "${xxl_job_samples[@]}"; do
  if [[ -f "$sample" ]]; then
    if grep -Eq 'xxl\.job\.admin\.accessToken=default_token(\s|$)' "$sample"; then
      echo "Secret injection check failed: $sample still has xxl.job.admin.accessToken=default_token." >&2
      exit 1
    fi
    if grep -Eq 'spring\.ai\.openai\.api-key=xxxxxx(\s|$)' "$sample"; then
      echo "Secret injection check failed: $sample still has spring.ai.openai.api-key=xxxxxx." >&2
      exit 1
    fi
    if ! grep -Fq 'REPLACE_WITH_PROD_TOKEN' "$sample"; then
      echo "Secret injection check failed: $sample missing non-production placeholder REPLACE_WITH_PROD_TOKEN." >&2
      exit 1
    fi
  fi
done

echo "BASE-03-FIX-MERGE checks passed: Nacos identity, xxl-job-admin, install.sh and xxl-job samples use environment injection."
