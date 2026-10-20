#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/schema-required-tables.sh"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-smart_converge_x}"
mysql_args=(--default-character-set=utf8mb4 --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME")
if [[ -n "${MYSQL_PASSWORD:-}" ]]; then export MYSQL_PWD="$MYSQL_PASSWORD"; fi

scalar() { mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" --execute="$1"; }
fail() { echo "Database verification failed: $1" >&2; exit 1; }

table_list="$(printf "'%s'," "${SCX_REQUIRED_TABLES[@]}")"; table_list="${table_list%,}"
actual_count="$(scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN (${table_list})")"
[[ "$actual_count" -eq "${#SCX_REQUIRED_TABLES[@]}" ]] || fail "required table manifest is incomplete"

business_tables="$(scalar "SELECT COALESCE(GROUP_CONCAT(table_name ORDER BY table_name),'') FROM information_schema.tables WHERE table_schema=DATABASE() AND table_comment LIKE 'BUSINESS:%'")"
expected_business_tables='scx_ocpx_advertiser,scx_ocpx_creative,scx_ocpx_deduction_rule,scx_ocpx_media,scx_ocpx_monitor_task,scx_ocpx_promotion_link'
[[ "$business_tables" == "$expected_business_tables" ]] || fail "the OCPX business main tables are not exact"

ocpx_tables="$(scalar "SELECT COALESCE(GROUP_CONCAT(table_name ORDER BY table_name),'') FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'scx_ocpx\\_%' AND table_name NOT IN ('scx_ocpx_rta_profile','scx_ocpx_rta_task_binding','scx_ocpx_rta_decision_fact','scx_ocpx_rta_hourly_metric')")"
expected_ocpx_tables='scx_ocpx_advertiser,scx_ocpx_advertiser_account,scx_ocpx_billing_config_audit,scx_ocpx_billing_entry,scx_ocpx_creative,scx_ocpx_creative_promotion_link,scx_ocpx_deduction_rule,scx_ocpx_event_fact,scx_ocpx_flow_log,scx_ocpx_flow_log_detail,scx_ocpx_hourly_metric,scx_ocpx_log_callback,scx_ocpx_log_feedback,scx_ocpx_log_feedback_day,scx_ocpx_media,scx_ocpx_monitor_task,scx_ocpx_promotion_link,scx_ocpx_protocol_allowed_host,scx_ocpx_report_mat,scx_ocpx_report_mat_temp,scx_ocpx_rta_binding,scx_ocpx_rta_hour_stat,scx_ocpx_rta_log,scx_ocpx_rta_media_access,scx_ocpx_rta_upstream'
[[ "$ocpx_tables" == "$expected_ocpx_tables" ]] || fail "OCPX truthful facts, account, ledger and reporting tables are incomplete"

assert_exact_columns() {
  local table="$1" expected="$2" actual
  actual="$(scalar "SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='${table}'")"
  [[ "$actual" == "$expected" ]] || fail "${table} columns do not match the current management model"
}

assert_exact_columns scx_ocpx_advertiser 'id,advertiser_name,upstream_protocol,account_user_id,credential_config_key,bid_url,macro_params,create_time,update_time'
assert_exact_columns scx_ocpx_protocol_allowed_host 'id,protocol_type,host,create_time,update_time'
assert_exact_columns scx_ocpx_promotion_link 'id,advertiser_id,link_name,platform,link_channel,link_url,deeplink,link_ext,remark,is_delete,create_time,update_time'
assert_exact_columns scx_ocpx_creative 'id,advertiser_id,creative_name,creative_type,asset_url,source_material_id,file_name,mime_type,file_size,width,height,checksum_sha256,status,remark,is_delete,create_by,create_time,update_time'
assert_exact_columns scx_ocpx_creative_promotion_link 'advertiser_id,creative_id,promotion_link_id,create_by,create_time'
assert_exact_columns scx_ocpx_media 'id,media_name,credential_config_key,uri,macro_params,create_time,update_time'
assert_exact_columns scx_ocpx_monitor_task 'id,monitor_name,advertiser_id,promotion_link_id,media_id,rta_enabled,billing_enabled,billing_type,unit_price,conversion_event,biz_id,show_url,clk_url,remark,is_delete,create_time,update_time'
assert_exact_columns scx_ocpx_billing_config_audit 'id,monitor_task_id,advertiser_id,action_type,before_config,after_config,operator_user_id,occurred_at'
assert_exact_columns scx_ocpx_deduction_rule 'id,promotion_link_id,media_id,ratio,is_delete,create_time,update_time'
assert_exact_columns scx_ocpx_event_fact 'id,fact_key,flow_type,event_code,request_id,advertiser_id,promotion_link_id,media_id,monitor_task_id,target_conversion,billing_enabled_snapshot,billing_type_snapshot,billing_unit_price_snapshot,conversion_event_snapshot,deduction_decision,deduction_ratio_snapshot,deduction_decided_at,spend_amount,currency_amount,billing_status,billing_reason_code,received_at'
assert_exact_columns scx_ocpx_hourly_metric 'id,time_hour,advertiser_id,promotion_link_id,media_id,monitor_task_id,impression_count,click_count,target_conversion_count,callback_count,deduction_passed_count,deduction_deducted_count,activate_count,evoke_count,login_count,register_count,payment_count,customer_acquisition_count,next_stay_count,visits_count,authentication_count,place_order_count,finish_the_order_count,first_activation_count,first_order_count,download_count,activity_count,addiction_count,spend_amount,currency_amount,rejected_click_count,rejected_conversion_count,update_time'

advertiser_account_column="$(scalar "SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_advertiser'
 AND column_name='account_user_id' AND data_type='varchar'
 AND character_maximum_length=64 AND is_nullable='YES'")"
[[ "$advertiser_account_column" -eq 1 ]] || fail "advertiser account_user_id must be nullable VARCHAR(64)"

invalid_upstream_protocols="$(scalar "SELECT COUNT(*) FROM scx_ocpx_advertiser
 WHERE upstream_protocol IS NOT NULL
 AND upstream_protocol NOT IN ('TEMPLATE_GET','AERIM','WEIFENG','GRAVITY','RENREN_VIDEO','PHOENIX')")"
[[ "$invalid_upstream_protocols" -eq 0 ]] || fail "advertiser upstream protocol is invalid"

invalid_protocol_hosts="$(scalar "SELECT COUNT(*) FROM scx_ocpx_protocol_allowed_host
 WHERE protocol_type NOT IN ('AERIM','WEIFENG','GRAVITY','RENREN_VIDEO','PHOENIX') OR host<>LOWER(host)
 OR host LIKE '%://%' OR host LIKE '%/%' OR host LIKE '%:%' OR host LIKE '%*%'")"
[[ "$invalid_protocol_hosts" -eq 0 ]] || fail "protocol allowed hosts contain an invalid value"

advertiser_account_unique="$(scalar "SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_advertiser'
 AND index_name='uk_ocpx_advertiser_account_user' AND non_unique=0
 AND column_name='account_user_id'")"
[[ "$advertiser_account_unique" -eq 1 ]] || fail "advertiser account_user_id unique index is missing"

advertiser_account_fk="$(scalar "SELECT COUNT(*) FROM information_schema.key_column_usage
 WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_advertiser'
 AND constraint_name='fk_ocpx_advertiser_account_user'
 AND column_name='account_user_id' AND referenced_table_name='sys_user'
 AND referenced_column_name='id'")"
[[ "$advertiser_account_fk" -eq 1 ]] || fail "advertiser account_user_id foreign key is missing"

monitor_text_urls="$(scalar "SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_task'
 AND column_name IN ('show_url','clk_url') AND data_type='text'")"
[[ "$monitor_text_urls" -eq 2 ]] || fail "monitor show_url and clk_url must both be TEXT"

invalid_conversion_events="$(scalar "SELECT COUNT(*) FROM scx_ocpx_monitor_task WHERE conversion_event NOT IN (
 'ACTIVATE','EVOKE','LOGIN','REGISTER','PAYMENT','CUSTOMER_ACQUISITION','NEXT_STAY','VISITS',
 'AUTHENTICATION','ORDER','FINISH_THE_ORDER','FIRST_ACTIVATION','FIRST_ORDER','DOWNLOAD','ACTIVITY','ADDICTION')")"
[[ "$invalid_conversion_events" -eq 0 ]] || fail "monitor target conversion event is invalid"

invalid_billing_config="$(scalar "SELECT COUNT(*) FROM scx_ocpx_monitor_task
 WHERE billing_enabled NOT IN (0,1) OR billing_type NOT IN (1,2)
 OR unit_price<0 OR (billing_enabled=1 AND unit_price<=0)")"
[[ "$invalid_billing_config" -eq 0 ]] || fail "monitor billing configuration is invalid"

invalid_rta_tasks="$(scalar "SELECT COUNT(*) FROM scx_ocpx_monitor_task WHERE rta_enabled NOT IN (0,1)")"
[[ "$invalid_rta_tasks" -eq 0 ]] || fail "monitor RTA switch is invalid"

invalid_rta_bindings="$(scalar "SELECT COUNT(*) FROM scx_ocpx_rta_binding b
 LEFT JOIN scx_ocpx_monitor_task t ON t.id=b.monitor_task_id
 LEFT JOIN scx_ocpx_rta_upstream u ON u.id=b.upstream_id
 WHERE t.id IS NULL OR u.id IS NULL OR u.advertiser_id<>t.advertiser_id")"
[[ "$invalid_rta_bindings" -eq 0 ]] || fail "RTA binding ownership invalid"
invalid_rta_media="$(scalar "SELECT COUNT(*) FROM scx_ocpx_rta_media_access r LEFT JOIN scx_ocpx_media m ON m.id=r.media_id WHERE m.id IS NULL")"
[[ "$invalid_rta_media" -eq 0 ]] || fail "RTA media configuration references missing media"
rta_menu_count="$(scalar "SELECT COUNT(*) FROM sys_menu WHERE parent_id='menu_ocpx_rta' AND type='0' AND del_flag='0'")"
[[ "$rta_menu_count" -eq 4 ]] || fail "RTA management requires overview, records, logs and reports pages"
rta_overview_count="$(scalar "SELECT COUNT(*) FROM sys_menu WHERE id='menu_ocpx_rta_overview' AND parent_id='menu_ocpx_rta' AND permission='ocpx:rta:bindings:view' AND component='ocpx/rta/overview/index' AND del_flag='0'")"
[[ "$rta_overview_count" -eq 1 ]] || fail "RTA overview menu missing"
rta_config_permission_count="$(scalar "SELECT COUNT(*) FROM sys_menu WHERE type='1' AND del_flag='0' AND
 ((id='menu_ocpx_rta_upstreams' AND parent_id='menu_ocpx_advertiser') OR
  (id='menu_ocpx_rta_media' AND parent_id='menu_ocpx_media') OR
  (id='menu_ocpx_rta_bindings' AND parent_id='menu_ocpx_monitor_task'))")"
[[ "$rta_config_permission_count" -eq 3 ]] || fail "RTA configuration permissions must belong to their business objects"


deduction_ratio_contract="$(scalar "SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_deduction_rule'
 AND column_name='ratio' AND data_type='decimal' AND numeric_precision=9 AND numeric_scale=6")"
[[ "$deduction_ratio_contract" -eq 1 ]] || fail "media callback ratio must be DECIMAL(9,6)"

invalid_deduction_ratios="$(scalar "SELECT COUNT(*) FROM scx_ocpx_deduction_rule WHERE ratio<0 OR ratio>1")"
[[ "$invalid_deduction_ratios" -eq 0 ]] || fail "media callback ratio must be between 0 and 1"

invalid_deduction_facts="$(scalar "SELECT COUNT(*) FROM scx_ocpx_event_fact
 WHERE deduction_decision NOT IN ('PENDING','PASSED','DEDUCTED')
 OR deduction_ratio_snapshot<0 OR deduction_ratio_snapshot>1
 OR (deduction_decision IS NULL AND (deduction_ratio_snapshot IS NOT NULL OR deduction_decided_at IS NOT NULL))
 OR (deduction_decision='PENDING' AND (deduction_ratio_snapshot IS NOT NULL OR deduction_decided_at IS NOT NULL))
 OR (deduction_decision IN ('PASSED','DEDUCTED')
     AND (deduction_ratio_snapshot IS NULL OR deduction_decided_at IS NULL))")"
[[ "$invalid_deduction_facts" -eq 0 ]] || fail "media deduction facts contain an invalid decision or ratio"

invalid_deduction_aggregates="$(scalar "SELECT COUNT(*) FROM scx_ocpx_hourly_metric
 WHERE callback_count<0 OR deduction_passed_count<0 OR deduction_deducted_count<0
 OR deduction_passed_count+deduction_deducted_count>callback_count")"
[[ "$invalid_deduction_aggregates" -eq 0 ]] || fail "media deduction hourly counts violate the fact invariant"

invalid_creatives="$(scalar "SELECT COUNT(*) FROM scx_ocpx_creative
 WHERE creative_type<>1 OR status NOT IN (0,1) OR file_size<=0 OR width<=0 OR height<=0
 OR width>8192 OR height>8192 OR CAST(width AS UNSIGNED)*CAST(height AS UNSIGNED)>40000000
 OR checksum_sha256 NOT REGEXP '^[0-9a-f]{64}$'")"
[[ "$invalid_creatives" -eq 0 ]] || fail "creative image metadata is invalid"

invalid_creative_owners="$(scalar "SELECT COUNT(*) FROM scx_ocpx_creative_promotion_link cp
 JOIN scx_ocpx_creative c ON c.id=cp.creative_id
 JOIN scx_ocpx_promotion_link l ON l.id=cp.promotion_link_id
 WHERE BINARY cp.advertiser_id<>BINARY c.advertiser_id
 OR BINARY cp.advertiser_id<>BINARY l.advertiser_id")"
[[ "$invalid_creative_owners" -eq 0 ]] || fail "creative binding crosses advertiser ownership"

account_rows="$(scalar "SELECT COUNT(*) FROM scx_ocpx_advertiser a LEFT JOIN scx_ocpx_advertiser_account ac ON ac.advertiser_id=a.id WHERE ac.advertiser_id IS NULL")"
[[ "$account_rows" -eq 0 ]] || fail "every advertiser must have an authoritative account"

invalid_account_rows="$(scalar "SELECT COUNT(*) FROM scx_ocpx_advertiser_account WHERE currency<>'CNY' OR available_balance<0")"
[[ "$invalid_account_rows" -eq 0 ]] || fail "advertiser account currency or balance is invalid"

truth_unique_indexes="$(scalar "SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND non_unique=0 AND
 ((table_name='scx_ocpx_event_fact' AND index_name='uk_ocpx_event_fact_key')
  OR (table_name='scx_ocpx_event_fact' AND index_name='uk_ocpx_event_fact_identity')
  OR (table_name='scx_ocpx_billing_entry' AND index_name='uk_ocpx_billing_business_key')
  OR (table_name='scx_ocpx_hourly_metric' AND index_name='uk_ocpx_hourly_metric_scope'))")"
[[ "$truth_unique_indexes" -eq 4 ]] || fail "truthful fact, billing or hourly idempotency index is missing"

deduction_fact_index="$(scalar "SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event_fact'
 AND index_name='idx_ocpx_event_fact_deduction_time'")"
[[ "$deduction_fact_index" -eq 5 ]] || fail "media deduction coverage index is missing"

callback_record_index="$(scalar "SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event_fact'
 AND index_name='idx_ocpx_event_fact_callback_page'
 AND ((seq_in_index=1 AND column_name='flow_type')
   OR (seq_in_index=2 AND column_name='received_at')
   OR (seq_in_index=3 AND column_name='id'))")"
[[ "$callback_record_index" -eq 3 ]] || fail "Callback-record ordered index is missing or invalid"

invalid_platforms="$(scalar "SELECT COUNT(*) FROM scx_ocpx_promotion_link WHERE platform NOT IN (1,2,3,4)")"
[[ "$invalid_platforms" -eq 0 ]] || fail "promotion-link platform must be one of 1 Android, 2 iOS, 3 Android+iOS, 4 quick app"

deduction_unique_parts="$(scalar "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_deduction_rule' AND index_name='uk_ocpx_deduction_rule_route'")"
deduction_function_part="$(scalar "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_deduction_rule' AND index_name='uk_ocpx_deduction_rule_route' AND expression IS NOT NULL")"
[[ "$deduction_unique_parts" -eq 3 && "$deduction_function_part" -eq 1 ]] || fail "deduction route functional unique index is missing"

route_table_list="$(printf "'%s'," "${SCX_OCPX_ROUTE_TABLES[@]}")"; route_table_list="${route_table_list%,}"
route_column_list="$(printf "'%s'," "${SCX_OCPX_ROUTE_COLUMNS[@]}")"; route_column_list="${route_column_list%,}"
retired_route_column_list="$(printf "'%s'," "${SCX_OCPX_RETIRED_ROUTE_COLUMNS[@]}")"; retired_route_column_list="${retired_route_column_list%,}"
route_column_count="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN (${route_table_list}) AND column_name IN (${route_column_list})")"
expected_route_column_count=$(( ${#SCX_OCPX_ROUTE_TABLES[@]} * ${#SCX_OCPX_ROUTE_COLUMNS[@]} ))
[[ "$route_column_count" -eq "$expected_route_column_count" ]] || fail "OCPX route ID columns m_id/t_id/a_id/l_id are incomplete"
retired_route_column_count="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN (${route_table_list}) AND column_name IN (${retired_route_column_list})")"
[[ "$retired_route_column_count" -eq 0 ]] || fail "retired OCPX route ID columns remain"
route_comment_count="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN (${route_table_list}) AND column_name IN (${route_column_list}) AND column_comment<>''")"
[[ "$route_comment_count" -eq "$expected_route_column_count" ]] || fail "OCPX route ID column comments are incomplete"

aerim_callback_columns="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='scx_ocpx_log_callback'
 AND column_name IN ('partner_action_type','standard_action_type','action_time','currency_amount')")"
[[ "$aerim_callback_columns" -eq 4 ]] || fail "AERIM callback fidelity columns are incomplete"

aerim_report_columns="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN ('scx_ocpx_report_mat_temp','scx_ocpx_report_mat')
 AND column_name IN ('first_activation','first_order','currency_amount')")"
[[ "$aerim_report_columns" -eq 6 ]] || fail "AERIM report columns are incomplete"

phoenix_report_columns="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN ('scx_ocpx_report_mat_temp','scx_ocpx_report_mat')
 AND column_name IN ('download','activity','addiction')")"
[[ "$phoenix_report_columns" -eq 6 ]] || fail "PHOENIX report columns are incomplete"

feedback_detail_columns="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN ('scx_ocpx_log_feedback','scx_ocpx_log_feedback_day')
 AND column_name IN ('device_androidid_md5','device_idfa_md5','aaid','device_caid2','device_caid_list',
 'last_caid','prev_caid','device_imei_md5','device_oaid_md5','ipv6','model','osv')")"
[[ "$feedback_detail_columns" -eq 24 ]] || fail "normalized Feedback request detail columns are incomplete"

flow_response_preview_columns="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='scx_ocpx_flow_log' AND column_name='response_preview'
 AND data_type='varchar' AND character_maximum_length=4096 AND is_nullable='YES'")"
[[ "$flow_response_preview_columns" -eq 1 ]] || fail "bounded flow-log response preview column is missing or invalid"

flow_log_page_index="$(scalar "SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_flow_log'
 AND index_name='idx_ocpx_flow_log_page'
 AND ((seq_in_index=1 AND column_name='event_time') OR (seq_in_index=2 AND column_name='id'))")"
[[ "$flow_log_page_index" -eq 2 ]] || fail "flow-log event-time pagination index is missing or invalid"

flow_preview_present_column="$(scalar "SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_flow_log'
 AND column_name='response_preview_present' AND data_type='tinyint'
 AND extra LIKE '%GENERATED%' AND generation_expression LIKE '%response_preview%'")"
[[ "$flow_preview_present_column" -eq 1 ]] || fail "flow-log response preview presence column is missing or invalid"

flow_preview_retention_index="$(scalar "SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_flow_log'
 AND index_name='idx_ocpx_flow_log_preview_retention'
 AND ((seq_in_index=1 AND column_name='response_preview_present') OR (seq_in_index=2 AND column_name='event_time') OR (seq_in_index=3 AND column_name='id'))")"
[[ "$flow_preview_retention_index" -eq 3 ]] || fail "flow-log response preview retention index is missing or invalid"

feedback_retention_index="$(scalar "SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_log_feedback'
 AND index_name='idx_ocpx_feedback_create_time' AND seq_in_index=1 AND column_name='create_time'")"
[[ "$feedback_retention_index" -eq 1 ]] || fail "feedback retention index is missing or invalid"

callback_retention_index="$(scalar "SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_log_callback'
 AND index_name='idx_ocpx_callback_create_time' AND seq_in_index=1 AND column_name='create_time'")"
[[ "$callback_retention_index" -eq 1 ]] || fail "callback retention index is missing or invalid"

feedback_day_retention_index="$(scalar "SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_log_feedback_day'
 AND index_name='idx_ocpx_feedback_day_time_hour_res' AND seq_in_index=1 AND column_name='time_hour_res'")"
[[ "$feedback_day_retention_index" -eq 1 ]] || fail "feedback-day retention index is missing or invalid"

debug_tables="$(scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'scx_api_debug\\_%'")"
[[ "$debug_tables" -eq 0 ]] || fail "retired API debugger tables remain"

