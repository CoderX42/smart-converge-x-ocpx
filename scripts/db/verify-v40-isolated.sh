#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DB_DIR="${ROOT_DIR}/scx-backend/db/smart-converge-x"
MIGRATION_FILE="${ROOT_DIR}/scx-backend/scx-boot/src/main/resources/db/migration/V40__add_ocpx_channel_ingress.sql"
V41_FILE="${ROOT_DIR}/scx-backend/scx-boot/src/main/resources/db/migration/V41__add_ocpx_channel_management.sql"
RUN_ID="${$}"
PASSWORD="scx_v40_local_only"
containers=("scx-v40-empty-${RUN_ID}" "scx-v40-upgrade-${RUN_ID}")
volumes=("scx-v40-empty-${RUN_ID}-data" "scx-v40-upgrade-${RUN_ID}-data")
evidence_dir="$(mktemp -d)"

cleanup() {
  for container in "${containers[@]}"; do
    [[ "$container" == scx-v40-* ]] || continue
    docker rm -f "$container" >/dev/null 2>&1 || true
  done
  for volume in "${volumes[@]}"; do
    [[ "$volume" == scx-v40-*-data ]] || continue
    docker volume rm "$volume" >/dev/null 2>&1 || true
  done
  rm -rf "$evidence_dir"
}
trap cleanup EXIT

start_mysql() {
  local container="$1"
  local volume="$2"
  docker volume create "$volume" >/dev/null
  docker run -d --name "$container" \
    -e MYSQL_ROOT_PASSWORD="$PASSWORD" \
    -e MYSQL_DATABASE=smart_converge_x \
    -p 127.0.0.1::3306 \
    -v "$volume:/var/lib/mysql" \
    mysql:8.0 \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_general_ci \
    --lower-case-table-names=1 >/dev/null
  for _ in {1..30}; do
    if docker exec "$container" mysqladmin ping -uroot -p"$PASSWORD" --silent >/dev/null 2>&1; then
      local port
      port="$(docker port "$container" 3306/tcp | awk -F: '{print $NF}')"
      if MYSQL_PWD="$PASSWORD" mysqladmin ping --host=127.0.0.1 --port="$port" --user=root --silent \
        >/dev/null 2>&1; then
        echo "$port"
        return
      fi
    fi
    sleep 2
  done
  echo "MySQL container did not become ready: $container" >&2
  exit 1
}

run_init() {
  local port="$1"
  shift
  (
    cd "$DB_DIR"
    env MYSQL_HOST=127.0.0.1 MYSQL_PORT="$port" MYSQL_USERNAME=root \
      MYSQL_PASSWORD="$PASSWORD" "$@" ./init-database.sh
  )
}

mysql_value() {
  local port="$1"
  local sql="$2"
  MYSQL_PWD="$PASSWORD" mysql --batch --skip-column-names \
    --host=127.0.0.1 --port="$port" --user=root smart_converge_x \
    --execute="$sql"
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  local message="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "$message: expected=$expected actual=$actual" >&2
    exit 1
  fi
}

empty_port="$(start_mysql "${containers[0]}" "${volumes[0]}")"
run_init "$empty_port"
assert_equals 1 "$(mysql_value "$empty_port" "SELECT COUNT(*) FROM scx_schema_migration WHERE version='V40';")" \
  "empty database did not record V40"
assert_equals 1 "$(mysql_value "$empty_port" "SELECT COUNT(*) FROM scx_schema_migration WHERE version='V41';")" \
  "empty database did not record V41"
assert_equals 4 "$(mysql_value "$empty_port" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('scx_channel_client','scx_channel_credential','scx_click_callback_target','scx_click_dedup_guard');")" \
  "empty database is missing V40 tables"
MYSQL_PWD="$PASSWORD" mysql --host=127.0.0.1 --port="$empty_port" --user=root smart_converge_x < "$MIGRATION_FILE"
MYSQL_PWD="$PASSWORD" mysql --host=127.0.0.1 --port="$empty_port" --user=root smart_converge_x < "$V41_FILE"
run_init "$empty_port" SCX_SKIP_BASELINE=true
assert_equals 2 "$(mysql_value "$empty_port" "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_callback_job' AND column_name IN ('payload_json','payload_hash');")" \
  "V41 callback snapshot columns are incomplete"
