#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FRONTEND_DIR="${ROOT_DIR}/scx-fronted"
API_URL="${L0_API_URL:-http://127.0.0.1:19999}"
FRONTEND_URL="${L0_FRONTEND_URL:-http://127.0.0.1:18080}"
RUN_ID="${L0_RUN_ID:?L0_RUN_ID is required}"
TARGET_HOST="${L0_TARGET_HOST:-}"
EXPECTED_SSH_FINGERPRINT="${L0_SSH_FINGERPRINT:-SHA256:rrH8nVzaA40JGueBrcH8Jq2tad//8FL0WbUEuHSOXZo}"
API_HOST="${API_URL#*://}"
API_HOST="${API_HOST%%/*}"
API_HOST="${API_HOST%%:*}"
ADMIN_USERNAME="${L0_ADMIN_USERNAME:?L0_ADMIN_USERNAME is required}"
ADMIN_PASSWORD="${L0_ADMIN_PASSWORD:?L0_ADMIN_PASSWORD is required}"
OPERATOR_PASSWORD="${L0_OPERATOR_PASSWORD:?L0_OPERATOR_PASSWORD is required}"
ANALYST_PASSWORD="${L0_ANALYST_PASSWORD:?L0_ANALYST_PASSWORD is required}"
NOSCOPE_PASSWORD="${L0_NOSCOPE_PASSWORD:?L0_NOSCOPE_PASSWORD is required}"
AUTHORIZED_MEDIA_ID="${L0_AUTHORIZED_MEDIA_ID:-media_sandbox}"

[[ "$RUN_ID" =~ ^[a-z0-9-]{6,32}$ ]] || {
  printf 'L0_RUN_ID must match [a-z0-9-]{6,32}.\n' >&2
  exit 2
}

# This script creates temporary users/media and is fail-closed. A production
# run must identify the corrected host and confirm its SSH key out-of-band.
# No keyscan/accept-new is performed here.
[[ "${L0_ALLOW_PRODUCTION_WRITE:-false}" == "I_HAVE_AUTHORIZATION" ]] || {
  printf 'Refusing production writes: set L0_ALLOW_PRODUCTION_WRITE=I_HAVE_AUTHORIZATION explicitly.\n' >&2
  exit 2
}
[[ "$TARGET_HOST" == "42.81.102.181" ]] || {
  printf 'Refusing production writes: L0_TARGET_HOST must be 42.81.102.181.\n' >&2
  exit 2
}
[[ "$API_HOST" == "127.0.0.1" || "$API_HOST" == "42.81.102.181" ]] || {
  printf 'Refusing production writes: L0_API_URL host must be localhost or 42.81.102.181.\n' >&2
  exit 2
}
[[ "$EXPECTED_SSH_FINGERPRINT" == "SHA256:rrH8nVzaA40JGueBrcH8Jq2tad//8FL0WbUEuHSOXZo" ]] || {
  printf 'Refusing production writes: SSH fingerprint is not the confirmed production key.\n' >&2
  exit 2
}
[[ "${L0_SSH_FINGERPRINT_CONFIRMED:-false}" == "true" ]] || {
  printf 'Refusing production writes: set L0_SSH_FINGERPRINT_CONFIRMED=true after out-of-band verification.\n' >&2
  exit 2
}

EVIDENCE_DIR="${L0_EVIDENCE_DIR:-${ROOT_DIR}/artifacts/acceptance}"
EVIDENCE_FILE="${EVIDENCE_DIR}/four-role-${RUN_ID}.json"

for command in curl jq openssl pnpm; do
  command -v "$command" >/dev/null 2>&1 || {
    printf 'Missing required command: %s\n' "$command" >&2
    exit 2
  }
done

ADMIN_TOKEN=""
UNAUTHORIZED_MEDIA_ID=""
UNAUTHORIZED_MEDIA_VERSION="0"
OPERATOR_USER_ID=""
ANALYST_USER_ID=""
NOSCOPE_USER_ID=""
OPERATOR_SCOPE_ID=""
ANALYST_SCOPE_ID=""

encrypt_password() {
  (
    cd "$FRONTEND_DIR"
    SCX_PLAIN_PASSWORD="$1" pnpm --filter @boot/web-ele exec node -e '
      const CryptoJS = require("crypto-js");
      const key = CryptoJS.SHA256("yzytech2026yzytech");
      process.stdout.write(CryptoJS.AES.encrypt(
        CryptoJS.enc.Utf8.parse(process.env.SCX_PLAIN_PASSWORD),
        key,
        { mode: CryptoJS.mode.ECB, padding: CryptoJS.pad.Pkcs7 },
      ).toString());
    ' 2>/dev/null
  )
}