secret_columns="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN ('scx_ocpx_advertiser','scx_ocpx_promotion_link','scx_ocpx_creative','scx_ocpx_media','scx_ocpx_monitor_task','scx_ocpx_deduction_rule','scx_ocpx_flow_log','scx_ocpx_log_feedback','scx_ocpx_log_feedback_day','scx_ocpx_log_callback')
 AND column_name REGEXP '(^|_)(account|password|secret)(_|$)'
 AND NOT (table_name='scx_ocpx_advertiser' AND column_name='account_user_id')")"
[[ "$secret_columns" -eq 0 ]] || fail "OCPX tables expose account/password/secret columns"

invalid_advertiser_accounts="$(scalar "SELECT COUNT(*)
 FROM scx_ocpx_advertiser a
 LEFT JOIN sys_user u ON u.id=a.account_user_id
 WHERE a.account_user_id IS NOT NULL
 AND (u.id IS NULL OR u.type<>'2' OR u.status<>'0' OR u.del_flag<>'0'
      OR u.shop_id IS NULL OR BINARY u.shop_id<>BINARY a.id)")"
[[ "$invalid_advertiser_accounts" -eq 0 ]] || fail "advertiser account bindings must reference active type-2 users scoped to the advertiser"

invalid_link_owner="$(scalar "SELECT COUNT(*) FROM scx_ocpx_monitor_task t JOIN scx_ocpx_promotion_link l ON l.id=t.promotion_link_id WHERE BINARY l.advertiser_id<>BINARY t.advertiser_id")"
[[ "$invalid_link_owner" -eq 0 ]] || fail "monitor task references a promotion link owned by another advertiser"