assert_equals 1 "$(mysql_value "$empty_port" "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_channel_client' AND index_name='uk_scx_channel_media_source';")" \
  "V41 channel uniqueness index is missing"

upgrade_port="$(start_mysql "${containers[1]}" "${volumes[1]}")"
run_init "$upgrade_port" SCX_MIGRATION_MAX_VERSION=39 SCX_SKIP_VERIFY=true
mysql_value "$upgrade_port" "INSERT INTO scx_raw_event (id,source,event_id,task_id,event_type,occurred_at,received_at,device_key_hash,payload_hash,payload_json,status) VALUES ('v40-preserve','sandbox','v40-preserve','task_demo','CLICK',NOW(3),NOW(3),REPEAT('a',64),REPEAT('b',64),JSON_OBJECT('probe',true),'ACCEPTED');"
MYSQL_PWD="$PASSWORD" mysql --host=127.0.0.1 --port="$upgrade_port" --user=root smart_converge_x < "$MIGRATION_FILE"
MYSQL_PWD="$PASSWORD" mysql --host=127.0.0.1 --port="$upgrade_port" --user=root smart_converge_x < "$MIGRATION_FILE"
assert_equals 1 "$(mysql_value "$upgrade_port" "SELECT COUNT(*) FROM scx_raw_event WHERE id='v40-preserve' AND JSON_EXTRACT(payload_json,'$.probe')=TRUE;")" \
  "V40 upgrade did not preserve the representative raw event"
run_init "$upgrade_port" SCX_SKIP_BASELINE=true SCX_MIGRATION_MAX_VERSION=40
run_init "$upgrade_port" SCX_SKIP_BASELINE=true SCX_MIGRATION_MAX_VERSION=40
assert_equals 3 "$(mysql_value "$upgrade_port" "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND index_name IN ('uk_scx_raw_receipt','uk_scx_raw_client_event','idx_scx_raw_processing');")" \
  "V40 raw event indexes are incomplete"
MYSQL_PWD="$PASSWORD" mysql --host=127.0.0.1 --port="$upgrade_port" --user=root smart_converge_x < "$V41_FILE"
MYSQL_PWD="$PASSWORD" mysql --host=127.0.0.1 --port="$upgrade_port" --user=root smart_converge_x < "$V41_FILE"
run_init "$upgrade_port" SCX_SKIP_BASELINE=true SCX_MIGRATION_MAX_VERSION=41
run_init "$upgrade_port" SCX_SKIP_BASELINE=true SCX_MIGRATION_MAX_VERSION=41
assert_equals 1 "$(mysql_value "$upgrade_port" "SELECT COUNT(*) FROM scx_raw_event WHERE id='v40-preserve' AND JSON_EXTRACT(payload_json,'$.probe')=TRUE;")" \
  "V41 upgrade did not preserve the representative raw event"
v40_checksum="$(shasum -a 256 "$MIGRATION_FILE" | awk '{print $1}')"
v41_checksum="$(shasum -a 256 "$V41_FILE" | awk '{print $1}')"
assert_equals "$v40_checksum" "$(mysql_value "$upgrade_port" "SELECT checksum FROM scx_schema_migration WHERE version='V40';")" \
  "V40 migration checksum does not match the frozen file"
assert_equals "$v41_checksum" "$(mysql_value "$upgrade_port" "SELECT checksum FROM scx_schema_migration WHERE version='V41';")" \
  "V41 migration checksum does not match the file"