expect_success() {
  local response="$1"
  local operation="$2"
  if [[ "$(jq -r '.code' <<<"$response")" != "0" ]]; then
    printf '%s failed: code=%s msg=%s\n' \
      "$operation" \
      "$(jq -r '.code' <<<"$response")" \
      "$(jq -r '.msg // "unknown"' <<<"$response")" >&2
    return 1
  fi
}

api_json() {
  local method="$1"
  local path="$2"
  local payload="${3:-}"
  if [[ -n "$payload" ]]; then
    curl -fsS --max-time 15 -X "$method" \
      -H "satoken: ${ADMIN_TOKEN}" \
      -H 'Content-Type: application/json' \
      --data-binary @- \
      "${API_URL}/boot${path}" <<<"$payload"
  else
    curl -fsS --max-time 15 -X "$method" \
      -H "satoken: ${ADMIN_TOKEN}" \
      "${API_URL}/boot${path}"
  fi
}

disable_user() {
  local id="$1"
  local username="$2"
  local nickname="$3"
  local role_id="$4"
  [[ -n "$id" ]] || return 0
  api_json PUT /system/user "$(jq -n \
    --arg id "$id" \
    --arg username "$username" \
    --arg nickname "$nickname" \
    --arg role "$role_id" \
    '{id:$id,username:$username,nickname:$nickname,status:"1",type:"0",deptId:"1",roles:[$role]}')" \
    >/dev/null 2>&1 || true
}

cleanup() {
  set +e
  if [[ -n "$OPERATOR_SCOPE_ID" ]]; then
    api_json DELETE "/subjects/media-scopes/${OPERATOR_SCOPE_ID}?mediaId=${AUTHORIZED_MEDIA_ID}" >/dev/null 2>&1
  fi
  if [[ -n "$ANALYST_SCOPE_ID" ]]; then
    api_json DELETE "/subjects/media-scopes/${ANALYST_SCOPE_ID}?mediaId=${AUTHORIZED_MEDIA_ID}" >/dev/null 2>&1
  fi
  disable_user "$OPERATOR_USER_ID" "l0-op-${RUN_ID}" "L0 临时运营 ${RUN_ID}" role_operator
  disable_user "$ANALYST_USER_ID" "l0-an-${RUN_ID}" "L0 临时分析 ${RUN_ID}" role_analyst
  disable_user "$NOSCOPE_USER_ID" "l0-no-${RUN_ID}" "L0 临时无范围 ${RUN_ID}" role_clk_user
  if [[ -n "$UNAUTHORIZED_MEDIA_ID" ]]; then
    api_json PUT "/subjects/media/${UNAUTHORIZED_MEDIA_ID}/status" \
      "$(jq -n --argjson version "$UNAUTHORIZED_MEDIA_VERSION" \
        '{status:"DISABLED",version:$version}')" >/dev/null 2>&1
  fi
}
trap cleanup EXIT

encoded_admin_password="$(encrypt_password "$ADMIN_PASSWORD")"
login_response="$(curl -fsS --max-time 15 -X POST \
  --data-urlencode "username=${ADMIN_USERNAME}" \
  --data-urlencode "password=${encoded_admin_password}" \
  "${API_URL}/boot/auth/token/login")"
expect_success "$login_response" "admin login"
ADMIN_TOKEN="$(jq -r '.data.tokenValue // empty' <<<"$login_response")"
[[ -n "$ADMIN_TOKEN" ]] || {
  printf 'Admin login did not return a token.\n' >&2
  exit 1
}

create_user() {
  local username="$1"
  local password="$2"
  local nickname="$3"
  local phone="$4"
  local role_id="$5"
  local create_response
  create_response="$(api_json POST /system/user "$(jq -n \
    --arg username "$username" \
    --arg password "$password" \
    --arg nickname "$nickname" \
    --arg phone "$phone" \
    --arg role "$role_id" \
    '{username:$username,password:$password,nickname:$nickname,phone:$phone,status:"0",type:"0",deptId:"1",roles:[$role]}')")"
  expect_success "$create_response" "create ${username}"

  local page_response
  page_response="$(curl -fsS --get --max-time 15 \
    -H "satoken: ${ADMIN_TOKEN}" \
    --data-urlencode 'current=1' \
    --data-urlencode 'size=20' \
    --data-urlencode "username=${username}" \
    "${API_URL}/boot/system/user/page")"
  expect_success "$page_response" "query ${username}"
  jq -r --arg username "$username" \
    '.data.records[] | select(.username == $username) | .id' \
    <<<"$page_response" | head -1
}

