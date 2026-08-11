#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/schema-required-tables.sh"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-smart_converge_x}"

mysql_args=(
  --default-character-set=utf8mb4
  --host="$MYSQL_HOST"
  --port="$MYSQL_PORT"
  --user="$MYSQL_USERNAME"
)

if [[ -n "${MYSQL_PASSWORD:-}" ]]; then
  export MYSQL_PWD="$MYSQL_PASSWORD"
fi

table_list="$(printf "'%s'," "${SCX_REQUIRED_TABLES[@]}")"
table_list="${table_list%,}"
xxl_table_list="$(printf "'%s'," "${SCX_XXL_JOB_TABLES[@]}")"
xxl_table_list="${xxl_table_list%,}"
allowed_table_list="$(printf "'%s'," "${SCX_ALLOWED_TABLES[@]}")"
allowed_table_list="${allowed_table_list%,}"

actual_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name IN (${table_list});")"

if [[ "$actual_count" -ne "${#SCX_REQUIRED_TABLES[@]}" ]]; then
  echo "Database verification failed: expected ${#SCX_REQUIRED_TABLES[@]} required tables, found $actual_count." >&2
  exit 1
fi

xxl_actual_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name IN (${xxl_table_list});")"

if [[ "$xxl_actual_count" -ne "${#SCX_XXL_JOB_TABLES[@]}" ]]; then
  echo "Database verification failed: expected ${#SCX_XXL_JOB_TABLES[@]} XXL-JOB tables, found $xxl_actual_count." >&2
  exit 1
fi

unexpected_tables="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COALESCE(GROUP_CONCAT(table_name ORDER BY table_name SEPARATOR ','),'') FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_type='BASE TABLE' AND table_name NOT IN (${allowed_table_list});")"

if [[ -n "$unexpected_tables" ]]; then
  echo "Database verification failed: unexpected tables: $unexpected_tables" >&2
  exit 1
fi

admin_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='system' AND r.role_code='ROLE_ADMIN' AND u.del_flag='0';")"

if [[ "$admin_count" -ne 1 ]]; then
  echo "Database verification failed: default administrator role mapping is missing." >&2
  exit 1
fi

retired_namespace="$(printf '%s%s' 'a' 'dx')"
legacy_namespace_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name LIKE 'scx_${retired_namespace}_%') + (SELECT COUNT(*) FROM sys_menu WHERE id LIKE 'menu_${retired_namespace}_%' OR id LIKE 'perm_${retired_namespace}_%' OR parent_id LIKE 'menu_${retired_namespace}_%' OR parent_id LIKE 'perm_${retired_namespace}_%' OR path LIKE '/${retired_namespace}%' OR component LIKE '${retired_namespace}/%' OR permission LIKE '${retired_namespace}:%');")"

if [[ "$legacy_namespace_count" -ne 0 ]]; then
  echo "Database verification failed: retired table or menu namespace remains." >&2
  exit 1
fi

dangling_ocpx_role_menu_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_menu m ON m.id=rm.menu_id WHERE (rm.menu_id LIKE 'menu_ocpx_%' OR rm.menu_id LIKE 'perm_ocpx_%') AND m.id IS NULL;")"

if [[ "$dangling_ocpx_role_menu_count" -ne 0 ]]; then
  echo "Database verification failed: dangling OCPX role-menu assignments found." >&2
  exit 1
fi

admin_ocpx_menu_gap_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM sys_role r JOIN sys_menu m ON m.del_flag='0' AND (m.id LIKE 'menu_ocpx_%' OR m.id LIKE 'perm_ocpx_%' OR m.path LIKE '/ocpx%' OR m.permission LIKE 'ocpx:%') LEFT JOIN sys_role_menu rm ON rm.role_id=r.id AND rm.menu_id=m.id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND rm.id IS NULL;")"

if [[ "$admin_ocpx_menu_gap_count" -ne 0 ]]; then
  echo "Database verification failed: administrator OCPX menu grants are incomplete." >&2
  exit 1
fi

system_menu_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM sys_menu WHERE del_flag='0' AND ((id='menu_ocpx_system' AND name='系统管理') OR (parent_id='menu_ocpx_system' AND id IN ('menu_system_user','menu_role','menu_menu','menu_dept','menu_dict','menu_storage','menu_log','menu_login_log','menu_ocpx_event')));")"

if [[ "$system_menu_count" -ne 10 ]]; then
  echo "Database verification failed: original system-management menu tree is incomplete." >&2
  exit 1
fi

admin_system_menu_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.id) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.id IN ('menu_ocpx_system','menu_system_user','menu_role','menu_menu','menu_dept','menu_dict','menu_storage','menu_log','menu_login_log','menu_ocpx_event');")"

if [[ "$admin_system_menu_count" -ne 10 ]]; then
  echo "Database verification failed: administrator system-management menu grants are incomplete." >&2
  exit 1
fi

admin_system_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('system:sysuser:page','system:sysrole:page','system:sysmenu:page','system:sysdept:page','system:sysdict:page','system:storageconfig:page','system:syslog:page','system:sysloginlog:page','ocpx:event:view');")"

if [[ "$admin_system_permission_count" -ne 9 ]]; then
  echo "Database verification failed: administrator system-management RBAC permissions are incomplete." >&2
  exit 1
fi

ocpx_scope_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND ((table_name='scx_ocpx_user_scope' AND column_name IN ('user_id','scope_type','scope_id','permission_level','valid_from','valid_to','status','version','deleted','active_scope_key')) OR (table_name='scx_ocpx_user_scope_revision' AND column_name IN ('user_id','version','update_by','update_time')));")"

if [[ "$ocpx_scope_column_count" -ne 14 ]]; then
  echo "Database verification failed: OCPX object-scope schema is incomplete." >&2
  exit 1
fi

admin_scope_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('ocpx:scope:view','ocpx:scope:edit');")"

if [[ "$admin_scope_permission_count" -ne 2 ]]; then
  echo "Database verification failed: administrator OCPX object-scope permissions are incomplete." >&2
  exit 1
fi

active_scope_administrator_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT s.user_id) FROM scx_ocpx_user_scope s JOIN sys_user u ON u.id=s.user_id WHERE u.del_flag='0' AND s.deleted=0 AND s.status='ACTIVE' AND s.scope_type='ALL' AND s.scope_id IS NULL AND s.permission_level='MANAGE' AND (s.valid_from IS NULL OR s.valid_from<=NOW(3)) AND (s.valid_to IS NULL OR s.valid_to>NOW(3));")"

if [[ "$active_scope_administrator_count" -lt 1 ]]; then
  echo "Database verification failed: at least one active OCPX scope administrator is required." >&2
  exit 1
fi

invalid_scope_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_user_scope WHERE deleted=0 AND (scope_type NOT IN ('ALL','ADVERTISER','CHANNEL','MEDIA_ACCOUNT') OR permission_level NOT IN ('VIEW','OPERATE','MANAGE') OR (scope_type='ALL' AND scope_id IS NOT NULL) OR (scope_type<>'ALL' AND scope_id IS NULL) OR (valid_from IS NOT NULL AND valid_to IS NOT NULL AND valid_from>=valid_to));")"

if [[ "$invalid_scope_count" -ne 0 ]]; then
  echo "Database verification failed: invalid OCPX object-scope rows found." >&2
  exit 1
fi