composite_fk="$(scalar "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_monitor_task' AND constraint_name='fk_ocpx_monitor_task_link_owner'")"
[[ "$composite_fk" -eq 1 ]] || fail "advertiser/promotion-link composite foreign key is missing"

retired_permissions="$(scalar "SELECT COUNT(*) FROM sys_menu WHERE del_flag='0' AND
 (permission LIKE CONCAT('ad','x:%') OR permission REGEXP '^ocpx:(channel|product|app-link|media-account|task|monitor|deduction|event|runtime|api-debug|scope|account):'
  OR permission IN (
    'ocpx:monitor-task:publish','ocpx:advertiser:grant',
    'ocpx:advertiser:status','ocpx:advertiser:archive',
    'ocpx:promotion-link:status','ocpx:promotion-link:archive','ocpx:promotion-link:validate',
    'ocpx:media:status','ocpx:media:archive','ocpx:media:preflight','ocpx:media:connectivity','ocpx:media:export',
    'ocpx:monitor-task:status','ocpx:monitor-task:archive'
  ))")"
[[ "$retired_permissions" -eq 0 ]] || fail "retired OCPX permissions remain active"

required_permissions="$(scalar "SELECT COUNT(DISTINCT permission) FROM sys_menu WHERE del_flag='0' AND permission IN ('ocpx:advertiser:recharge','ocpx:advertiser:deduct','ocpx:advertiser:delete','ocpx:promotion-link:delete','ocpx:creative:view','ocpx:creative:add','ocpx:creative:edit','ocpx:creative:delete','ocpx:creative:bind','ocpx:billing-entry:view','ocpx:media:delete','ocpx:monitor-task:delete','ocpx:monitor-task:billing','ocpx:monitor-task:test','ocpx:monitor-task:integration','ocpx:report:export','ocpx:callback-record:view','ocpx:flow-log:detail','ocpx:advertiser:role:manage','ocpx:advertiser:scope:all','ocpx:protocol-config:manage')")"
[[ "$required_permissions" -eq 21 ]] || fail "required OCPX action, creative, billing, ledger, callback-record, flow-detail, protocol-config, monitor-test, integration and data-scope permissions are incomplete"

