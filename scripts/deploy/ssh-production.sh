#!/usr/bin/env bash
# Strict, fail-closed SSH wrapper for the single SCX production host.
# It never runs ssh-keyscan or accept-new and never edits known_hosts.
set -euo pipefail

PROD_HOST="42.81.102.181"
EXPECTED_FINGERPRINT="SHA256:rrH8nVzaA40JGueBrcH8Jq2tad//8FL0WbUEuHSOXZo"
SSH_USER="${SCX_PROD_SSH_USER:-root}"
KNOWN_HOSTS="${SCX_SSH_KNOWN_HOSTS:-${HOME}/.ssh/known_hosts}"

die() { printf '[ssh-production] ERROR: %s\n' "$*" >&2; exit 2; }
[[ -f "$KNOWN_HOSTS" ]] || die "known_hosts 不存在: ${KNOWN_HOSTS}；请先由基础设施负责人带外确认并安装指纹"
command -v ssh-keygen >/dev/null 2>&1 || die "缺少 ssh-keygen"
command -v ssh >/dev/null 2>&1 || die "缺少 ssh"

# ssh-keygen -F handles hashed and plain host entries without contacting the
# network.  An absent entry must never be accepted implicitly.
ssh-keygen -F "$PROD_HOST" -f "$KNOWN_HOSTS" >/dev/null 2>&1 \
  || die "${PROD_HOST} 未存在于 ${KNOWN_HOSTS}；拒绝连接未知指纹"

known_fingerprints="$(
  ssh-keygen -F "$PROD_HOST" -f "$KNOWN_HOSTS" \
    | awk '$1 !~ /^#/ && NF >= 3 {print $2 " " $3}' \
    | while read -r key_type key_blob; do
        [[ -n "$key_type" && -n "$key_blob" ]] || continue
        printf '%s %s\n' "$key_type" "$key_blob" \
          | ssh-keygen -lf /dev/stdin -E sha256 2>/dev/null \
          | awk '{print $2}'
      done
)"
grep -Fxq "$EXPECTED_FINGERPRINT" <<<"$known_fingerprints" \
  || die "${PROD_HOST} 的 known_hosts 指纹与已确认值不匹配；拒绝连接"

[[ "${SCX_SSH_FINGERPRINT_CONFIRMED:-}" == "$EXPECTED_FINGERPRINT" ]] \
  || die "请设置 SCX_SSH_FINGERPRINT_CONFIRMED=${EXPECTED_FINGERPRINT}（带外核对后）"

if [[ $# -eq 0 ]]; then
  set -- bash -l
fi
printf '[ssh-production] target=%s user=%s fingerprint=%s known_hosts=%s\n' \
  "$PROD_HOST" "$SSH_USER" "$EXPECTED_FINGERPRINT" "$KNOWN_HOSTS"
exec ssh \
  -o BatchMode=yes \
  -o StrictHostKeyChecking=yes \
  -o UserKnownHostsFile="$KNOWN_HOSTS" \
  -o IdentitiesOnly=yes \
  "${SSH_USER}@${PROD_HOST}" "$@"
