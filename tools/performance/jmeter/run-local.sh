#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/scx-backend"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
RESULT_DIR="${SCRIPT_DIR}/results/${RUN_ID}"
RUN_TMP="$(mktemp -d -t scx-ocpx-jmeter.XXXXXX)"
COMPOSE=(docker-compose -p scx-ocpx-jmeter -f "${SCRIPT_DIR}/docker-compose.yml")
ENGINE_PID=""
MOCK_PID=""
JMETER_PID=""
ENGINE_LOG=""
SCX_KEEP_STACK="${SCX_KEEP_STACK:-false}"
SCX_STAGES="${SCX_STAGES:-100:300 300:300 600:300 1000:300 2000:60 5000:60 10000:60 20000:60}"

mkdir -p "$RESULT_DIR"
chmod 700 "$RUN_TMP"
umask 077
openssl rand -hex 32 > "${RUN_TMP}/channel.secret"
device_key="$(openssl rand -hex 32)"
payload_key="$(openssl rand -hex 32)"
callback_key="$(openssl rand -hex 32)"
printf '%s\n' \
  "scx:" \
  "  ocpx:" \
  "    device-hmac-key: ${device_key}" \
  "    payload-aes-key: ${payload_key}" \
  "    callback-token-key: ${callback_key}" > "${RUN_TMP}/secrets.yml"
chmod 600 "${RUN_TMP}/channel.secret" "${RUN_TMP}/secrets.yml"
unset device_key payload_key callback_key

stop_engine() {
  if [[ -n "$ENGINE_PID" ]] && kill -0 "$ENGINE_PID" 2>/dev/null; then
    kill "$ENGINE_PID"
    for _ in $(seq 1 30); do
      kill -0 "$ENGINE_PID" 2>/dev/null || break
      sleep 1
    done
    if kill -0 "$ENGINE_PID" 2>/dev/null; then
      kill -9 "$ENGINE_PID"
    fi
    wait "$ENGINE_PID" 2>/dev/null || true
  fi
  ENGINE_PID=""
}

stop_mock() {
  if [[ -n "$MOCK_PID" ]] && kill -0 "$MOCK_PID" 2>/dev/null; then
    kill "$MOCK_PID"
    wait "$MOCK_PID" 2>/dev/null || true
  fi
  MOCK_PID=""
}

stop_jmeter() {
  if [[ "$JMETER_PID" =~ ^[0-9]+$ ]] && kill -0 "$JMETER_PID" 2>/dev/null; then
    kill "$JMETER_PID" 2>/dev/null || true
    for _ in $(seq 1 10); do
      kill -0 "$JMETER_PID" 2>/dev/null || break
      sleep 1
    done
    kill -9 "$JMETER_PID" 2>/dev/null || true
    wait "$JMETER_PID" 2>/dev/null || true
  fi
  JMETER_PID=""
}