advertiser_role="$(scalar "SELECT COUNT(*) FROM sys_role
 WHERE BINARY id=BINARY 'role_advertiser'
 AND BINARY role_code=BINARY 'ROLE_ADVERTISER'
 AND type='0' AND shop_id IS NULL AND del_flag='0'")"
[[ "$advertiser_role" -eq 1 ]] || fail "the protected shared ROLE_ADVERTISER role is missing or invalid"

invalid_advertiser_role_menus="$(scalar "SELECT COUNT(*)
 FROM sys_role_menu rm
 LEFT JOIN sys_menu m ON m.id=rm.menu_id AND m.del_flag='0'
 WHERE rm.role_id='role_advertiser'
 AND (m.id IS NULL
      OR NOT (m.permission LIKE 'ocpx:%'
              OR (COALESCE(m.permission,'')='' AND m.path LIKE '/ocpx%'))
      OR m.permission IN ('ocpx:advertiser:role:manage','ocpx:advertiser:recharge','ocpx:advertiser:deduct','ocpx:monitor-task:billing','ocpx:monitor-task:test','ocpx:monitor-task:integration','ocpx:flow-log:detail'))")"
[[ "$invalid_advertiser_role_menus" -eq 0 ]] || fail "ROLE_ADVERTISER contains a non-OCPX or protected role-management permission"