advertiser_onboarding_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND ((table_name='scx_ocpx_advertiser' AND column_name IN ('advertiser_code','adapter_version','credential_ref','credential_revision','current_document_id','document_revision','create_request_key','create_request_hash')) OR (table_name='scx_ocpx_monitor_link' AND column_name IN ('republish_required','republish_reason','published_advertiser_credential_revision','published_upstream_adapter_version','published_document_id','published_document_revision')) OR (table_name='scx_ocpx_adapter' AND column_name='capability_revision') OR (table_name='scx_ocpx_integration_document' AND column_name IN ('owner_type','owner_id','document_type','storage_config_id','storage_provider','object_key','document_version','scan_status','status')) OR (table_name='scx_ocpx_advertiser_credential_revision' AND column_name IN ('advertiser_id','credential_ref','credential_revision','secret_ciphertext','storage_mode','status','overlap_until')));"
)"

if [[ "$advertiser_onboarding_column_count" -ne 31 ]]; then
  echo "Database verification failed: Advertiser onboarding schema is incomplete." >&2
  exit 1
fi

advertiser_onboarding_index_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND CONCAT(table_name,':',index_name) IN ('scx_ocpx_advertiser:uk_scx_ocpx_advertiser_code','scx_ocpx_advertiser:uk_scx_ocpx_advertiser_create_request','scx_ocpx_integration_document:uk_scx_ocpx_document_version','scx_ocpx_advertiser_credential_revision:uk_scx_ocpx_advertiser_credential_revision','scx_ocpx_advertiser_credential_revision:uk_scx_ocpx_advertiser_credential_ref','scx_ocpx_advertiser_change_audit:idx_scx_ocpx_advertiser_audit');"
)"

if [[ "$advertiser_onboarding_index_count" -ne 6 ]]; then
  echo "Database verification failed: Advertiser onboarding indexes are incomplete." >&2
  exit 1
fi

admin_advertiser_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('ocpx:advertiser:view','ocpx:advertiser:add','ocpx:advertiser:edit','ocpx:advertiser:status','ocpx:advertiser:credential','ocpx:advertiser:document:view','ocpx:advertiser:document:edit');"
)"

if [[ "$admin_advertiser_permission_count" -ne 7 ]]; then
  echo "Database verification failed: administrator Advertiser onboarding permissions are incomplete." >&2
  exit 1
fi

invalid_credential_revision_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_advertiser_credential_revision WHERE credential_revision<1 OR storage_mode NOT IN ('LEGACY','ENCRYPTED') OR status NOT IN ('ACTIVE','OVERLAP','RETIRED') OR (storage_mode='LEGACY' AND secret_ciphertext IS NOT NULL) OR (storage_mode='ENCRYPTED' AND (secret_ciphertext IS NULL OR secret_ciphertext=''));"
)"

if [[ "$invalid_credential_revision_count" -ne 0 ]]; then
  echo "Database verification failed: invalid Advertiser credential-revision metadata found." >&2
  exit 1
fi

product_master_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND ((table_name='scx_ocpx_product' AND column_name IN ('advertiser_id','code','name','normalized_name','package_name','logo_object_id','description','status','config_version','event_policy_revision','create_request_key','create_request_hash','archived_by','archived_at','create_by','update_by')) OR (table_name='scx_ocpx_product_logo_object' AND column_name IN ('storage_config_id','storage_provider','object_key','file_name','content_type','file_size','sha256','image_width','image_height','status')) OR (table_name='scx_ocpx_product_event_policy' AND column_name IN ('product_id','event_definition_id','daily_limit','status','version','update_by')) OR (table_name='scx_ocpx_product_change_audit' AND column_name IN ('product_id','advertiser_id','action','before_config_version','after_config_version','event_policy_revision','affected_route_count')) OR (table_name='scx_ocpx_app_link' AND column_name='product_id') OR (table_name='scx_ocpx_monitor_link' AND column_name IN ('published_product_id','published_product_version','published_product_event_policy_revision')));"
)"

if [[ "$product_master_column_count" -ne 43 ]]; then
  echo "Database verification failed: Product master-data schema is incomplete." >&2
  exit 1
fi

product_master_index_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND CONCAT(table_name,':',index_name) IN ('scx_ocpx_product:uk_scx_ocpx_product_code','scx_ocpx_product:uk_scx_ocpx_product_name','scx_ocpx_product:uk_scx_ocpx_product_create_request','scx_ocpx_product:idx_scx_ocpx_product_filter','scx_ocpx_product:idx_scx_ocpx_product_package','scx_ocpx_product:idx_scx_ocpx_product_logo','scx_ocpx_product_logo_object:idx_scx_ocpx_product_logo_status','scx_ocpx_product_logo_object:idx_scx_ocpx_product_logo_hash','scx_ocpx_product_event_policy:uk_scx_ocpx_product_event','scx_ocpx_product_event_policy:idx_scx_ocpx_product_event_definition','scx_ocpx_product_change_audit:idx_scx_ocpx_product_audit','scx_ocpx_product_change_audit:idx_scx_ocpx_product_audit_advertiser','scx_ocpx_app_link:idx_scx_ocpx_app_link_product');"
)"

if [[ "$product_master_index_count" -ne 13 ]]; then
  echo "Database verification failed: Product master-data indexes are incomplete." >&2
  exit 1
fi

admin_product_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('ocpx:product:view','ocpx:product:add','ocpx:product:edit','ocpx:product:archive');"
)"

if [[ "$admin_product_permission_count" -ne 4 ]]; then
  echo "Database verification failed: administrator Product permissions are incomplete." >&2
  exit 1
fi

invalid_product_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_product p LEFT JOIN scx_ocpx_advertiser a ON BINARY a.id=BINARY p.advertiser_id WHERE a.id IS NULL OR p.code='' OR p.name='' OR p.normalized_name='' OR p.status NOT IN ('ACTIVE','DISABLED','ARCHIVED') OR p.config_version<1 OR p.event_policy_revision<1 OR p.create_request_key='' OR CHAR_LENGTH(p.create_request_hash)<>64 OR (p.status='ARCHIVED' AND p.archived_at IS NULL) OR (p.status<>'ARCHIVED' AND p.archived_at IS NOT NULL);"
)"

invalid_product_policy_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_product_event_policy p LEFT JOIN scx_ocpx_product x ON BINARY x.id=BINARY p.product_id LEFT JOIN scx_ocpx_event_definition e ON BINARY e.id=BINARY p.event_definition_id WHERE x.id IS NULL OR e.id IS NULL OR e.channel_id<>'' OR (e.mapping_state='LEGACY_UNMAPPED' AND e.category<>'CONVERSION') OR (e.mapping_state='CANONICAL' AND (e.category<>'STANDARD' OR e.direction NOT IN ('DOWNSTREAM_DELIVERY','BIDIRECTIONAL'))) OR p.daily_limit<0 OR p.status NOT IN ('ACTIVE','DISABLED') OR p.version<1;"
)"

if [[ "$invalid_product_count" -ne 0 || "$invalid_product_policy_count" -ne 0 ]]; then
  echo "Database verification failed: invalid Product master-data rows found." >&2
  exit 1
fi

app_link_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND ((table_name='scx_ocpx_app_link' AND column_name IN ('biz_code','product_id','document_id','document_version','normalized_name','link_type','link_url_template','template_host','template_fingerprint','template_macro_set','redirect_enabled','deeplink_url','download_url','description','status','mapping_status','config_version','create_request_key','create_request_hash','archived_by','archived_at','create_by','update_by','current_name_marker')) OR (table_name='scx_ocpx_app_link_change_audit' AND column_name IN ('app_link_id','advertiser_id','product_id','action','before_config_version','after_config_version','mapping_status','template_host','template_fingerprint','affected_route_count','actor')) OR (table_name='scx_ocpx_monitor_link' AND column_name IN ('published_app_link_id','published_app_link_version','published_app_link_type','published_link_url_template','published_link_template_host','published_link_template_fingerprint','published_link_template_macro_set','published_redirect_enabled','published_deeplink_url','published_download_url')));"
)"