verify_skip_locked() {
  local table="$1"
  local predicate="$2"
  local order_by="$3"
  local index_name="$4"
  local first_file="${evidence_dir}/${table}-first"
  local second_file="${evidence_dir}/${table}-second"
  MYSQL_PWD="$PASSWORD" mysql --batch --skip-column-names --host=127.0.0.1 --port="$upgrade_port" \
    --user=root smart_converge_x --execute="START TRANSACTION; SELECT id FROM ${table} FORCE INDEX (${index_name}) WHERE ${predicate} ORDER BY ${order_by} LIMIT 2 FOR UPDATE SKIP LOCKED; DO SLEEP(2); COMMIT;" > "$first_file" &
  local first_pid=$!
  sleep 0.3
  MYSQL_PWD="$PASSWORD" mysql --batch --skip-column-names --host=127.0.0.1 --port="$upgrade_port" \
    --user=root smart_converge_x --execute="START TRANSACTION; SELECT id FROM ${table} FORCE INDEX (${index_name}) WHERE ${predicate} ORDER BY ${order_by} LIMIT 2 FOR UPDATE SKIP LOCKED; COMMIT;" > "$second_file"
  wait "$first_pid"
  assert_equals 4 "$(sort -u "$first_file" "$second_file" | wc -l | tr -d ' ')" \
    "${table} SKIP LOCKED workers did not claim four distinct rows"
}

mysql_value "$upgrade_port" "INSERT INTO scx_raw_event (id,source,event_id,task_id,event_type,occurred_at,received_at,device_key_hash,payload_hash,payload_json,status,processing_status) VALUES ('lease-raw-1','sandbox','lease-raw-1','task_demo','CLICK',NOW(3),NOW(3),REPEAT('1',64),REPEAT('a',64),JSON_OBJECT(),'ACCEPTED','PENDING'),('lease-raw-2','sandbox','lease-raw-2','task_demo','CLICK',NOW(3),NOW(3),REPEAT('2',64),REPEAT('b',64),JSON_OBJECT(),'ACCEPTED','PENDING'),('lease-raw-3','sandbox','lease-raw-3','task_demo','CLICK',NOW(3),NOW(3),REPEAT('3',64),REPEAT('c',64),JSON_OBJECT(),'ACCEPTED','PENDING'),('lease-raw-4','sandbox','lease-raw-4','task_demo','CLICK',NOW(3),NOW(3),REPEAT('4',64),REPEAT('d',64),JSON_OBJECT(),'ACCEPTED','PENDING');"
mysql_value "$upgrade_port" "INSERT INTO scx_event_outbox (id,aggregate_id,event_type,payload_json,status,attempts,create_time) VALUES ('lease-outbox-1','lease-raw-1','LEASE_1',JSON_OBJECT(),'PENDING',0,NOW(3)),('lease-outbox-2','lease-raw-2','LEASE_2',JSON_OBJECT(),'PENDING',0,NOW(3)),('lease-outbox-3','lease-raw-3','LEASE_3',JSON_OBJECT(),'PENDING',0,NOW(3)),('lease-outbox-4','lease-raw-4','LEASE_4',JSON_OBJECT(),'PENDING',0,NOW(3));"
mysql_value "$upgrade_port" "INSERT INTO scx_callback_job (id,attribution_result_id,action_type,target_ref,status,attempts,create_time) VALUES ('lease-callback-1','lease-a-1','ORIGINAL','lease-target-1','PENDING',0,NOW(3)),('lease-callback-2','lease-a-2','ORIGINAL','lease-target-2','PENDING',0,NOW(3)),('lease-callback-3','lease-a-3','ORIGINAL','lease-target-3','PENDING',0,NOW(3)),('lease-callback-4','lease-a-4','ORIGINAL','lease-target-4','PENDING',0,NOW(3));"
verify_skip_locked scx_raw_event "processing_status='PENDING' AND (locked_until IS NULL OR locked_until < NOW(3))" "locked_until,received_at,id" "idx_scx_raw_lease_claim"
verify_skip_locked scx_event_outbox "status IN ('PENDING','RETRYING') AND (next_retry_at IS NULL OR next_retry_at <= NOW(3)) AND (locked_until IS NULL OR locked_until < NOW(3))" "locked_until,status,next_retry_at,create_time,id" "idx_scx_outbox_lease_claim"
verify_skip_locked scx_callback_job "status IN ('PENDING','RETRYING') AND (next_retry_at IS NULL OR next_retry_at <= NOW(3)) AND (locked_until IS NULL OR locked_until < NOW(3))" "locked_until,status,next_retry_at,create_time,id" "idx_scx_callback_lease_claim"

echo "V40/V41 isolated verification passed: empty, V39 upgrade, direct repeat, migration repeat, three two-worker lease races."