admin_protected_permissions="$(scalar "SELECT COUNT(DISTINCT m.permission)
 FROM sys_role r
 JOIN sys_role_menu rm ON rm.role_id=r.id
 JOIN sys_menu m ON m.id=rm.menu_id AND m.del_flag='0'
 WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0'
 AND m.permission IN ('ocpx:advertiser:recharge','ocpx:advertiser:deduct','ocpx:monitor-task:billing','ocpx:monitor-task:test','ocpx:monitor-task:integration','ocpx:flow-log:detail','ocpx:protocol-config:manage')")"
[[ "$admin_protected_permissions" -eq 7 ]] || fail "system administrator finance, flow-detail, protocol-config, monitor-test and integration permissions are incomplete"

admin_callback_record="$(scalar "SELECT COUNT(DISTINCT m.permission)
 FROM sys_role r
 JOIN sys_role_menu rm ON rm.role_id=r.id
 JOIN sys_menu m ON m.id=rm.menu_id AND m.del_flag='0'
 WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0'
 AND m.permission='ocpx:callback-record:view'")"
[[ "$admin_callback_record" -eq 1 ]] || fail "system administrator upstream callback record permission is missing"

non_admin_protocol_config_grants="$(scalar "SELECT COUNT(*)
 FROM sys_role_menu rm
 JOIN sys_menu m ON m.id=rm.menu_id AND m.del_flag='0'
 LEFT JOIN sys_role r ON r.id=rm.role_id
 WHERE m.permission='ocpx:protocol-config:manage'
 AND (r.id IS NULL OR r.role_code<>'ROLE_ADMIN' OR r.del_flag<>'0')")"