cleanup() {
  stop_jmeter
  stop_engine
  stop_mock
  if [[ "$SCX_KEEP_STACK" != "true" ]]; then
    bash "${SCRIPT_DIR}/stop-local-stack.sh" >> "${RESULT_DIR}/cleanup.log" 2>&1 || true
  fi
  if [[ "$RUN_TMP" == /var/folders/*/T/scx-ocpx-jmeter.* || "$RUN_TMP" == /tmp/scx-ocpx-jmeter.* ]]; then
    find "$RUN_TMP" -type f -delete 2>/dev/null || true
    rmdir "$RUN_TMP" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

find_engine_jar() {
  find "${BACKEND_DIR}/scx-ocpx-engine/target" -maxdepth 1 -type f \
    \( -name 'scx-ocpx-engine.jar' -o -name 'scx-ocpx-engine-*.jar' \) \
    ! -name '*.original' | head -n 1
}

start_engine() {
  mode="${1:-base}"
  log_name="engine-${mode}-$(date -u +%H%M%S).log"
  ENGINE_LOG="${RESULT_DIR}/${log_name}"
  locations="file:${SCRIPT_DIR}/application-perf-local.yml,file:${RUN_TMP}/secrets.yml"
  if [[ "$mode" == "callback" ]]; then
    locations="${locations},file:${SCRIPT_DIR}/application-perf-callback.yml"
  fi
  engine_jar="$(find_engine_jar)"
  if [[ -z "$engine_jar" ]]; then
    echo "OCPX engine jar was not produced." >&2
    exit 1
  fi
  java -Xms512m -Xmx2g -jar "$engine_jar" \
    --spring.profiles.active=perf-local \
    --spring.config.additional-location="$locations" > "$ENGINE_LOG" 2>&1 &
  ENGINE_PID=$!
  for _ in $(seq 1 90); do
    if curl --fail --silent http://127.0.0.1:19999/ocpx/actuator/health >/dev/null 2>&1; then
      return
    fi
    if ! kill -0 "$ENGINE_PID" 2>/dev/null; then
      tail -n 80 "$ENGINE_LOG" >&2
      exit 1
    fi
    sleep 1
  done
  tail -n 80 "$ENGINE_LOG" >&2
  echo "OCPX engine did not become healthy." >&2
  exit 1
}

start_mock() {
  python3 "${SCRIPT_DIR}/mock_chain.py" --secret-file "${RUN_TMP}/channel.secret" \
    > "${RESULT_DIR}/mock.log" 2>&1 &
  MOCK_PID=$!
  for _ in $(seq 1 30); do
    curl --fail --silent http://127.0.0.1:18081/stats >/dev/null 2>&1 && return
    sleep 1
  done
  echo "Mock callback chain did not become ready." >&2
  exit 1
}

redis_counters() {
  "${COMPOSE[@]}" exec -T redis-primary redis-cli --raw EVAL \
    "local pending=0; local lag=0; for p=0,63 do local key='scx:ocpx:{feedback:'..p..'}:stream'; local groups=redis.call('XINFO','GROUPS',key); for _,g in ipairs(groups) do local name=''; local gp=0; local gl=0; for i=1,#g,2 do if g[i]=='name' then name=g[i+1] elseif g[i]=='pending' then gp=tonumber(g[i+1]) or 0 elseif g[i]=='lag' then gl=tonumber(g[i+1]) or 0 end end; if name=='scx-ocpx-persist' then pending=pending+gp; lag=lag+gl end end end; return {pending,lag}" 0
}

record_environment() {
  {
    date -u '+timestamp=%Y-%m-%dT%H:%M:%SZ'
    docker info --format 'docker_cpus={{.NCPU}} docker_memory_bytes={{.MemTotal}} driver={{.Driver}}'
    java -version 2>&1 | head -n 1
    jmeter --version 2>&1 | sed -n '1,8p'
  } > "${RESULT_DIR}/environment.txt"
}

run_functional_batch() {
  prefix="${RUN_ID}-functional"
  python3 "${SCRIPT_DIR}/functional_batch.py" --secret-file "${RUN_TMP}/channel.secret" \
    --prefix "$prefix" --output "${RESULT_DIR}/functional-results.json"
  bash "${SCRIPT_DIR}/reconcile.sh" "$prefix" 28 180 | tee "${RESULT_DIR}/functional-reconcile.txt"
  grep -q '^eligible=16$' "${RESULT_DIR}/functional-reconcile.txt"
  grep -q '^deduplicated=11$' "${RESULT_DIR}/functional-reconcile.txt"
  grep -q '^skipped_no_device=1$' "${RESULT_DIR}/functional-reconcile.txt"
  MYSQL_PWD=local-jmeter-root-only mysql --host=127.0.0.1 --port=13316 --user=root \
    --table smart_converge_x --execute="SELECT device_platform,device_id_type,device_key_version,COUNT(*) AS facts FROM scx_ocpx_event WHERE source_direction='DOWNSTREAM_FEEDBACK' AND event_id LIKE '${prefix}%' GROUP BY device_platform,device_id_type,device_key_version ORDER BY device_platform,device_id_type;" \
    > "${RESULT_DIR}/functional-device-matrix.txt"
  bash "${SCRIPT_DIR}/reset-test-data.sh"
}

run_fault_batch() {
  fault_log="${RESULT_DIR}/fault-results.txt"
  : > "$fault_log"

  "${COMPOSE[@]}" stop redis-replica >/dev/null
  for _ in $(seq 1 30); do
    connected="$("${COMPOSE[@]}" exec -T redis-primary redis-cli INFO replication | tr -d '\r' | sed -n 's/^connected_slaves://p')"
    [[ "$connected" == "0" ]] && break
    sleep 1
  done
  status="$(python3 "${SCRIPT_DIR}/send_event.py" --secret-file "${RUN_TMP}/channel.secret" --event-id "${RUN_ID}-fault-replica")"
  echo "redis_replica_unavailable_http=${status}" | tee -a "$fault_log"
  [[ "$status" == "503" ]]
  "${COMPOSE[@]}" start redis-replica >/dev/null
  for _ in $(seq 1 30); do
    link="$("${COMPOSE[@]}" exec -T redis-replica redis-cli INFO replication 2>/dev/null | tr -d '\r' | sed -n 's/^master_link_status://p')"
    [[ "$link" == "up" ]] && break
    sleep 1
  done
  [[ "$link" == "up" ]]

  "${COMPOSE[@]}" stop redis-primary >/dev/null
  status="$(python3 "${SCRIPT_DIR}/send_event.py" --secret-file "${RUN_TMP}/channel.secret" --event-id "${RUN_ID}-fault-primary")"
  echo "redis_primary_unavailable_http=${status}" | tee -a "$fault_log"
  [[ "$status" == "503" || "$status" == "CLIENT_ERROR" ]]
  "${COMPOSE[@]}" start redis-primary >/dev/null
  for _ in $(seq 1 30); do
    "${COMPOSE[@]}" exec -T redis-primary redis-cli ping 2>/dev/null | grep -q PONG && break
    sleep 1
  done
  for _ in $(seq 1 30); do
    link="$("${COMPOSE[@]}" exec -T redis-replica redis-cli INFO replication 2>/dev/null | tr -d '\r' | sed -n 's/^master_link_status://p')"
    [[ "$link" == "up" ]] && break
    sleep 1
  done
  sleep 2

  bash "${SCRIPT_DIR}/reset-test-data.sh"
  mysql_container="$("${COMPOSE[@]}" ps -q mysql)"
  docker pause "$mysql_container" >/dev/null
  pending_prefix="${RUN_ID}-fault-pending"
  for index in $(seq 1 20); do
    status="$(python3 "${SCRIPT_DIR}/send_event.py" --secret-file "${RUN_TMP}/channel.secret" \
      --event-id "${pending_prefix}-${index}" --device "synthetic-fault-${index}")"
    [[ "$status" == "202" ]]
  done
  captured="$("${COMPOSE[@]}" exec -T redis-primary redis-cli --raw EVAL \
    "for p=0,63 do local key='scx:ocpx:{feedback:'..p..'}:stream'; local rows=redis.call('XRANGE',key,'-','+'); for _,row in ipairs(rows) do local fields=row[2]; local event=''; local envelope=''; for i=1,#fields,2 do if fields[i]=='event_id' then event=fields[i+1] elseif fields[i]=='envelope' then envelope=fields[i+1] end end; if event=='${pending_prefix}-1' then return {key,envelope} end end end; return {}" 0)"
  captured_key="$(printf '%s\n' "$captured" | sed -n '1p')"
  captured_envelope="$(printf '%s\n' "$captured" | sed -n '2p')"
  [[ -n "$captured_key" && -n "$captured_envelope" ]]
  stop_engine
  docker unpause "$mysql_container" >/dev/null
  start_engine base
  started_drain="$SECONDS"
  bash "${SCRIPT_DIR}/reconcile.sh" "$pending_prefix" 20 180 | tee -a "$fault_log"
  echo "restart_pending_drain_seconds=$((SECONDS - started_drain))" | tee -a "$fault_log"

  "${COMPOSE[@]}" exec -T redis-primary redis-cli XADD "$captured_key" '*' \
    event_id "${pending_prefix}-1" envelope "$captured_envelope" >/dev/null
  bash "${SCRIPT_DIR}/reconcile.sh" "$pending_prefix" 20 120 > "${RESULT_DIR}/fault-commit-before-ack-reconcile.txt"
  echo "commit_before_ack_replay_facts=20" | tee -a "$fault_log"
  unset captured captured_envelope

  invalid_event="${RUN_ID}-invalid-envelope"
  "${COMPOSE[@]}" exec -T redis-primary redis-cli XADD 'scx:ocpx:{feedback:0}:stream' '*' \
    event_id "$invalid_event" envelope '{not-json' >/dev/null
  for _ in $(seq 1 60); do
    invalid_dlq="$(MYSQL_PWD=local-jmeter-root-only mysql --host=127.0.0.1 --port=13316 --user=root \
      --batch --skip-column-names smart_converge_x --execute="SELECT COUNT(*) FROM scx_ocpx_ingress_dlq WHERE event_id='${invalid_event}' AND reason LIKE 'INVALID_ENVELOPE:%';")"
    [[ "$invalid_dlq" == "1" ]] && break
    sleep 1
  done
  echo "invalid_envelope_dlq=${invalid_dlq}" | tee -a "$fault_log"
  [[ "$invalid_dlq" == "1" ]]
  bash "${SCRIPT_DIR}/reset-test-data.sh"
}

run_jmeter_stage() {
  qps="$1"
  duration="$2"
  label="$3"
  prefix="${RUN_ID}-${label}-qps${qps}"
  stage_dir="${RESULT_DIR}/${label}-qps${qps}"
  mkdir -p "$stage_dir"
  threads=$((qps / 5 + 128))
  if [[ "$threads" -gt 4096 ]]; then
    threads=4096
  fi
  tpm=$((qps * 60))
  jtl="${stage_dir}/results.jtl"
  metrics="${stage_dir}/resources.tsv"
  printf 'timestamp\tredis_pending\tredis_lag\tengine_rss_kb\tengine_cpu_pct\tjmeter_rss_kb\tjmeter_cpu_pct\tdocker_stats\n' > "$metrics"

  HEAP='-Xms512m -Xmx3g -XX:MaxMetaspaceSize=256m' jmeter -n \
    -t "${SCRIPT_DIR}/scx-ocpx-feedback.jmx" \
    -l "$jtl" -j "${stage_dir}/jmeter.log" \
    -JsecretFile="${RUN_TMP}/channel.secret" -JrunId="$prefix" -Jqps="$qps" -Jtpm="$tpm" \
    -Jduration="$duration" -Jthreads="$threads" -Jramp=1 -JdevicePool=0 \
    -Jjmeter.save.saveservice.output_format=csv \
    -Jjmeter.save.saveservice.print_field_names=true \
    -Jjmeter.save.saveservice.samplerData=false \
    -Jjmeter.save.saveservice.requestHeaders=false \
    -Jjmeter.save.saveservice.responseHeaders=false \
    -Jjmeter.save.saveservice.response_data=false \
    -Jjmeter.save.saveservice.response_data.on_error=false &
  jmeter_pid=$!
  JMETER_PID="$jmeter_pid"
  while kill -0 "$jmeter_pid" 2>/dev/null; do
    counters="$(redis_counters 2>/dev/null || printf '0\n0\n')"
    pending="$(printf '%s\n' "$counters" | sed -n '1p')"
    lag="$(printf '%s\n' "$counters" | sed -n '2p')"
    engine_rss="0"
    engine_cpu="0"
    if [[ "$ENGINE_PID" =~ ^[0-9]+$ ]]; then
      read -r engine_rss engine_cpu < <(ps -o rss=,%cpu= -p "$ENGINE_PID" 2>/dev/null || printf '0 0\n')
    fi
    jmeter_java_pid="$(pgrep -P "$jmeter_pid" 2>/dev/null | head -n 1 || true)"
    jmeter_rss="0"
    jmeter_cpu="0"
    if [[ "$jmeter_java_pid" =~ ^[0-9]+$ ]]; then
      read -r jmeter_rss jmeter_cpu < <(ps -o rss=,%cpu= -p "$jmeter_java_pid" 2>/dev/null || printf '0 0\n')
    fi
    docker_stats="$(docker stats --no-stream --format '{{.Name}}={{.CPUPerc}}/{{.MemUsage}}' 2>/dev/null | grep '^scx-ocpx-jmeter-' | tr '\n' ';' || true)"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      "${pending:-0}" "${lag:-0}" "${engine_rss:-0}" "${engine_cpu:-0}" \
      "${jmeter_rss:-0}" "${jmeter_cpu:-0}" "$docker_stats" >> "$metrics"
    sleep 5
  done
  set +e
  wait "$jmeter_pid"
  jmeter_status=$?
  JMETER_PID=""
  set -e
  if [[ "$jmeter_status" -ne 0 ]]; then
    echo "JMeter exited with ${jmeter_status} at ${qps} QPS." >&2
    return 1
  fi
  python3 "${SCRIPT_DIR}/summarize_jtl.py" "$jtl" --output "${stage_dir}/summary.json"
  requests="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["requests"])' "${stage_dir}/summary.json")"
  failures="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["failures"])' "${stage_dir}/summary.json")"
  accepted="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["statuses"].get("202", 0))' "${stage_dir}/summary.json")"
  measured_qps="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["measuredQps"])' "${stage_dir}/summary.json")"
  python3 - "$qps" "$measured_qps" <<'PY'
import sys
target = float(sys.argv[1])
actual = float(sys.argv[2])
if actual < target * 0.95:
    raise SystemExit(f"measured QPS {actual} is below 95% of target {target}")
PY
  started_drain="$SECONDS"
  set +e
  bash "${SCRIPT_DIR}/reconcile.sh" "$prefix" "$accepted" 1200 | tee "${stage_dir}/reconcile.txt"
  reconcile_status="${PIPESTATUS[0]}"
  set -e
  drain_seconds=$((SECONDS - started_drain))
  max_pending="$(awk -F '\t' 'NR>1 && $2+0>max {max=$2+0} END {print max+0}' "$metrics")"
  max_lag="$(awk -F '\t' 'NR>1 && $3+0>max {max=$3+0} END {print max+0}' "$metrics")"
  printf '%s\n' "target_qps=${qps}" "duration_seconds=${duration}" "threads=${threads}" \
    "requests=${requests}" "accepted_202=${accepted}" "failures=${failures}" \
    "measured_qps=${measured_qps}" "max_pending=${max_pending}" \
    "max_lag=${max_lag}" "drain_seconds=${drain_seconds}" > "${stage_dir}/stage.txt"
  if [[ "$reconcile_status" -ne 0 || "$failures" -ne 0 || "$accepted" -ne "$requests" ]]; then
    echo "Stage ${qps} QPS failed acceptance: requests=${requests} accepted_202=${accepted} failures=${failures} reconcile_status=${reconcile_status}." >&2
    return 1
  fi
}

run_callback_batch() {
  stop_engine
  start_mock
  start_engine callback
  run_jmeter_stage 100 60 callback
  prefix="${RUN_ID}-callback-qps100"
  bash "${SCRIPT_DIR}/reconcile_callback.sh" "$prefix" http://127.0.0.1:18081/stats 480 \
    | tee "${RESULT_DIR}/callback-qps100/callback-reconcile.txt"
  stop_engine
  stop_mock
  bash "${SCRIPT_DIR}/reset-test-data.sh"
  start_engine base
}

record_environment
echo "Building OCPX engine..."
(cd "$BACKEND_DIR" && mvn -pl scx-ocpx-engine -am -DskipTests package) > "${RESULT_DIR}/engine-build.log" 2>&1
bash "${SCRIPT_DIR}/start-local-stack.sh" "${RUN_TMP}/channel.secret" | tee "${RESULT_DIR}/stack-start.log"
start_engine base

echo "Running functional matrix..."
run_functional_batch
echo "Running fault-injection matrix..."
run_fault_batch
echo "Running isolated 10% callback and 500/500/200 retry batch..."
run_callback_batch

completed_stages=0
for stage in $SCX_STAGES; do
  qps="${stage%%:*}"
  duration="${stage##*:}"
  if ! [[ "$qps" =~ ^[0-9]+$ && "$duration" =~ ^[0-9]+$ ]]; then
    echo "Invalid SCX_STAGES entry: ${stage}" >&2
    exit 1
  fi
  echo "Running Feedback stage: target=${qps} QPS duration=${duration}s"
  if ! run_jmeter_stage "$qps" "$duration" feedback; then
    echo "Stopped before the next stage because ${qps} QPS did not pass." | tee "${RESULT_DIR}/stage-stop.txt"
    break
  fi
  completed_stages=$((completed_stages + 1))
  bash "${SCRIPT_DIR}/reset-test-data.sh"
done

printf '%s\n' "run_id=${RUN_ID}" "completed_stages=${completed_stages}" "configured_stages=${SCX_STAGES}" \
  "result_dir=${RESULT_DIR}" > "${RESULT_DIR}/run-summary.txt"
python3 "${SCRIPT_DIR}/generate_report.py" "$RESULT_DIR"
echo "Local JMeter run finished. Results: ${RESULT_DIR}"
