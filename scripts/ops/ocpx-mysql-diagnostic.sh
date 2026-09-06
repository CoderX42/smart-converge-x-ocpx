#!/usr/bin/env bash
set -Eeuo pipefail

# Read-only OCPX MySQL diagnostic collector.
# Authentication is delegated to mysql client defaults-extra-file or login-path.
# No COUNT(*) over business tables, EXPLAIN ANALYZE, FLUSH, or SET GLOBAL is issued.

usage() {
  cat <<'USAGE'
Usage:
  ocpx-mysql-diagnostic.sh --defaults-extra-file PATH [options]
  ocpx-mysql-diagnostic.sh --login-path NAME [options]

Options:
  --database NAME       Database to inspect (default: smart_converge_x)
  --interval SECONDS    Delay between status snapshots, 1..60 (default: 15)
  --format tsv          Output tab-separated sections (default: tsv)
  --help                Show this help

The script prints only metadata, aggregate counters, digest text and index metadata.
It never prints connection credentials or full process-list SQL text.
USAGE
}

die() { echo "ERROR: $*" >&2; exit 2; }

AUTH_ARGS=()
DATABASE='smart_converge_x'
INTERVAL=15
FORMAT='tsv'

while [[ $# -gt 0 ]]; do
  case "$1" in
    --defaults-extra-file)
      [[ $# -ge 2 ]] || die '--defaults-extra-file requires PATH'
      [[ -r "$2" ]] || die "defaults file is not readable: $2"
      AUTH_ARGS+=("--defaults-extra-file=$2"); shift 2 ;;
    --login-path)
      [[ $# -ge 2 ]] || die '--login-path requires NAME'
      [[ "$2" =~ ^[A-Za-z0-9_.-]+$ ]] || die 'login-path contains unsupported characters'
      AUTH_ARGS+=("--login-path=$2"); shift 2 ;;
    --database)
      [[ $# -ge 2 ]] || die '--database requires NAME'
      [[ "$2" =~ ^[A-Za-z0-9_]+$ ]] || die 'database contains unsupported characters'
      DATABASE="$2"; shift 2 ;;
    --interval)
      [[ $# -ge 2 ]] || die '--interval requires SECONDS'
      [[ "$2" =~ ^[0-9]+$ ]] || die 'interval must be an integer'
      INTERVAL="$2"; (( INTERVAL >= 1 && INTERVAL <= 60 )) || die 'interval must be 1..60'; shift 2 ;;
    --format)
      [[ $# -ge 2 ]] || die '--format requires tsv'
      [[ "$2" == 'tsv' ]] || die 'only tsv output is supported'; FORMAT="$2"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done
[[ ${#AUTH_ARGS[@]} -gt 0 ]] || die 'provide --defaults-extra-file or --login-path'
command -v mysql >/dev/null 2>&1 || die 'mysql client is required'

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/scx-mysql-diagnostic.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

mysql_query() {
  local sql="$1"
  mysql "${AUTH_ARGS[@]}" --database="$DATABASE" --batch --raw --skip-column-names \
    --connect-timeout=5 --init-command='SET SESSION MAX_EXECUTION_TIME=5000' -e "$sql"
}

emit_header() {
  printf '# SCX OCPX MySQL diagnostic\n'
  printf '# generated_at\t%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  printf '# database\t%s\n' "$DATABASE"
  printf '# read_only\ttrue\n'
}

emit_identity() {
  printf '\n[identity]\nkey\tvalue\n'
  mysql_query "SELECT 'hostname',@@hostname UNION ALL SELECT 'port',@@port UNION ALL SELECT 'server_uuid',@@server_uuid UNION ALL SELECT 'version',@@version UNION ALL SELECT 'database',COALESCE(DATABASE(),'') UNION ALL SELECT 'read_only',@@read_only" \
    | awk -F '\t' '{print $1 "\t" $2}'
}

emit_variables() {
  printf '\n[variables]\nname\tvalue\n'
  mysql_query "SHOW GLOBAL VARIABLES WHERE Variable_name IN ('innodb_buffer_pool_size','innodb_buffer_pool_instances','innodb_io_capacity','innodb_io_capacity_max','innodb_flush_log_at_trx_commit','sync_binlog','max_connections','table_open_cache','tmp_table_size','max_heap_table_size','innodb_flush_neighbors','innodb_flush_sync','innodb_redo_log_capacity','performance_schema','slow_query_log','long_query_time')" \
    | awk -F '\t' '{print $1 "\t" $2}'
}

STATUS_SQL="SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_data_reads','Innodb_data_writes','Innodb_data_read','Innodb_data_written','Innodb_buffer_pool_reads','Innodb_buffer_pool_read_requests','Innodb_buffer_pool_pages_dirty','Innodb_log_waits','Innodb_os_log_fsyncs','Innodb_os_log_pending_fsyncs','Innodb_row_lock_time','Innodb_row_lock_waits','Created_tmp_disk_tables','Created_tmp_tables','Threads_connected','Threads_running','Com_select','Com_insert','Com_update','Com_delete','Questions')"

snapshot_status() {
  local destination="$1"
  mysql_query "$STATUS_SQL" > "$destination"
}

emit_status_delta() {
  local first="$1" second="$2"
  printf '\n[status_delta]\nname\tstart\tend\tdelta\n'
  awk -F '\t' '
    NR==FNR { start[$1]=$2; next }
    { end=$2+0; s=start[$1]+0; printf "%s\t%s\t%s\t%s\n", $1, s, end, end-s }
  ' "$first" "$second"
}

emit_table_io() {
  printf '\n[table_io_waits]\nschema\ttable\tread_ops\twrite_ops\tfetch_ops\tinsert_ops\tupdate_ops\tdelete_ops\tread_wait_ms\twrite_wait_ms\n'
  mysql_query "SELECT OBJECT_SCHEMA,OBJECT_NAME,COUNT_READ,COUNT_WRITE,COUNT_FETCH,COUNT_INSERT,COUNT_UPDATE,COUNT_DELETE,ROUND(SUM_TIMER_READ/1000000000000,3),ROUND(SUM_TIMER_WRITE/1000000000000,3) FROM performance_schema.table_io_waits_summary_by_table WHERE OBJECT_SCHEMA='${DATABASE}' AND OBJECT_NAME LIKE 'scx_ocpx_%' ORDER BY COUNT_WRITE+COUNT_READ DESC LIMIT 100" \
    | awk -F '\t' 'BEGIN{OFS="\t"}{print}'
}

emit_index_io() {
  printf '\n[index_io_waits]\nschema\ttable\tindex\tread_ops\twrite_ops\tfetch_ops\tinsert_ops\tupdate_ops\tdelete_ops\n'
  mysql_query "SELECT OBJECT_SCHEMA,OBJECT_NAME,COALESCE(INDEX_NAME,'<NO_INDEX>'),COUNT_READ,COUNT_WRITE,COUNT_FETCH,COUNT_INSERT,COUNT_UPDATE,COUNT_DELETE FROM performance_schema.table_io_waits_summary_by_index_usage WHERE OBJECT_SCHEMA='${DATABASE}' AND OBJECT_NAME LIKE 'scx_ocpx_%' ORDER BY COUNT_WRITE+COUNT_READ DESC LIMIT 200" \
    | awk -F '\t' 'BEGIN{OFS="\t"}{print}'
}

emit_digest() {
  printf '\n[top_statement_digests]\nschema\tdigest\tdigest_text\tcount\tavg_ms\trows_examined\trows_sent\ttmp_disk_tables\tno_index_used\terrors\n'
  mysql_query "SELECT COALESCE(SCHEMA_NAME,''),DIGEST,REPLACE(REPLACE(DIGEST_TEXT,CHAR(9),' '),CHAR(10),' '),COUNT_STAR,ROUND(SUM_TIMER_WAIT/COUNT_STAR/1000000000,3),SUM_ROWS_EXAMINED,SUM_ROWS_SENT,SUM_CREATED_TMP_DISK_TABLES,SUM_NO_INDEX_USED,SUM_ERRORS FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='${DATABASE}' ORDER BY SUM_TIMER_WAIT DESC LIMIT 30" \
    | awk -F '\t' 'BEGIN{OFS="\t"}{print}'
}

emit_table_sizes() {
  printf '\n[table_sizes_estimate]\nschema\ttable\testimated_rows\tdata_bytes\tindex_bytes\tupdate_time\n'
  mysql_query "SELECT TABLE_SCHEMA,TABLE_NAME,TABLE_ROWS,DATA_LENGTH,INDEX_LENGTH,COALESCE(DATE_FORMAT(UPDATE_TIME,'%Y-%m-%dT%H:%i:%s'),'') FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DATABASE}' AND TABLE_NAME LIKE 'scx_ocpx_%' ORDER BY DATA_LENGTH+INDEX_LENGTH DESC" \
    | awk -F '\t' 'BEGIN{OFS="\t"}{print}'
}

emit_indexes() {
  printf '\n[index_metadata]\nschema\ttable\tindex\tseq\tcolumn\tnon_unique\tcardinality\tindex_type\n'
  mysql_query "SELECT TABLE_SCHEMA,TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX,COLUMN_NAME,NON_UNIQUE,CARDINALITY,INDEX_TYPE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='${DATABASE}' AND TABLE_NAME LIKE 'scx_ocpx_%' ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX" \
    | awk -F '\t' 'BEGIN{OFS="\t"}{print}'
}

emit_locks_and_connections() {
  printf '\n[transactions]\ntrx_id\tstate\tstarted\twait_started\trows_locked\trows_modified\n'
  mysql_query "SELECT trx_id,COALESCE(trx_state,''),COALESCE(DATE_FORMAT(trx_started,'%Y-%m-%dT%H:%i:%s'),'') ,COALESCE(DATE_FORMAT(trx_wait_started,'%Y-%m-%dT%H:%i:%s'),'') ,trx_rows_locked,trx_rows_modified FROM information_schema.innodb_trx ORDER BY trx_started LIMIT 50" \
    | awk -F '\t' 'BEGIN{OFS="\t"}{print}'
  printf '\n[processlist_summary]\ncommand\tstate\tcount\tmax_time_s\n'
  mysql_query "SELECT COALESCE(COMMAND,''),COALESCE(STATE,''),COUNT(*),MAX(TIME) FROM information_schema.PROCESSLIST WHERE DB='${DATABASE}' GROUP BY COMMAND,STATE ORDER BY COUNT(*) DESC LIMIT 50" \
    | awk -F '\t' 'BEGIN{OFS="\t"}{print}'
}

emit_header
emit_identity
emit_variables
snapshot_status "$TMP_DIR/status.start"
printf '\n[snapshot]\nkey\tvalue\nstart_utc\t%s\ninterval_seconds\t%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$INTERVAL"
sleep "$INTERVAL"
snapshot_status "$TMP_DIR/status.end"
printf 'end_utc\t%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
emit_status_delta "$TMP_DIR/status.start" "$TMP_DIR/status.end"
emit_table_io
emit_index_io
emit_digest
emit_table_sizes
emit_indexes
emit_locks_and_connections