if [[ "$app_link_column_count" -ne 45 ]]; then
  echo "Database verification failed: structured AppLink schema is incomplete." >&2
  exit 1
fi

app_link_index_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND CONCAT(table_name,':',index_name) IN ('scx_ocpx_app_link:uk_scx_ocpx_app_link_name','scx_ocpx_app_link:uk_scx_ocpx_app_link_request','scx_ocpx_app_link:idx_scx_ocpx_app_link_product','scx_ocpx_app_link:idx_scx_ocpx_app_link_filter','scx_ocpx_app_link:idx_scx_ocpx_app_link_document','scx_ocpx_app_link_change_audit:idx_scx_ocpx_app_link_audit','scx_ocpx_app_link_change_audit:idx_scx_ocpx_app_link_audit_advertiser','scx_ocpx_monitor_link:idx_scx_ocpx_monitor_app_link_publish');"
)"

if [[ "$app_link_index_count" -ne 8 ]]; then
  echo "Database verification failed: structured AppLink indexes are incomplete." >&2
  exit 1
fi

admin_app_link_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('ocpx:app-link:view','ocpx:app-link:add','ocpx:app-link:edit','ocpx:app-link:status','ocpx:app-link:archive','ocpx:app-link:validate');"
)"

if [[ "$admin_app_link_permission_count" -ne 6 ]]; then
  echo "Database verification failed: administrator AppLink permissions are incomplete." >&2
  exit 1
fi

invalid_app_link_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_app_link l LEFT JOIN scx_ocpx_advertiser a ON BINARY a.id=BINARY l.advertiser_id LEFT JOIN scx_ocpx_product p ON BINARY p.id=BINARY l.product_id LEFT JOIN scx_ocpx_integration_document d ON BINARY d.id=BINARY l.document_id WHERE l.deleted=0 AND (a.id IS NULL OR l.mapping_status NOT IN ('LEGACY_PENDING_PRODUCT','READY') OR l.status NOT IN ('ACTIVE','DISABLED','ARCHIVED') OR (l.mapping_status<>'READY' AND l.status<>'DISABLED') OR (l.mapping_status='READY' AND (l.biz_code IS NULL OR l.biz_code='' OR p.id IS NULL OR BINARY p.advertiser_id<>BINARY l.advertiser_id OR d.id IS NULL OR d.owner_type<>'ADVERTISER' OR BINARY d.owner_id<>BINARY l.advertiser_id OR l.document_version<1 OR l.normalized_name IS NULL OR l.normalized_name='' OR l.link_type NOT IN ('ANDROID','IOS','OTHER') OR l.link_url_template IS NULL OR l.link_url_template='' OR l.template_host IS NULL OR l.template_host='' OR CHAR_LENGTH(l.template_fingerprint)<>64 OR l.template_macro_set IS NULL OR JSON_VALID(l.template_macro_set)=0 OR l.config_version<1)) OR (l.create_request_key IS NOT NULL AND (l.create_request_key='' OR CHAR_LENGTH(l.create_request_hash)<>64)) OR (l.status='ARCHIVED' AND l.archived_at IS NULL) OR (l.status<>'ARCHIVED' AND l.archived_at IS NOT NULL));"
)"

invalid_monitor_app_link_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_monitor_link m LEFT JOIN scx_ocpx_app_link l ON BINARY l.id=BINARY m.published_app_link_id WHERE m.deleted=0 AND ((m.published_app_link_id IS NULL AND (m.published_app_link_version<>0 OR m.published_app_link_type IS NOT NULL OR m.published_link_url_template IS NOT NULL OR m.published_link_template_host IS NOT NULL OR m.published_link_template_fingerprint IS NOT NULL OR m.published_link_template_macro_set IS NOT NULL OR m.published_redirect_enabled<>0 OR m.published_deeplink_url IS NOT NULL OR m.published_download_url IS NOT NULL)) OR (m.published_app_link_id IS NOT NULL AND (l.id IS NULL OR m.published_app_link_version<1 OR m.published_app_link_type NOT IN ('ANDROID','IOS','OTHER') OR m.published_link_url_template IS NULL OR m.published_link_url_template='' OR m.published_link_template_host IS NULL OR m.published_link_template_host='' OR CHAR_LENGTH(m.published_link_template_fingerprint)<>64 OR m.published_link_template_macro_set IS NULL OR JSON_VALID(m.published_link_template_macro_set)=0 OR m.published_product_id IS NULL OR BINARY m.published_product_id<>BINARY l.product_id)));"
)"

if [[ "$invalid_app_link_count" -ne 0 || "$invalid_monitor_app_link_count" -ne 0 ]]; then
  echo "Database verification failed: invalid AppLink mapping or published snapshot rows found." >&2
  exit 1
fi

media_account_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND ((table_name='scx_ocpx_media_account' AND column_name IN ('channel_id','code','name','normalized_name','document_id','document_version','description','default_credential_id','status','config_version','credential_revision','create_request_key','create_request_hash','archived_by','archived_at','create_by','update_by')) OR (table_name='scx_ocpx_media_credential' AND column_name IN ('media_account_id','login_type','login_identifier_ciphertext','login_identifier_hash','masked_identifier','current_credential_ref','current_secret_revision','description','status','version','last_preflight_status','last_preflight_at','create_by','update_by')) OR (table_name='scx_ocpx_media_credential_revision' AND column_name IN ('credential_id','credential_ref','secret_revision','secret_ciphertext','status','overlap_until','reason','created_by')) OR (table_name='scx_ocpx_media_account_owner' AND column_name IN ('media_account_id','user_id','responsibility','status','version','create_by','update_by')) OR (table_name='scx_ocpx_media_account_change_audit' AND column_name IN ('media_account_id','action','before_config_version','after_config_version','credential_revision','affected_route_count','actor')) OR (table_name='scx_ocpx_monitor_link' AND column_name IN ('media_account_id','media_credential_id','published_media_account_id','published_media_account_version','published_media_account_credential_revision','published_media_credential_id','published_media_credential_secret_revision')));"
)"

if [[ "$media_account_column_count" -ne 60 ]]; then
  echo "Database verification failed: MediaAccount/Credential schema is incomplete." >&2
  exit 1
fi

media_account_index_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND CONCAT(table_name,':',index_name) IN ('scx_ocpx_media_account:uk_scx_ocpx_media_account_code','scx_ocpx_media_account:uk_scx_ocpx_media_account_name','scx_ocpx_media_account:uk_scx_ocpx_media_account_request','scx_ocpx_media_account:idx_scx_ocpx_media_account_filter','scx_ocpx_media_account:idx_scx_ocpx_media_account_document','scx_ocpx_media_account:idx_scx_ocpx_media_account_default_credential','scx_ocpx_media_credential:uk_scx_ocpx_media_credential_identifier','scx_ocpx_media_credential:idx_scx_ocpx_media_credential_status','scx_ocpx_media_credential_revision:uk_scx_ocpx_media_credential_revision','scx_ocpx_media_credential_revision:uk_scx_ocpx_media_credential_ref','scx_ocpx_media_credential_revision:idx_scx_ocpx_media_credential_revision_status','scx_ocpx_media_account_owner:uk_scx_ocpx_media_account_owner','scx_ocpx_media_account_owner:idx_scx_ocpx_media_account_owner_user','scx_ocpx_media_account_change_audit:idx_scx_ocpx_media_account_audit','scx_ocpx_monitor_link:idx_scx_ocpx_monitor_media_account');"
)"