phone_suffix="${RUN_ID//[^0-9]/}"
phone_suffix="${phone_suffix: -7}"
while [[ "${#phone_suffix}" -lt 7 ]]; do
  phone_suffix="0${phone_suffix}"
done

OPERATOR_USER_ID="$(create_user "l0-op-${RUN_ID}" "$OPERATOR_PASSWORD" \
  "L0 临时运营 ${RUN_ID}" "1981${phone_suffix}" role_operator)"
ANALYST_USER_ID="$(create_user "l0-an-${RUN_ID}" "$ANALYST_PASSWORD" \
  "L0 临时分析 ${RUN_ID}" "1982${phone_suffix}" role_analyst)"
NOSCOPE_USER_ID="$(create_user "l0-no-${RUN_ID}" "$NOSCOPE_PASSWORD" \
  "L0 临时无范围 ${RUN_ID}" "1983${phone_suffix}" role_clk_user)"

media_response="$(api_json POST /subjects/media "$(jq -n --arg code "l0-hidden-${RUN_ID}" \
  --arg name "L0 未授权媒体 ${RUN_ID}" \
  '{code:$code,name:$name,timezone:"Asia/Shanghai",settlementCurrency:"CNY"}')")"
expect_success "$media_response" "create unauthorized media"
UNAUTHORIZED_MEDIA_ID="$(jq -r '.data.id' <<<"$media_response")"
UNAUTHORIZED_MEDIA_VERSION="$(jq -r '.data.version' <<<"$media_response")"

operator_scope_response="$(api_json POST /subjects/media-scopes "$(jq -n \
  --arg userId "$OPERATOR_USER_ID" \
  --arg mediaId "$AUTHORIZED_MEDIA_ID" \
  '{userId:$userId,mediaId:$mediaId}')")"
expect_success "$operator_scope_response" "grant operator scope"
OPERATOR_SCOPE_ID="$(jq -r '.data.id' <<<"$operator_scope_response")"

analyst_scope_response="$(api_json POST /subjects/media-scopes "$(jq -n \
  --arg userId "$ANALYST_USER_ID" \
  --arg mediaId "$AUTHORIZED_MEDIA_ID" \
  '{userId:$userId,mediaId:$mediaId}')")"
expect_success "$analyst_scope_response" "grant analyst scope"
ANALYST_SCOPE_ID="$(jq -r '.data.id' <<<"$analyst_scope_response")"

(
  cd "$FRONTEND_DIR/apps/web-ele"
  E2E_API_TARGET="$API_URL" \
  E2E_BASE_URL="$FRONTEND_URL" \
  L0_RUN_ID="$RUN_ID" \
  L0_ALLOW_PRODUCTION_WRITE=true \
  L0_AUTHORIZED_MEDIA_ID="$AUTHORIZED_MEDIA_ID" \
  L0_SAFE_WRITE_MEDIA_ID="$AUTHORIZED_MEDIA_ID" \
  L0_UNAUTHORIZED_MEDIA_ID="$UNAUTHORIZED_MEDIA_ID" \
  L0_ADMIN_USERNAME="$ADMIN_USERNAME" \
  L0_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  L0_OPERATOR_USERNAME="l0-op-${RUN_ID}" \
  L0_OPERATOR_PASSWORD="$OPERATOR_PASSWORD" \
  L0_ANALYST_USERNAME="l0-an-${RUN_ID}" \
  L0_ANALYST_PASSWORD="$ANALYST_PASSWORD" \
  L0_NOSCOPE_USERNAME="l0-no-${RUN_ID}" \
  L0_NOSCOPE_PASSWORD="$NOSCOPE_PASSWORD" \
    pnpm exec playwright test e2e/l0-four-role-production-gate.spec.ts --reporter=line
)

mkdir -p "$EVIDENCE_DIR"
# Metadata only: never persist credentials, tokens, request payloads or
# browser storage. The run output and audit query remain external evidence.
jq -n \
  --arg runId "$RUN_ID" \
  --arg targetHost "$TARGET_HOST" \
  --arg sshFingerprint "$EXPECTED_SSH_FINGERPRINT" \
  --arg apiHost "$API_URL" \
  '{runId:$runId,targetHost:$targetHost,sshFingerprint:$sshFingerprint,apiBase:$apiHost,
    acceptance:"four-role-production-gate",result:"passed",secretsPersisted:false}' \
  > "$EVIDENCE_FILE"
chmod 0640 "$EVIDENCE_FILE"
printf 'four-role-production-gate=passed run_id=%s\n' "$RUN_ID"
printf 'evidence=%s (metadata only; no credentials/tokens)\n' "$EVIDENCE_FILE"