[[ "$non_admin_protocol_config_grants" -eq 0 ]] || fail "protocol configuration must be granted only to active ROLE_ADMIN"

non_admin_monitor_test_grants="$(scalar "SELECT COUNT(*)
 FROM sys_role_menu rm
 JOIN sys_menu m ON m.id=rm.menu_id AND m.del_flag='0'
 LEFT JOIN sys_role r ON r.id=rm.role_id
 WHERE m.permission='ocpx:monitor-task:test'
 AND (r.id IS NULL OR r.role_code<>'ROLE_ADMIN' OR r.del_flag<>'0')")"
[[ "$non_admin_monitor_test_grants" -eq 0 ]] || fail "monitor-task click testing must be granted only to active ROLE_ADMIN"

non_admin_monitor_integration_grants="$(scalar "SELECT COUNT(*)
 FROM sys_role_menu rm
 JOIN sys_menu m ON m.id=rm.menu_id AND m.del_flag='0'
 LEFT JOIN sys_role r ON r.id=rm.role_id
 WHERE m.permission='ocpx:monitor-task:integration'
 AND (r.id IS NULL OR r.role_code<>'ROLE_ADMIN' OR r.del_flag<>'0')")"
[[ "$non_admin_monitor_integration_grants" -eq 0 ]] || fail "monitor-task integration must be granted only to active ROLE_ADMIN"