if [[ "$media_account_index_count" -ne 15 ]]; then
  echo "Database verification failed: MediaAccount/Credential indexes are incomplete." >&2
  exit 1
fi

admin_media_account_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('ocpx:media-account:view','ocpx:media-account:add','ocpx:media-account:edit','ocpx:media-account:status','ocpx:media-account:archive','ocpx:media-account:credential:view','ocpx:media-account:credential:add','ocpx:media-account:credential:edit','ocpx:media-account:credential:rotate','ocpx:media-account:credential:status','ocpx:media-account:owner:view','ocpx:media-account:owner:edit','ocpx:media-account:debug');"
)"

if [[ "$admin_media_account_permission_count" -ne 13 ]]; then
  echo "Database verification failed: administrator MediaAccount permissions are incomplete." >&2
  exit 1
fi

invalid_media_account_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_media_account a LEFT JOIN scx_ocpx_channel c ON BINARY c.id=BINARY a.channel_id LEFT JOIN scx_ocpx_media_credential d ON BINARY d.id=BINARY a.default_credential_id WHERE c.id IS NULL OR a.code='' OR a.name='' OR a.normalized_name='' OR a.status NOT IN ('ACTIVE','DISABLED','PAUSED','ARCHIVED') OR a.config_version<1 OR a.credential_revision<0 OR a.create_request_key='' OR CHAR_LENGTH(a.create_request_hash)<>64 OR (a.document_id IS NULL AND a.document_version<>0) OR (a.document_id IS NOT NULL AND a.document_version<1) OR (a.default_credential_id IS NOT NULL AND (d.id IS NULL OR BINARY d.media_account_id<>BINARY a.id)) OR (a.status='ARCHIVED' AND a.archived_at IS NULL) OR (a.status<>'ARCHIVED' AND a.archived_at IS NOT NULL);"
)"

invalid_media_credential_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_media_credential c LEFT JOIN scx_ocpx_media_account a ON BINARY a.id=BINARY c.media_account_id LEFT JOIN scx_ocpx_media_credential_revision r ON BINARY r.credential_id=BINARY c.id AND BINARY r.credential_ref=BINARY c.current_credential_ref AND r.secret_revision=c.current_secret_revision WHERE a.id IS NULL OR r.id IS NULL OR r.status<>'ACTIVE' OR c.login_type<>'EMAIL' OR c.login_identifier_ciphertext='' OR CHAR_LENGTH(c.login_identifier_hash)<>64 OR c.masked_identifier='' OR c.current_credential_ref='' OR c.current_secret_revision<1 OR c.status NOT IN ('PENDING','ACTIVE','DISABLED','REVOKED') OR c.version<1 OR (c.last_preflight_status IS NOT NULL AND c.last_preflight_status NOT IN ('READY','BLOCKED'));"
)"

invalid_media_revision_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_media_credential_revision r LEFT JOIN scx_ocpx_media_credential c ON BINARY c.id=BINARY r.credential_id WHERE c.id IS NULL OR r.credential_ref='' OR r.secret_revision<1 OR r.secret_ciphertext='' OR r.status NOT IN ('ACTIVE','OVERLAP','RETIRED') OR (r.status='OVERLAP' AND r.overlap_until IS NULL) OR (r.status<>'OVERLAP' AND r.overlap_until IS NOT NULL);"
)"

invalid_media_owner_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_media_account_owner o LEFT JOIN scx_ocpx_media_account a ON BINARY a.id=BINARY o.media_account_id LEFT JOIN sys_user u ON BINARY u.id=BINARY o.user_id WHERE a.id IS NULL OR u.id IS NULL OR u.del_flag<>'0' OR o.responsibility<>'PRIMARY' OR o.status NOT IN ('ACTIVE','DISABLED') OR o.version<1;"
)"

invalid_media_scope_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_user_scope s LEFT JOIN scx_ocpx_media_account a ON s.scope_type='MEDIA_ACCOUNT' AND BINARY a.id=BINARY s.scope_id WHERE s.deleted=0 AND s.status='ACTIVE' AND s.scope_type='MEDIA_ACCOUNT' AND a.id IS NULL;"
)"

invalid_monitor_media_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_monitor_link m LEFT JOIN scx_ocpx_media_account a ON BINARY a.id=BINARY m.media_account_id LEFT JOIN scx_ocpx_media_credential c ON BINARY c.id=BINARY m.media_credential_id LEFT JOIN scx_ocpx_media_account pa ON BINARY pa.id=BINARY m.published_media_account_id LEFT JOIN scx_ocpx_media_credential pc ON BINARY pc.id=BINARY m.published_media_credential_id WHERE m.deleted=0 AND ((m.media_account_id IS NOT NULL AND (a.id IS NULL OR BINARY a.channel_id<>BINARY m.channel_id)) OR (m.media_credential_id IS NOT NULL AND (m.media_account_id IS NULL OR c.id IS NULL OR BINARY c.media_account_id<>BINARY m.media_account_id)) OR (m.published_media_account_id IS NULL AND (m.published_media_account_version<>0 OR m.published_media_account_credential_revision<>0 OR m.published_media_credential_id IS NOT NULL OR m.published_media_credential_secret_revision<>0)) OR (m.published_media_account_id IS NOT NULL AND (pa.id IS NULL OR m.published_media_account_version<1 OR BINARY pa.channel_id<>BINARY m.channel_id OR m.published_media_credential_id IS NULL OR pc.id IS NULL OR BINARY pc.media_account_id<>BINARY m.published_media_account_id OR m.published_media_credential_secret_revision<1)));"
)"

if [[ "$invalid_media_account_count" -ne 0 || "$invalid_media_credential_count" -ne 0 || "$invalid_media_revision_count" -ne 0 || "$invalid_media_owner_count" -ne 0 || "$invalid_media_scope_count" -ne 0 || "$invalid_monitor_media_count" -ne 0 ]]; then
  echo "Database verification failed: invalid MediaAccount/Credential/Owner rows found." >&2
  exit 1
fi

task_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND ((table_name='scx_ocpx_task' AND column_name IN ('biz_code','advertiser_id','product_id','click_app_link_id','exposure_app_link_id','channel_id','media_account_id','media_credential_id','media_credential_secret_revision','operator_user_id','task_name','normalized_name','exposure_enabled','exposure_as_click','dedup_window_seconds','rta_mode','description','status','config_version','republish_required','republish_reason','published_task_version','create_request_key','create_request_hash','archived_by','archived_at','create_by','update_by','current_name_key')) OR (table_name='scx_ocpx_task_change_audit' AND column_name IN ('task_id','advertiser_id','channel_id','media_account_id','action','before_config_version','after_config_version','affected_monitor_count','actor','change_summary')) OR (table_name='scx_ocpx_monitor_link' AND column_name IN ('task_id','task_mapping_status','task_route_kind','task_config_revision')));"
)"

if [[ "$task_column_count" -ne 43 ]]; then
  echo "Database verification failed: S5 Task schema is incomplete." >&2
  exit 1
fi

