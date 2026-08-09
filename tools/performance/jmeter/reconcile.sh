#!/usr/bin/env bash
set -euo pipefail

PREFIX="${1:?usage: reconcile.sh PREFIX EXPECTED_UNIQUE [TIMEOUT_SECONDS]}"
EXPECTED="${2:?usage: reconcile.sh PREFIX EXPECTED_UNIQUE [TIMEOUT_SECONDS]}"
TIMEOUT="${3:-900}"

if [[ ! "$PREFIX" =~ ^[A-Za-z0-9_-]+$ || ! "$EXPECTED" =~ ^[0-9]+$ || ! "$TIMEOUT" =~ ^[0-9]+$ ]]; then
  echo "Unsafe reconciliation argument." >&2
  exit 1
fi

mysql_scalar() {
  MYSQL_PWD=local-jmeter-root-only mysql --host=127.0.0.1 --port=13316 --user=root \
    --batch --skip-column-names smart_converge_x --execute="$1"
}

redis_counters() {
  docker-compose -p scx-ocpx-jmeter -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/docker-compose.yml" \
    exec -T redis-primary redis-cli --raw EVAL \
    "local pending=0; local lag=0; for p=0,63 do local key='scx:ocpx:{feedback:'..p..'}:stream'; local groups=redis.call('XINFO','GROUPS',key); for _,g in ipairs(groups) do local name=''; local gp=0; local gl=0; for i=1,#g,2 do if g[i]=='name' then name=g[i+1] elseif g[i]=='pending' then gp=tonumber(g[i+1]) or 0 elseif g[i]=='lag' then gl=tonumber(g[i+1]) or 0 end end; if name=='scx-ocpx-persist' then pending=pending+gp; lag=lag+gl end end end; return {pending,lag}" 0
}

deadline=$((SECONDS + TIMEOUT))
while true; do
  facts="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_event WHERE source_direction='DOWNSTREAM_FEEDBACK' AND event_id LIKE '${PREFIX}%';")"
  dlq="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_ingress_dlq WHERE event_id LIKE '${PREFIX}%';")"
  redis_output="$(redis_counters)"
  pending="$(printf '%s\n' "$redis_output" | sed -n '1p')"
  lag="$(printf '%s\n' "$redis_output" | sed -n '2p')"
  if [[ $((facts + dlq)) -ge "$EXPECTED" && "$pending" -eq 0 && "$lag" -eq 0 ]]; then
    break
  fi
  if (( SECONDS >= deadline )); then
    echo "drain_timeout facts=${facts} dlq=${dlq} pending=${pending} lag=${lag}" >&2
    exit 1
  fi
  sleep 2
done

decisions="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_click_dedup_decision WHERE event_id LIKE '${PREFIX}%';")"
eligible="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_click_dedup_decision WHERE event_id LIKE '${PREFIX}%' AND status='ELIGIBLE';")"
deduplicated="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_click_dedup_decision WHERE event_id LIKE '${PREFIX}%' AND status='DEDUPLICATED';")"
skipped_no_device="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_click_dedup_decision WHERE event_id LIKE '${PREFIX}%' AND status='SKIPPED_NO_DEVICE';")"
upstream_jobs="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_delivery_job WHERE direction='FEEDBACK_UPSTREAM' AND event_id LIKE '${PREFIX}%';")"
deduplicated_jobs="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_delivery_job j JOIN scx_ocpx_click_dedup_decision d ON d.event_id=j.event_id WHERE d.event_id LIKE '${PREFIX}%' AND d.status='DEDUPLICATED';")"
billing="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_billing_entry WHERE charge_event_id LIKE '${PREFIX}%';")"
redis_output="$(redis_counters)"
pending="$(printf '%s\n' "$redis_output" | sed -n '1p')"
lag="$(printf '%s\n' "$redis_output" | sed -n '2p')"

printf '%s\n' \
  "prefix=${PREFIX}" \
  "expected_unique=${EXPECTED}" \
  "feedback_facts=${facts}" \
  "ingress_dlq=${dlq}" \
  "click_decisions=${decisions}" \
  "eligible=${eligible}" \
  "deduplicated=${deduplicated}" \
  "skipped_no_device=${skipped_no_device}" \
  "upstream_jobs=${upstream_jobs}" \
  "deduplicated_jobs=${deduplicated_jobs}" \
  "billing_entries=${billing}" \
  "redis_pending=${pending}" \
  "redis_lag=${lag}"

if [[ $((facts + dlq)) -ne "$EXPECTED" ]]; then
  echo "Accepted unique count does not close against facts plus DLQ." >&2
  exit 1
fi
if [[ "$decisions" -ne "$facts" || $((eligible + deduplicated + skipped_no_device)) -ne "$facts" ]]; then
  echo "Click facts do not close against click decision classes." >&2
  exit 1
fi
if [[ "$upstream_jobs" -ne $((eligible + skipped_no_device)) || "$deduplicated_jobs" -ne 0 || "$billing" -ne 0 ]]; then
	echo "Only effective clicks (including no-device fallback) may create upstream jobs, and billing must remain OFF." >&2
  exit 1
fi
if [[ "$pending" -ne 0 || "$lag" -ne 0 ]]; then
  echo "Redis did not fully drain." >&2
  exit 1
fi