non_admin_flow_detail_grants="$(scalar "SELECT COUNT(*)
 FROM sys_role_menu rm
 JOIN sys_menu m ON m.id=rm.menu_id AND m.del_flag='0'
 LEFT JOIN sys_role r ON r.id=rm.role_id
 WHERE m.permission='ocpx:flow-log:detail'
 AND (r.id IS NULL OR r.role_code<>'ROLE_ADMIN' OR r.del_flag<>'0')")"
[[ "$non_admin_flow_detail_grants" -eq 0 ]] || fail "flow-log request details must be granted only to active ROLE_ADMIN"

advertiser_scope_dependency="$(scalar "SELECT COUNT(*)
 FROM sys_role_menu rm
 JOIN sys_menu m ON m.id=rm.menu_id AND m.del_flag='0'
 WHERE rm.role_id='role_advertiser'
 AND m.permission IN (
   'ocpx:advertiser:add','ocpx:advertiser:delete',
   'ocpx:media:view','ocpx:media:add','ocpx:media:edit','ocpx:media:delete',
   'ocpx:deduction-rule:list','ocpx:deduction-rule:create',
   'ocpx:deduction-rule:update','ocpx:deduction-rule:delete'
 )
 AND NOT EXISTS (
   SELECT 1 FROM sys_role_menu scope_rm
   JOIN sys_menu scope_m ON scope_m.id=scope_rm.menu_id AND scope_m.del_flag='0'
   WHERE scope_rm.role_id='role_advertiser'
   AND scope_m.permission='ocpx:advertiser:scope:all'
 )")"