task_index_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND CONCAT(table_name,':',index_name) IN ('scx_ocpx_task:uk_scx_ocpx_task_code','scx_ocpx_task:uk_scx_ocpx_task_name','scx_ocpx_task:uk_scx_ocpx_task_request','scx_ocpx_task:idx_scx_ocpx_task_filter','scx_ocpx_task:idx_scx_ocpx_task_product_link','scx_ocpx_task:idx_scx_ocpx_task_operator','scx_ocpx_task:idx_scx_ocpx_task_credential','scx_ocpx_task_change_audit:idx_scx_ocpx_task_audit','scx_ocpx_task_change_audit:idx_scx_ocpx_task_audit_scope','scx_ocpx_monitor_link:idx_scx_ocpx_monitor_task');"
)"

if [[ "$task_index_count" -ne 10 ]]; then
  echo "Database verification failed: S5 Task indexes are incomplete." >&2
  exit 1
fi

admin_task_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('ocpx:task:view','ocpx:task:add','ocpx:task:edit','ocpx:task:status','ocpx:task:archive','ocpx:task:publish','ocpx:monitor:view','ocpx:monitor:publish','ocpx:monitor:copy','ocpx:monitor:status');"
)"

if [[ "$admin_task_permission_count" -ne 10 ]]; then
  echo "Database verification failed: administrator Task permissions are incomplete." >&2
  exit 1
fi

invalid_task_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_task t LEFT JOIN scx_ocpx_advertiser a ON BINARY a.id=BINARY t.advertiser_id LEFT JOIN scx_ocpx_product p ON BINARY p.id=BINARY t.product_id LEFT JOIN scx_ocpx_app_link c ON BINARY c.id=BINARY t.click_app_link_id LEFT JOIN scx_ocpx_app_link e ON BINARY e.id=BINARY t.exposure_app_link_id LEFT JOIN scx_ocpx_channel h ON BINARY h.id=BINARY t.channel_id LEFT JOIN scx_ocpx_media_account m ON BINARY m.id=BINARY t.media_account_id LEFT JOIN scx_ocpx_media_credential d ON BINARY d.id=BINARY t.media_credential_id LEFT JOIN scx_ocpx_media_credential_revision r ON BINARY r.credential_id=BINARY t.media_credential_id AND r.secret_revision=t.media_credential_secret_revision LEFT JOIN sys_user u ON BINARY u.id=BINARY t.operator_user_id WHERE a.id IS NULL OR p.id IS NULL OR BINARY p.advertiser_id<>BINARY t.advertiser_id OR c.id IS NULL OR BINARY c.product_id<>BINARY t.product_id OR BINARY c.advertiser_id<>BINARY t.advertiser_id OR h.id IS NULL OR m.id IS NULL OR BINARY m.channel_id<>BINARY t.channel_id OR d.id IS NULL OR BINARY d.media_account_id<>BINARY t.media_account_id OR r.id IS NULL OR u.id IS NULL OR u.del_flag<>'0' OR t.task_name='' OR t.normalized_name='' OR t.status NOT IN ('DRAFT','READY','ACTIVE','PAUSED','ARCHIVED') OR t.rta_mode<>'OFF' OR t.dedup_window_seconds NOT BETWEEN 0 AND 86400 OR t.config_version<1 OR t.media_credential_secret_revision<1 OR t.published_task_version<0 OR t.published_task_version>t.config_version OR t.create_request_key='' OR CHAR_LENGTH(t.create_request_hash)<>64 OR (t.exposure_enabled=0 AND (t.exposure_app_link_id IS NOT NULL OR t.exposure_as_click<>0)) OR (t.exposure_enabled=1 AND (e.id IS NULL OR BINARY e.product_id<>BINARY t.product_id OR BINARY e.advertiser_id<>BINARY t.advertiser_id)) OR (t.status='ARCHIVED' AND t.archived_at IS NULL) OR (t.status<>'ARCHIVED' AND t.archived_at IS NOT NULL);"
)"

invalid_task_monitor_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_monitor_link m LEFT JOIN scx_ocpx_task t ON BINARY t.id=BINARY m.task_id WHERE m.deleted=0 AND ((m.task_id IS NULL AND (m.task_mapping_status<>'LEGACY_UNMAPPED' OR m.task_route_kind<>'LEGACY' OR m.task_config_revision<>0)) OR (m.task_id IS NOT NULL AND (t.id IS NULL OR m.task_mapping_status<>'READY' OR m.task_route_kind NOT IN ('CLICK','EXPOSURE') OR m.task_config_revision<1)));"
)"

if [[ "$invalid_task_count" -ne 0 || "$invalid_task_monitor_count" -ne 0 ]]; then
  echo "Database verification failed: invalid Task relation or legacy Monitor mapping rows found." >&2
  exit 1
fi

event_model_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND ((table_name='scx_ocpx_event_definition' AND column_name IN ('direction','standard_type','billing_eligible','subject_type','config_version','mapping_state','create_request_key','create_request_hash','archived_at','create_by','update_by')) OR (table_name='scx_ocpx_channel_event_mapping' AND column_name IN ('adapter_code','external_code','direction','event_definition_id','parameter_template_code','success_rule_code','config_version','mapping_state','create_request_key','create_request_hash','archived_at','create_by','update_by')) OR (table_name='scx_ocpx_task_event_binding' AND column_name IN ('task_id','channel_id','event_definition_id','channel_event_mapping_id','callback_ratio_basis_points','limit_value','limit_window','limit_timezone','over_limit_delivery','sampling_algorithm','source_type','source_id','source_revision','status','config_version','create_by','update_by')) OR (table_name='scx_ocpx_route_event_snapshot' AND column_name IN ('snapshot_schema_version','snapshot_mode','task_id','task_config_revision','task_event_binding_id','task_event_binding_revision','event_definition_id','event_definition_revision','event_mapping_revision','standard_event_code','direction','external_event_code','parameter_template_code','callback_ratio_basis_points','limit_value','limit_window','limit_timezone','over_limit_delivery','sampling_algorithm','snapshot_fingerprint')) OR (table_name='scx_ocpx_monitor_link' AND column_name='event_mapping_mode'));"
)"

if [[ "$event_model_column_count" -ne 62 ]]; then
  echo "Database verification failed: S6 four-layer Event schema is incomplete." >&2
  exit 1
fi

event_model_index_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND CONCAT(table_name,':',index_name) IN ('scx_ocpx_event_definition:uk_scx_ocpx_event_create_request','scx_ocpx_event_definition:idx_scx_ocpx_event_canonical','scx_ocpx_channel_event_mapping:uk_scx_ocpx_mapping_external','scx_ocpx_channel_event_mapping:uk_scx_ocpx_mapping_channel_id','scx_ocpx_channel_event_mapping:uk_scx_ocpx_mapping_request','scx_ocpx_channel_event_mapping:idx_scx_ocpx_mapping_canonical','scx_ocpx_task:uk_scx_ocpx_task_channel_id','scx_ocpx_task_event_binding:uk_scx_ocpx_task_event','scx_ocpx_task_event_binding:idx_scx_ocpx_task_event_mapping','scx_ocpx_task_event_binding:idx_scx_ocpx_task_event_channel','scx_ocpx_route_event_snapshot:uk_scx_ocpx_route_event_canonical','scx_ocpx_route_event_snapshot:idx_scx_ocpx_route_event_task','scx_ocpx_event_change_audit:idx_scx_ocpx_event_audit_object','scx_ocpx_event_change_audit:idx_scx_ocpx_event_audit_scope');"
)"

