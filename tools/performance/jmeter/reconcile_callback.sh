#!/usr/bin/env bash
set -euo pipefail

PREFIX="${1:?usage: reconcile_callback.sh PREFIX MOCK_STATS_URL [TIMEOUT_SECONDS]}"
STATS_URL="${2:?usage: reconcile_callback.sh PREFIX MOCK_STATS_URL [TIMEOUT_SECONDS]}"
TIMEOUT="${3:-420}"

if [[ ! "$PREFIX" =~ ^[A-Za-z0-9_-]+$ || ! "$TIMEOUT" =~ ^[0-9]+$ ]]; then
  echo "Unsafe callback reconciliation argument." >&2
  exit 1
fi

mysql_scalar() {
  MYSQL_PWD=local-jmeter-root-only mysql --host=127.0.0.1 --port=13316 --user=root \
    --batch --skip-column-names smart_converge_x --execute="$1"
}

deadline=$((SECONDS + TIMEOUT))
while true; do
  upstream_open="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_delivery_job WHERE direction='FEEDBACK_UPSTREAM' AND event_id LIKE '${PREFIX}%' AND status<>'SUCCESS';")"
  callback_facts="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_event WHERE source_direction='UPSTREAM_CALLBACK' AND parent_event_id LIKE '${PREFIX}%';")"
  callback_open="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_delivery_job WHERE direction='CALLBACK_DOWNSTREAM' AND source_event_id LIKE '${PREFIX}%' AND status<>'SUCCESS';")"
  stats="$(curl --fail --silent "$STATS_URL")"
  callback_unique="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["callbackUnique"])' <<< "$stats")"
  downstream_success="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["downstreamSuccessKeys"])' <<< "$stats")"
  if [[ "$upstream_open" -eq 0 && "$callback_facts" -eq "$callback_unique" && "$callback_open" -eq 0 && "$downstream_success" -eq "$callback_unique" ]]; then
    break
  fi
  if (( SECONDS >= deadline )); then
    echo "callback_drain_timeout upstream_open=${upstream_open} callback_facts=${callback_facts} callback_unique=${callback_unique} callback_open=${callback_open} downstream_success=${downstream_success}" >&2
    exit 1
  fi
  sleep 2
done

callback_decisions="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_deduction_decision d JOIN scx_ocpx_event e ON e.event_id=d.callback_event_id AND e.source_direction='UPSTREAM_CALLBACK' WHERE e.parent_event_id LIKE '${PREFIX}%';")"
callback_jobs="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_delivery_job WHERE direction='CALLBACK_DOWNSTREAM' AND source_event_id LIKE '${PREFIX}%';")"
callback_bad_status="$(mysql_scalar "SELECT COUNT(*) FROM scx_ocpx_delivery_job WHERE direction='CALLBACK_DOWNSTREAM' AND source_event_id LIKE '${PREFIX}%' AND status IN ('PENDING','RETRY','DEAD','PROCESSING');")"
stats="$(curl --fail --silent "$STATS_URL")"
callback_requests="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["callbackHttpRequests"])' <<< "$stats")"
callback_success="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["callbackHttpSuccess"])' <<< "$stats")"
downstream_unique="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["downstreamUniqueKeys"])' <<< "$stats")"
downstream_attempts="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["downstreamAttempts"])' <<< "$stats")"
downstream_success="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["downstreamSuccessKeys"])' <<< "$stats")"
invalid_signatures="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["invalidSignatures"])' <<< "$stats")"

printf '%s\n' \
  "prefix=${PREFIX}" \
  "callback_unique=${callback_unique}" \
  "callback_http_requests=${callback_requests}" \
  "callback_http_success=${callback_success}" \
  "callback_facts=${callback_facts}" \
  "deduction_decisions=${callback_decisions}" \
  "downstream_jobs=${callback_jobs}" \
  "downstream_unique_keys=${downstream_unique}" \
  "downstream_attempts=${downstream_attempts}" \
  "downstream_success_keys=${downstream_success}" \
  "invalid_signatures=${invalid_signatures}" \
  "callback_bad_status=${callback_bad_status}"

if [[ "$callback_unique" -le 0 || "$callback_requests" -ne $((callback_unique * 2)) || "$callback_success" -ne "$callback_requests" ]]; then
  echo "The 10% callback generator or duplicate callback acceptance did not close." >&2
  exit 1
fi
if [[ "$callback_facts" -ne "$callback_unique" || "$callback_decisions" -ne "$callback_unique" || "$callback_jobs" -ne "$callback_unique" ]]; then
  echo "Callback fact, deduction decision, and downstream job counts do not close." >&2
  exit 1
fi
if [[ "$downstream_unique" -ne "$callback_unique" || "$downstream_attempts" -ne $((callback_unique * 3)) || "$downstream_success" -ne "$callback_unique" || "$invalid_signatures" -ne 0 || "$callback_bad_status" -ne 0 ]]; then
  echo "Downstream 500/500/200 retry and idempotency-key reconciliation failed." >&2
  exit 1
fi