[[ "$advertiser_scope_dependency" -eq 0 ]] || fail "global OCPX permissions require the advertiser all-data scope grant"

admin_report_export="$(scalar "SELECT COUNT(*)
 FROM sys_role r
 JOIN sys_role_menu rm ON rm.role_id=r.id
 JOIN sys_menu m ON m.id=rm.menu_id
 WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0'
 AND m.permission='ocpx:report:export' AND m.del_flag='0'")"
[[ "$admin_report_export" -eq 1 ]] || fail "system administrator report-export grant is missing"

admin_advertiser_role_manage="$(scalar "SELECT COUNT(*)
 FROM sys_role r
 JOIN sys_role_menu rm ON rm.role_id=r.id
 JOIN sys_menu m ON m.id=rm.menu_id
 WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0'
 AND m.permission='ocpx:advertiser:role:manage' AND m.del_flag='0'")"
[[ "$admin_advertiser_role_manage" -eq 1 ]] || fail "system administrator advertiser-role management grant is missing"

admin_ocpx_crud="$(scalar "SELECT COUNT(DISTINCT m.permission)
 FROM sys_role r
 JOIN sys_role_menu rm ON rm.role_id=r.id
 JOIN sys_menu m ON m.id=rm.menu_id
 WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0'
 AND m.permission IN (
   'ocpx:advertiser:view','ocpx:advertiser:add','ocpx:advertiser:edit','ocpx:advertiser:delete',
   'ocpx:promotion-link:view','ocpx:promotion-link:add','ocpx:promotion-link:edit','ocpx:promotion-link:delete',
   'ocpx:creative:view','ocpx:creative:add','ocpx:creative:edit','ocpx:creative:delete','ocpx:creative:bind',
   'ocpx:media:view','ocpx:media:add','ocpx:media:edit','ocpx:media:delete',
   'ocpx:monitor-task:view','ocpx:monitor-task:add','ocpx:monitor-task:edit','ocpx:monitor-task:delete',
   'ocpx:deduction-rule:list','ocpx:deduction-rule:create','ocpx:deduction-rule:update','ocpx:deduction-rule:delete'
 )")"
[[ "$admin_ocpx_crud" -eq 25 ]] || fail "ROLE_ADMIN must have complete management permissions for all OCPX resources"

system_admin="$(scalar "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='system' AND u.del_flag='0' AND r.role_code='ROLE_ADMIN' AND r.del_flag='0'")"
[[ "$system_admin" -eq 1 ]] || fail "system administrator role mapping changed"

dangling_role_menu="$(scalar "SELECT COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_menu m ON m.id=rm.menu_id WHERE m.id IS NULL")"
[[ "$dangling_role_menu" -eq 0 ]] || fail "dangling role-menu assignments found"

echo "Database verification passed: OCPX management, truthful fact, account, ledger, hourly metric and system tables are valid."

assert_exact_columns scx_ocpx_rta_upstream 'id,advertiser_id,name,protocol,protocol_config,config_schema_version,access_code,endpoint,credential_ref,timeout_ms,admission_seconds,enabled,version,update_time'

assert_exact_columns scx_ocpx_rta_media_access 'media_id,notify_url,credential_ref,enabled,version,update_time,protocol,protocol_config'

assert_exact_columns scx_ocpx_rta_binding 'monitor_task_id,upstream_id,product_id,external_task_id,enabled,version,update_time,strategy_id,oppo_pkg'

assert_exact_columns scx_ocpx_rta_log 'id,relay_request_id,request_id,media_id,advertiser_id,upstream_id,task_id,stage,status,reason,duration_ms,event_time,update_time'

assert_exact_columns scx_ocpx_rta_hour_stat 'time_hour,media_id,advertiser_id,task_id,upstream_id,request_count,allow_count,deny_count,error_count,timeout_count,delivery_success_count,delivery_failed_count,version,update_time'