if [[ "$event_model_index_count" -ne 14 ]]; then
  echo "Database verification failed: S6 Event indexes are incomplete." >&2
  exit 1
fi

event_snapshot_trigger_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='${MYSQL_DATABASE}' AND trigger_name IN ('trg_scx_ocpx_route_event_snapshot_no_update','trg_scx_ocpx_route_event_snapshot_no_delete');"
)"

if [[ "$event_snapshot_trigger_count" -ne 2 ]]; then
  echo "Database verification failed: RouteEventSnapshot immutability triggers are missing." >&2
  exit 1
fi

admin_event_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('ocpx:event:view','ocpx:event:add','ocpx:event:edit','ocpx:event:status','ocpx:event-mapping:view','ocpx:event-mapping:add','ocpx:event-mapping:edit','ocpx:event-mapping:status','ocpx:task:event:edit');"
)"

if [[ "$admin_event_permission_count" -ne 9 ]]; then
  echo "Database verification failed: administrator S6 Event permissions are incomplete." >&2
  exit 1
fi

invalid_event_definition_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_event_definition e WHERE e.mapping_state NOT IN ('LEGACY_UNMAPPED','CANONICAL') OR (e.mapping_state='CANONICAL' AND (e.category<>'STANDARD' OR e.channel_id<>'' OR e.direction IS NULL OR e.standard_type IS NULL OR e.subject_type IS NULL OR e.direction NOT IN ('UPSTREAM_CALLBACK','DOWNSTREAM_DELIVERY','BIDIRECTIONAL') OR e.standard_type NOT REGEXP '^[A-Z][A-Z0-9_]{1,63}$' OR e.event_code NOT REGEXP '^[A-Z][A-Z0-9_]{1,63}$' OR e.subject_type NOT IN ('DEVICE','ACCOUNT','ORDER','LEAD') OR e.config_version<1));"
)"

invalid_event_mapping_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_channel_event_mapping m LEFT JOIN scx_ocpx_channel c ON BINARY c.id=BINARY m.channel_id LEFT JOIN scx_ocpx_adapter a ON BINARY a.adapter_code=BINARY m.adapter_code LEFT JOIN scx_ocpx_event_definition e ON BINARY e.id=BINARY m.event_definition_id WHERE c.id IS NULL OR m.mapping_state NOT IN ('LEGACY_UNMAPPED','CANONICAL') OR (m.mapping_state='CANONICAL' AND (m.adapter_code IS NULL OR m.external_code IS NULL OR m.direction IS NULL OR m.event_definition_id IS NULL OR m.parameter_template_code IS NULL OR m.success_rule_code IS NULL OR a.adapter_code IS NULL OR e.id IS NULL OR e.mapping_state<>'CANONICAL' OR BINARY c.adapter_code<>BINARY m.adapter_code OR (e.direction<>m.direction AND e.direction<>'BIDIRECTIONAL')));"
)"

invalid_task_event_binding_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_task_event_binding b LEFT JOIN scx_ocpx_task t ON BINARY t.id=BINARY b.task_id LEFT JOIN scx_ocpx_channel_event_mapping m ON BINARY m.id=BINARY b.channel_event_mapping_id LEFT JOIN scx_ocpx_event_definition e ON BINARY e.id=BINARY b.event_definition_id WHERE t.id IS NULL OR m.id IS NULL OR e.id IS NULL OR BINARY b.channel_id<>BINARY t.channel_id OR BINARY b.channel_id<>BINARY m.channel_id OR BINARY b.event_definition_id<>BINARY m.event_definition_id OR m.mapping_state<>'CANONICAL' OR e.mapping_state<>'CANONICAL' OR m.direction<>'DOWNSTREAM_DELIVERY' OR b.callback_ratio_basis_points NOT BETWEEN 0 AND 10000 OR (b.limit_value IS NOT NULL AND b.limit_value<0) OR b.limit_window<>'NATURAL_DAY' OR b.limit_timezone<>'Asia/Shanghai' OR b.sampling_algorithm<>'SHA256_MOD_10000_V1';"
)"

invalid_event_snapshot_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_route_event_snapshot s LEFT JOIN scx_ocpx_task_event_binding b ON BINARY b.id=BINARY s.task_event_binding_id LEFT JOIN scx_ocpx_event_definition e ON BINARY e.id=BINARY s.event_definition_id LEFT JOIN scx_ocpx_channel_event_mapping m ON BINARY m.id=BINARY s.event_mapping_id WHERE (s.snapshot_schema_version=1 AND s.snapshot_mode<>'LEGACY_V80') OR (s.snapshot_schema_version=2 AND (s.snapshot_mode<>'CANONICAL' OR s.task_id IS NULL OR s.task_config_revision IS NULL OR s.task_event_binding_id IS NULL OR s.task_event_binding_revision IS NULL OR s.event_definition_id IS NULL OR s.event_definition_revision IS NULL OR s.event_mapping_id IS NULL OR s.event_mapping_revision IS NULL OR s.standard_event_code IS NULL OR s.direction IS NULL OR s.external_event_code IS NULL OR s.parameter_template_code IS NULL OR s.callback_ratio_basis_points IS NULL OR s.limit_window IS NULL OR s.limit_timezone IS NULL OR s.sampling_algorithm IS NULL OR s.snapshot_fingerprint IS NULL OR b.id IS NULL OR e.id IS NULL OR m.id IS NULL OR s.direction<>'DOWNSTREAM_DELIVERY' OR CHAR_LENGTH(s.snapshot_fingerprint)<>64)) OR s.snapshot_schema_version NOT IN (1,2);"
)"

invalid_monitor_event_mode_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_monitor_link WHERE (event_snapshot_version=0 AND event_mapping_mode NOT IN ('LEGACY_UNMAPPED','LEGACY_ADAPTER')) OR (event_snapshot_version=1 AND event_mapping_mode<>'LEGACY_V80_SNAPSHOT') OR (event_snapshot_version>=2 AND event_mapping_mode<>'SNAPSHOT');"
)"

invalid_adapter_event_capability_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_adapter WHERE status='ACTIVE' AND NOT JSON_CONTAINS_PATH(capabilities_json,'all','$.eventParameterTemplates','$.eventMappingDirections');"
)"

if [[ "$invalid_event_definition_count" -ne 0 || "$invalid_event_mapping_count" -ne 0 || "$invalid_task_event_binding_count" -ne 0 || "$invalid_event_snapshot_count" -ne 0 || "$invalid_monitor_event_mode_count" -ne 0 || "$invalid_adapter_event_capability_count" -ne 0 ]]; then
  echo "Database verification failed: invalid or orphaned S6 Event truth rows found." >&2
  exit 1
fi

s7_table_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name IN ('scx_ocpx_route_version','scx_ocpx_route_runtime_state','scx_ocpx_route_publish_record','scx_ocpx_route_distribution','scx_ocpx_route_instance_ack','scx_ocpx_route_pause_command');"
)"

s7_outbox_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND table_name='scx_ocpx_config_outbox' AND column_name IN ('payload_json','delivery_status','attempt_count','available_at','lease_owner','lease_until','last_error_code','update_time');"
)"

s7_trigger_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='${MYSQL_DATABASE}' AND trigger_name IN ('trg_scx_ocpx_route_version_no_update','trg_scx_ocpx_route_version_no_delete');"
)"

invalid_s7_route_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_route_version v LEFT JOIN scx_ocpx_monitor_link m ON BINARY m.id=BINARY v.monitor_id LEFT JOIN scx_ocpx_task t ON BINARY t.id=BINARY v.task_id LEFT JOIN scx_ocpx_route_runtime_state s ON BINARY s.route_version_id=BINARY v.id LEFT JOIN scx_ocpx_route_distribution d ON BINARY d.route_version_id=BINARY v.id WHERE m.id IS NULL OR t.id IS NULL OR s.id IS NULL OR d.id IS NULL OR BINARY m.task_id<>BINARY v.task_id OR v.route_version<1 OR v.route_schema_version<>3 OR CHAR_LENGTH(v.route_code_hash)<>64 OR CHAR_LENGTH(v.snapshot_fingerprint)<>64 OR v.rta_mode<>'OFF' OR v.event_snapshot_schema_version<>2 OR v.status<>'PUBLISHED' OR s.route_version<>v.route_version OR d.route_version<>v.route_version;"
)"

invalid_s7_outbox_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_config_outbox WHERE delivery_status NOT IN ('PENDING','CLAIMED','RETRY_WAIT','DELIVERED','FAILED') OR attempt_count<0 OR available_at IS NULL OR (delivery_status='DELIVERED' AND published<>1);"
)"

if [[ "$s7_table_count" -ne 6 || "$s7_outbox_column_count" -ne 8 || "$s7_trigger_count" -ne 2 || "$invalid_s7_route_count" -ne 0 || "$invalid_s7_outbox_count" -ne 0 ]]; then
  echo "Database verification failed: S7 publication/runtime dimensions are incomplete or invalid." >&2
  exit 1
fi

ocpx_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND ((table_name='scx_ocpx_channel' AND column_name IN ('channel_code','adapter_code','auth_type','config_version','callback_host_allowlist')) OR (table_name='scx_ocpx_advertiser' AND column_name IN ('upstream_host_allowlist','bid_url')) OR (table_name='scx_ocpx_monitor_link' AND column_name IN ('route_status','config_version','downstream_callback_url','upstream_feedback_url','published_upstream_feedback_url','click_dedup_window_seconds','event_snapshot_version','event_mapping_mode')) OR (table_name='scx_ocpx_hourly_report' AND column_name IN ('raw_conversion_count','forwarded_conversion_count','deducted_conversion_count','delivery_failure_count','first_order_count','eligible_click_count','deduplicated_click_count','skipped_click_count','classified_click_count','first_order_count','first_activate_count','reactivation_count','retention_3d_count','retention_4d_count','retention_5d_count','retention_6d_count','retention_7d_count','create_time','update_time')) OR (table_name='scx_ocpx_event' AND column_name IN ('device_platform','device_id_type','device_key_version','source_event_code','conversion_event_code','media_event_code','cpa_charge_amount','daily_limit_result')) OR (table_name='scx_ocpx_delivery_attempt' AND column_name IN ('request_method','target_host','target_path','request_summary','response_excerpt','duration_ms')) OR (table_name='scx_ocpx_billing_entry' AND column_name IN ('balance_before','balance_after','operator_id','remark','channel_id','app_link_id','monitor_id','source_event_code','conversion_event_code','media_event_code')));")"

if [[ "$ocpx_column_count" -ne 57 ]]; then
  echo "Database verification failed: OCPX routing/report/accounting columns are incomplete." >&2
  exit 1
fi

bid_url_length="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT character_maximum_length FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND table_name='scx_ocpx_advertiser' AND column_name='bid_url';")"

if [[ "$bid_url_length" -ne 5000 ]]; then
  echo "Database verification failed: advertiser bid_url is not VARCHAR(5000)." >&2
  exit 1
fi

ocpx_audit_index_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND index_name IN ('idx_scx_ocpx_attempt_job','idx_scx_ocpx_attempt_time','idx_scx_ocpx_ingress_audit_time','idx_scx_ocpx_ingress_audit_event','idx_scx_ocpx_ingress_audit_route');")"

if [[ "$ocpx_audit_index_count" -ne 5 ]]; then
  echo "Database verification failed: OCPX attempt/ingress audit indexes are incomplete." >&2
  exit 1
fi

ocpx_adapter_contract_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_adapter WHERE adapter_code IN ('COCO','TEMPLATE_GET','STANDARD','TEMPLATE_GET_DOWNSTREAM') AND status='ACTIVE' AND JSON_UNQUOTE(JSON_EXTRACT(capabilities_json,'$.contractVersion'))='1.0' AND JSON_CONTAINS_PATH(capabilities_json,'all','$.supportedMacros','$.requiredUrlMacros','$.requiredAnyUrlMacros');")"

if [[ "$ocpx_adapter_contract_count" -ne 4 ]]; then
  echo "Database verification failed: built-in OCPX adapter capability contracts are incomplete." >&2
  exit 1
fi

admin_ocpx_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('ocpx:monitor:publish','ocpx:account:read','ocpx:account:recharge');")"

if [[ "$admin_ocpx_permission_count" -ne 3 ]]; then
  echo "Database verification failed: administrator OCPX monitor/account permissions are incomplete." >&2
  exit 1
fi

event_seed_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_event_definition WHERE deleted=0 AND built_in=1;")"
channel_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_channel WHERE deleted=0;")"
expected_event_seed_count="$((22 + channel_count * 44))"
if [[ "$event_seed_count" -lt "$expected_event_seed_count" ]]; then
  echo "Database verification failed: default event dictionaries are incomplete." >&2
  exit 1
fi

default_mapping_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_channel_event_mapping WHERE deleted=0 AND status='ACTIVE';")"
if [[ "$default_mapping_count" -lt "$((channel_count * 4))" ]]; then
  echo "Database verification failed: four default channel event mappings are incomplete." >&2
  exit 1
fi

s8_table_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name IN ('scx_ocpx_hourly_fact_contribution','scx_ocpx_hourly_aggregation_cursor','scx_ocpx_hourly_adjustment','scx_ocpx_report_export_task','scx_ocpx_report_export_audit');"
)"

s8_hourly_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND table_name='scx_ocpx_hourly_report' AND column_name IN ('tenant_id','stat_date','stat_hour','timezone','product_id','task_id','media_account_id','operator_user_id','route_version_id','route_version','dimension_status','raw_clicks','valid_clicks','duplicate_clicks','adjustment_count','deduction_count','adjustment_amount','deduction_amount','source_watermark','as_of','finality','quality_status','metric_definitions_version','dimension_key');"
)"

s8_event_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND table_name='scx_ocpx_event' AND column_name IN ('tenant_id','product_id','task_id','media_account_id','operator_user_id','route_version_id','dimension_status');"
)"

s8_index_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND CONCAT(table_name,':',index_name) IN ('scx_ocpx_hourly_report:uk_scx_ocpx_hourly_frozen_grain','scx_ocpx_hourly_report:idx_scx_ocpx_hourly_time_task','scx_ocpx_hourly_report:idx_scx_ocpx_hourly_scope','scx_ocpx_hourly_report:idx_scx_ocpx_hourly_product','scx_ocpx_hourly_report:idx_scx_ocpx_hourly_media','scx_ocpx_hourly_fact_contribution:uk_scx_ocpx_fact_contribution_source','scx_ocpx_hourly_fact_contribution:idx_scx_ocpx_fact_contribution_claim','scx_ocpx_hourly_adjustment:uk_scx_ocpx_hourly_adjustment_source','scx_ocpx_report_export_task:uk_scx_ocpx_report_export_idempotency','scx_ocpx_report_export_task:idx_scx_ocpx_report_export_claim');"
)"

s8_adjustment_trigger_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='${MYSQL_DATABASE}' AND trigger_name IN ('trg_scx_ocpx_hourly_adjustment_no_update','trg_scx_ocpx_hourly_adjustment_no_delete');"
)"

s8_admin_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('ocpx:report:view','ocpx:report:export','ocpx:report:deduction:export','ocpx:report:metric:sensitive');"
)"

s8_invalid_hourly_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_hourly_report WHERE timezone<>'Asia/Shanghai' OR dimension_status NOT IN ('UNMAPPED','FROZEN') OR finality NOT IN ('DELAYED','PROVISIONAL','FINAL') OR quality_status NOT IN ('COMPLETE','PARTIAL','UNMAPPED') OR metric_definitions_version<>'S8_V1' OR raw_clicks<0 OR valid_clicks<0 OR duplicate_clicks<0 OR (dimension_status='FROZEN' AND (tenant_id IS NULL OR product_id IS NULL OR task_id IS NULL OR media_account_id IS NULL OR operator_user_id IS NULL OR route_version_id IS NULL OR route_version<1));"
)"

s8_invalid_contribution_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_hourly_fact_contribution WHERE contribution_kind NOT IN ('FEEDBACK','CALLBACK','DELIVERY_SUCCESS','DELIVERY_FAILURE') OR timezone<>'Asia/Shanghai' OR route_version<1 OR stat_hour<>DATE_FORMAT(event_time,'%Y-%m-%d %H:00:00');"
)"

s8_invalid_export_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_report_export_task WHERE export_type NOT IN ('STANDARD','DEDUCTION') OR metric_definitions_version<>'S8_V1' OR status NOT IN ('PENDING','PROCESSING','SUCCEEDED','RETRY_WAIT','FAILED','CANCELLED','EXPIRED') OR attempt_count<0;"
)"

if [[ "$s8_table_count" -ne 5 || "$s8_hourly_column_count" -ne 24 || "$s8_event_column_count" -ne 7 || "$s8_index_count" -ne 10 || "$s8_adjustment_trigger_count" -ne 2 || "$s8_admin_permission_count" -ne 4 || "$s8_invalid_hourly_count" -ne 0 || "$s8_invalid_contribution_count" -ne 0 || "$s8_invalid_export_count" -ne 0 ]]; then
  echo "Database verification failed: S8 unified hourly facts/reports are incomplete or invalid." >&2
  exit 1
fi

s9_dashboard_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission='ocpx:dashboard:view';"
)"

s9_dashboard_scope_index_columns="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','),'') FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND table_name='scx_ocpx_hourly_report' AND index_name='idx_scx_ocpx_dashboard_scope';"
)"

s9_legacy_dashboard_grant_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id WHERE m.permission='ocpx:dashboard:read' AND m.del_flag='0';"
)"

if [[ "$s9_dashboard_permission_count" -ne 1 || "$s9_dashboard_scope_index_columns" != "advertiser_id,channel_id,media_account_id,time_hour" || "$s9_legacy_dashboard_grant_count" -ne 0 ]]; then
  echo "Database verification failed: S9 Dashboard scope index or permission contract is incomplete." >&2
  exit 1
fi

s10_table_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name IN ('scx_ocpx_runtime_fact','scx_ocpx_runtime_failure','scx_ocpx_runtime_replay_request','scx_ocpx_runtime_replay_attempt','scx_ocpx_runtime_command_audit');"
)"

s10_trigger_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='${MYSQL_DATABASE}' AND trigger_name IN ('trg_scx_ocpx_runtime_fact_no_update','trg_scx_ocpx_runtime_fact_no_delete','trg_scx_ocpx_runtime_failure_no_update','trg_scx_ocpx_runtime_failure_no_delete','trg_scx_ocpx_runtime_replay_attempt_no_update','trg_scx_ocpx_runtime_replay_attempt_no_delete','trg_scx_ocpx_runtime_command_audit_no_update','trg_scx_ocpx_runtime_command_audit_no_delete');"
)"

s10_unique_index_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND non_unique=0 AND CONCAT(table_name,':',index_name) IN ('scx_ocpx_delivery_attempt:uk_scx_ocpx_delivery_attempt_no','scx_ocpx_runtime_fact:uk_scx_ocpx_runtime_fact_idempotency','scx_ocpx_runtime_failure:uk_scx_ocpx_runtime_failure_source','scx_ocpx_runtime_replay_request:uk_scx_ocpx_runtime_replay_request','scx_ocpx_runtime_replay_request:uk_scx_ocpx_runtime_replay_fingerprint','scx_ocpx_runtime_replay_attempt:uk_scx_ocpx_runtime_replay_attempt_no');"
)"

s10_scope_index_columns="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','),'') FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND table_name='scx_ocpx_runtime_fact' AND index_name='idx_scx_ocpx_runtime_fact_time_scope';"
)"

s10_admin_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('ocpx:runtime:view','ocpx:runtime:detail','ocpx:runtime:body','ocpx:runtime:export','ocpx:runtime:dlq:view','ocpx:runtime:retry','ocpx:runtime:replay','ocpx:runtime:debug','ocpx:runtime:emergency-stop');"
)"

s10_legacy_runtime_grant_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id WHERE m.del_flag='0' AND m.permission IN ('ocpx:runtime:read');"
)"

s10_invalid_fact_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_runtime_fact WHERE dimension_status NOT IN ('FROZEN','UNMAPPED') OR quality_status NOT IN ('COMPLETE','PARTIAL','UNMAPPED') OR CHAR_LENGTH(idempotency_key_hash)<>64 OR received_time<event_time OR (dimension_status='FROZEN' AND (tenant_id IS NULL OR advertiser_id IS NULL OR product_id IS NULL OR app_link_id IS NULL OR task_id IS NULL OR channel_id IS NULL OR media_account_id IS NULL OR operator_user_id IS NULL OR route_id IS NULL OR route_version_id IS NULL OR route_version<1));"
)"

s10_invalid_failure_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_runtime_failure WHERE failure_revision<1 OR failure_class NOT IN ('RETRYABLE','NON_RETRYABLE','PROTOCOL_REJECTED','SIGNATURE_FAILED','SCOPE_REJECTED','DUPLICATE','LATE_ARRIVAL','UNKNOWN') OR CHAR_LENGTH(summary_hash)<>64 OR received_time<event_time;"
)"

s10_invalid_replay_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_runtime_replay_request WHERE mode<>'ORIGINAL_SNAPSHOT_ONLY' OR command_type<>'REPLAY' OR expected_failure_revision<1 OR source_route_version<1 OR status NOT IN ('QUEUED','CLAIMED','RETRY_WAIT','SUCCEEDED','TERMINAL','CANCELLED') OR attempt_count<0 OR attempt_count>max_attempts OR max_attempts NOT BETWEEN 1 AND 10;"
)"

if [[ "$s10_table_count" -ne 5 || "$s10_trigger_count" -ne 8 || "$s10_unique_index_count" -ne 6 || "$s10_scope_index_columns" != "advertiser_id,channel_id,media_account_id,received_time" || "$s10_admin_permission_count" -ne 9 || "$s10_legacy_runtime_grant_count" -ne 0 || "$s10_invalid_fact_count" -ne 0 || "$s10_invalid_failure_count" -ne 0 || "$s10_invalid_replay_count" -ne 0 ]]; then
  echo "Database verification failed: S10 runtime facts, compensation truth or permissions are incomplete." >&2
  exit 1
fi

echo "Smart Converge X database verification passed: ${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}"
