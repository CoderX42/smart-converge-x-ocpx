#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

python3 - "${ROOT_DIR}" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
schema = (root / "scx-backend/scx-boot/src/main/resources/db/schema/scx_current_schema.sql").read_text(encoding="utf-8")
table_starts = list(
    re.finditer(
        r"CREATE TABLE IF NOT EXISTS\s+(scx_ocpx_[a-z0-9_]+)",
        schema,
        re.IGNORECASE,
    )
)
business = []
for index, match in enumerate(table_starts):
    end = table_starts[index + 1].start() if index + 1 < len(table_starts) else len(schema)
    if "COMMENT='BUSINESS:" in schema[match.start():end]:
        business.append(match.group(1))
expected_business = {
    "scx_ocpx_advertiser",
    "scx_ocpx_creative",
    "scx_ocpx_promotion_link",
    "scx_ocpx_media",
    "scx_ocpx_monitor_task",
    "scx_ocpx_deduction_rule",
}
if set(business) != expected_business or len(business) != 6:
    raise SystemExit(f"OCPX management gate failed: BUSINESS tables are {business!r}")

expected_data = {
    "scx_ocpx_protocol_allowed_host",
    "scx_ocpx_advertiser_account",
    "scx_ocpx_creative_promotion_link",
    "scx_ocpx_billing_config_audit",
    "scx_ocpx_event_fact",
    "scx_ocpx_billing_entry",
    "scx_ocpx_hourly_metric",
    "scx_ocpx_flow_log",
    "scx_ocpx_flow_log_detail",
    "scx_ocpx_log_feedback",
    "scx_ocpx_log_feedback_day",
    "scx_ocpx_log_callback",
    "scx_ocpx_report_mat_temp",
    "scx_ocpx_report_mat",
    "scx_ocpx_rta_upstream",
    "scx_ocpx_rta_media_access",
    "scx_ocpx_rta_binding",
    "scx_ocpx_rta_log",
    "scx_ocpx_rta_hour_stat",
}
actual = re.findall(r"CREATE TABLE IF NOT EXISTS\s+(scx_ocpx_[a-z0-9_]+)", schema, re.IGNORECASE)
if set(actual) != expected_business | expected_data or len(actual) != 25:
    raise SystemExit(f"OCPX management gate failed: OCPX tables are {actual!r}")
if "scx_ocpx_report_hourly" in schema:
    raise SystemExit("legacy data-layer gate failed: obsolete hourly table remains")

def table_body(table: str) -> str:
    match = re.search(
        rf"CREATE TABLE IF NOT EXISTS\s+{table}\s*\((.*?)\) ENGINE=",
        schema,
        re.IGNORECASE | re.DOTALL,
    )
    if not match:
        raise SystemExit(f"V102 schema gate failed: missing {table}")
    return match.group(1).lower()

v102_columns = {
    "scx_ocpx_advertiser": {
        "required": ("advertiser_name", "upstream_protocol varchar(32)", "account_user_id varchar(64)", "credential_config_key", "bid_url", "macro_params"),
        "retired": ("advertiser_code", "adapter_code", "credential_version", "status", "deleted", "active_marker"),
    },
    "scx_ocpx_creative": {
        "required": ("advertiser_id", "creative_name", "source_material_id", "checksum_sha256", "is_delete"),
        "retired": ("creative_id", "group_id", "c_id"),
    },
    "scx_ocpx_promotion_link": {
        "required": ("platform tinyint", "link_url", "deeplink", "link_ext", "is_delete"),
        "retired": ("link_code", "package_name", "link_type", "link_url_template", "deeplink_url", "download_url", "macro_set", "status", "active_marker"),
    },
    "scx_ocpx_media": {
        "required": ("media_name", "credential_config_key", "uri varchar", "macro_params"),
        "retired": ("media_code", "adapter_code", "auth_type", "credential_version", "ingress_uri", "callback_url", "status", "deleted", "active_marker"),
    },
    "scx_ocpx_monitor_task": {
        "required": ("monitor_name", "billing_enabled tinyint", "billing_type tinyint", "unit_price decimal(20,6)", "conversion_event", "biz_id", "show_url text", "clk_url text", "is_delete"),
        "retired": ("task_code", "task_name", "operator_user_id", "status", "config_version", "archived_at", "active_marker"),
    },
    "scx_ocpx_deduction_rule": {
        "required": ("promotion_link_id", "media_id", "ratio decimal(9,6)", "is_delete", "ck_ocpx_deduction_ratio"),
        "retired": ("active_marker",),
    },
}
for table, contract in v102_columns.items():
    body = table_body(table)
    for required_column in contract["required"]:
        if required_column not in body:
            raise SystemExit(f"V102 schema gate failed: {table} missing {required_column}")
    for retired_column in contract["retired"]:
        if re.search(rf"\b{retired_column}\b", body):
            raise SystemExit(f"V102 schema gate failed: {table} retains {retired_column}")
if "(if(is_delete=0,1,null))" not in table_body("scx_ocpx_deduction_rule"):
    raise SystemExit("V102 schema gate failed: deduction functional unique index is missing")
for required_fragment in (
    "deduction_decision varchar(16)",
    "deduction_ratio_snapshot decimal(9,6)",
    "deduction_decided_at datetime(3)",
):
    if required_fragment not in table_body("scx_ocpx_event_fact"):
        raise SystemExit(f"V113 schema gate failed: event fact missing {required_fragment}")
for required_fragment in (
    "callback_count bigint",
    "deduction_passed_count bigint",
    "deduction_deducted_count bigint",
):
    if required_fragment not in table_body("scx_ocpx_hourly_metric"):
        raise SystemExit(f"V113 schema gate failed: hourly metric missing {required_fragment}")

retired = re.compile(
    r"scx_ocpx_(channel|app_link|monitor_link|hourly_report|product|media_account|media_credential|task)(?:\W|$)"
)
for relative in (
    "scx-backend/scx-ocpx-core/src",
    "scx-backend/scx-ocpx-engine/src",
    "scx-backend/scx-ocpx-admin/src",
    "scx-backend/scx-boot/src/main/resources/db/schema/scx_current_schema.sql",
):
    path = root / relative
    files = path.rglob("*") if path.is_dir() else [path]
    for file in files:
        if file.is_file() and file.suffix in {".java", ".xml", ".sql", ".yml"}:
            text = file.read_text(encoding="utf-8")
            if retired.search(text):
                raise SystemExit(f"four-entity gate failed: retired model in {file}")

engine = root / "scx-backend/scx-ocpx-engine"
java_root = engine / "src/main/java/com/yzytech/scx/ocpx"
core = root / "scx-backend/scx-ocpx-core"
core_java_root = core / "src/main/java/com/yzytech/scx/ocpx"
required = {
    "ScxOcpxEngineApplication.java",
    "feedback/model/FeedbackReq.java",
    "feedback/web/FeedbackIngressController.java",
    "feedback/web/FeedbackIngressAdapter.java",
    "feedback/web/FeedbackRequestAdapter.java",
    "feedback/service/FeedbackServiceImpl.java",
    "fact/service/OcpxEventFactService.java",
    "feedback/delivery/UpstreamDeliveryDispatcher.java",
    "feedback/delivery/TemplateGetUpstreamDeliveryService.java",
	"feedback/delivery/aerim/AerimUpstreamDeliveryService.java",
    "feedback/delivery/weifeng/WeifengUpstreamDeliveryService.java",
	"feedback/delivery/renrenvideo/RenrenVideoUpstreamDeliveryService.java",
	"feedback/delivery/renrenvideo/RenrenVideoEndpointPolicy.java",
	"feedback/delivery/renrenvideo/RenrenVideoHttpClient.java",
	"feedback/delivery/phoenix/PhoenixUpstreamDeliveryService.java",
	"feedback/delivery/phoenix/PhoenixEndpointPolicy.java",
	"feedback/delivery/phoenix/PhoenixHttpClient.java",
    "callback/model/CallbackReq.java",
    "callback/web/UpstreamCallbackController.java",
	"callback/web/aerim/AerimCallbackController.java",
    "callback/web/weifeng/WeifengCallbackController.java",
	"callback/web/renrenvideo/RenrenVideoCallbackAdapter.java",
	"callback/web/renrenvideo/RenrenVideoCallbackController.java",
	"callback/web/phoenix/PhoenixCallbackRequest.java",
	"callback/web/phoenix/PhoenixCallbackResponse.java",
	"callback/web/phoenix/PhoenixCallbackAdapter.java",
	"callback/web/phoenix/PhoenixCallbackController.java",
    "callback/service/CallbackServiceImpl.java",
    "callback/delivery/DownstreamDeliveryDispatcher.java",
    "callback/delivery/CallbackUrlDownstreamDeliveryService.java",
    "protocol/CallbackRequestAdapter.java",
    "protocol/template/TemplateGetProtocolAdapter.java",
    "report/model/LogData.java",
    "report/task/StorageTask.java",
    "configuration/index/OcpxAdvertiserIndex.java",
    "configuration/index/OcpxPromotionLinkIndex.java",
    "configuration/index/OcpxMediaIndex.java",
    "configuration/index/OcpxMonitorTaskIndex.java",
    "configuration/index/OcpxProtocolAllowedHostIndex.java",
    "configuration/mapper/OcpxProtocolAllowedHostConfigMapper.java",
    "configuration/task/ResetRedisKeyTask.java",
    "configuration/credential/PartnerCredentialProperties.java",
    "configuration/credential/PartnerCredentialResolver.java",
    "protocol/registry/AdvertiserProtocolRegistry.java",
    "protocol/security/ProtocolDnsResolver.java",
    "report/service/OcpxFlowLogWriter.java",
    "shared/redis/RedisHelper.java",
}
actual_sources = {p.relative_to(java_root).as_posix() for p in java_root.rglob("*.java")}
missing = required - actual_sources
if missing:
    raise SystemExit(f"OCPX Engine gate failed: missing sources {sorted(missing)!r}")

retired_partner_sources = {
    "callback/delivery/mi/MiDownstreamDeliveryService.java",
    "callback/web/kimi/KimiCallbackAdapter.java",
    "callback/web/kimi/KimiCallbackController.java",
    "callback/web/kimi/KimiCallbackReq.java",
    "feedback/delivery/kimi/KimiUpstreamDeliveryService.java",
    "feedback/web/mi/MiFeedbackController.java",
    "feedback/web/mi/MiParamAdapter.java",
    "feedback/web/mi/MiReq.java",
    "protocol/registry/MediaProtocolRegistry.java",
}
remaining_partner_sources = retired_partner_sources & actual_sources
if remaining_partner_sources:
    raise SystemExit(
        f"OCPX Engine gate failed: retired KIMI/MI sources remain {sorted(remaining_partner_sources)!r}"
    )

retired_partner_markers = (
    "KIMI",
    "/kimi/callback",
    "/mi/feedback",
    "MediaProtocolRegistry",
    "MiDownstreamDeliveryService",
    "trail.e.mi.com",
)
for file in java_root.rglob("*.java"):
    source = file.read_text(encoding="utf-8")
    if any(marker in source for marker in retired_partner_markers):
        raise SystemExit(f"OCPX Engine gate failed: retired KIMI/MI marker remains in {file}")

retired_engine_sources = {
    "feedback/web/GeneralController.java",
    "feedback/web/GeneralParamAdapter.java",
    "feedback/delivery/UnionServiceImpl.java",
    "callback/web/UnionController.java",
    "callback/delivery/GeneralServiceImpl.java",
    "protocol/union/UnionParamAdapter.java",
    "configuration/index/UserDspIndex.java",
    "configuration/index/DspLinkIndex.java",
    "configuration/index/UserChannelIndex.java",
    "configuration/index/ChannelMonitorIndex.java",
}
remaining_retired_sources = retired_engine_sources & actual_sources
if remaining_retired_sources:
    raise SystemExit(
        f"OCPX Engine gate failed: retired protocol-neutral names remain {sorted(remaining_retired_sources)!r}"
    )

retired_package_roots = {
    "bean",
    "constant",
    "controller",
    "dao",
    "index",
    "manager",
    "service",
    "support",
    "task",
    "util",
}
remaining_retired = sorted(name for name in retired_package_roots if (java_root / name).exists())
if remaining_retired:
    raise SystemExit(f"feature-package gate failed: retired package roots remain {remaining_retired!r}")

mapped = []
for entity_root in (
    core_java_root / "entity",
    java_root / "entity",
    root / "scx-backend/scx-ocpx-admin/src/main/java/com/yzytech/scx/ocpx/entity",
):
    if not entity_root.exists():
        continue
    for file in entity_root.rglob("*.java"):
        if "@TableName(" in file.read_text(encoding="utf-8"):
            mapped.append(file.relative_to(root).as_posix())
expected_mapped = {
    "scx-backend/scx-ocpx-core/src/main/java/com/yzytech/scx/ocpx/entity/OcpxAdvertiser.java",
    "scx-backend/scx-ocpx-core/src/main/java/com/yzytech/scx/ocpx/entity/OcpxCreative.java",
    "scx-backend/scx-ocpx-core/src/main/java/com/yzytech/scx/ocpx/entity/OcpxPromotionLink.java",
    "scx-backend/scx-ocpx-core/src/main/java/com/yzytech/scx/ocpx/entity/OcpxMedia.java",
    "scx-backend/scx-ocpx-core/src/main/java/com/yzytech/scx/ocpx/entity/OcpxMonitorTask.java",
    "scx-backend/scx-ocpx-core/src/main/java/com/yzytech/scx/ocpx/entity/OcpxDeductionRule.java",
    "scx-backend/scx-ocpx-core/src/main/java/com/yzytech/scx/ocpx/entity/OcpxProtocolAllowedHost.java",
}
if set(mapped) != expected_mapped:
    raise SystemExit(f"four-entity gate failed: shared entities are {sorted(mapped)!r}")

active_management = "\n".join(
    file.read_text(encoding="utf-8")
    for relative in (
        "scx-backend/scx-ocpx-core/src/main/java",
        "scx-backend/scx-ocpx-admin/src/main/java",
    )
    for file in (root / relative).rglob("*.java")
)
for retired_name in (
    "advertiserCode", "mediaCode", "credentialVersion",
    "upstreamHostAllowlist", "callbackHostAllowlist", "configVersion",
    "linkUrlTemplate", "deeplinkUrl", "downloadUrl", "macroSet",
    "redirectEnabled", "taskCode", "taskName", "archivedAt",
):
    if re.search(rf"\b{retired_name}\b", active_management):
        raise SystemExit(f"V102 source gate failed: retired management field {retired_name} remains")

admin_controllers = "\n".join(
    file.read_text(encoding="utf-8")
    for file in (root / "scx-backend/scx-ocpx-admin/src/main/java/com/yzytech/scx/ocpx/admin/controller").glob("*.java")
)
for retired_endpoint in (
    "/status", "/archive", "/preflight", "/connectivity-check", "/url:validate",
):
    if retired_endpoint in admin_controllers:
        raise SystemExit(f"V102 API gate failed: retired endpoint {retired_endpoint} remains")

v102 = (root / "scx-backend/scx-boot/src/main/resources/db/migration/V102__align_ocpx_management_with_vientiane.sql").read_text(encoding="utf-8")
for required_fragment in (
    "soft-deleted advertiser ids require manual handling",
    "soft-deleted media ids require manual handling",
    "WEB OTHER or invalid platform ids require manual mapping",
    "RENAME COLUMN ingress_uri TO uri",
    "RENAME COLUMN task_name TO monitor_name",
    "RENAME COLUMN deleted TO is_delete",
    "perm_ocpx_promotion_link_delete",
):
    if required_fragment not in v102:
        raise SystemExit(f"V102 migration gate failed: missing {required_fragment}")
if "scx_schema_migration" in v102:
    raise SystemExit("V102 migration gate failed: retired migration ledger is referenced")

v103 = (
    root / "scx-backend/scx-boot/src/main/resources/db/migration/V103__add_advertiser_account_scope.sql"
).read_text(encoding="utf-8")
for required_fragment in (
    "ADD COLUMN account_user_id VARCHAR(64) NULL",
    "uk_ocpx_advertiser_account_user",
    "FOREIGN KEY (account_user_id) REFERENCES sys_user(id)",
    "MODIFY COLUMN show_url TEXT NULL",
    "MODIFY COLUMN clk_url TEXT NULL",
    "'role_advertiser','广告主','ROLE_ADVERTISER'",
    "perm_ocpx_report_export",
):
    if required_fragment not in v103:
        raise SystemExit(f"V103 migration gate failed: missing {required_fragment}")
if "scx_schema_migration" in v103:
    raise SystemExit("V103 migration gate failed: retired migration ledger is referenced")

v104 = (
    root / "scx-backend/scx-boot/src/main/resources/db/migration/V104__grant_admin_full_ocpx_crud.sql"
).read_text(encoding="utf-8")
for required_fragment in (
    "perm_ocpx_advertiser_delete",
    "ocpx:advertiser:delete",
    "perm_ocpx_media_delete",
    "ocpx:media:delete",
    "r.role_code='ROLE_ADMIN'",
):
    if required_fragment not in v104:
        raise SystemExit(f"V104 migration gate failed: missing {required_fragment}")
if "role_advertiser" in v104.lower():
    raise SystemExit("V104 migration gate failed: advertiser role must remain read-only")
if "scx_schema_migration" in v104:
    raise SystemExit("V104 migration gate failed: retired migration ledger is referenced")

v106 = (
    root / "scx-backend/scx-boot/src/main/resources/db/migration/V106__add_aerim_callback_fidelity.sql"
).read_text(encoding="utf-8")
for required_fragment in (
    "partner_action_type VARCHAR(50)",
    "standard_action_type TINYINT UNSIGNED",
    "action_time BIGINT",
    "currency_amount DECIMAL(20,6)",
    "first_activation INT",
    "first_order INT",
):
    if required_fragment not in v106:
        raise SystemExit(f"V106 migration gate failed: missing {required_fragment}")
if "scx_schema_migration" in v106:
    raise SystemExit("V106 migration gate failed: retired migration ledger is referenced")

v107 = (
    root / "scx-backend/scx-boot/src/main/resources/db/migration/V107__add_advertiser_shared_role_permissions.sql"
).read_text(encoding="utf-8")
for required_fragment in (
    "ocpx:advertiser:role:manage",
    "ocpx:advertiser:scope:all",
    "r.role_code='ROLE_ADMIN'",
    "WHERE role_id='role_advertiser'",
    "m.path LIKE '/ocpx%'",
    "m.permission LIKE 'ocpx:%'",
    "COALESCE(m.permission,'') <> 'ocpx:advertiser:role:manage'",
):
    if required_fragment not in v107:
        raise SystemExit(f"V107 migration gate failed: missing {required_fragment}")
if "scx_schema_migration" in v107:
    raise SystemExit("V107 migration gate failed: retired migration ledger is referenced")

v109 = (
    root / "scx-backend/scx-boot/src/main/resources/db/migration/V109__retire_legacy_ocpx_permissions.sql"
).read_text(encoding="utf-8")
for required_fragment in (
    "ocpx:api-debug:read",
    "ocpx:monitor:publish",
    "ocpx:monitor:read",
    "ocpx:monitor:write",
    "DELETE rm\nFROM sys_role_menu rm",
    "UPDATE sys_menu\nSET del_flag='1'",
):
    if required_fragment not in v109:
        raise SystemExit(f"V109 migration gate failed: missing {required_fragment}")
if "DELETE FROM sys_menu" in v109:
    raise SystemExit("V109 migration gate failed: legacy menu rows must be soft-deleted")
if "scx_schema_migration" in v109:
    raise SystemExit("V109 migration gate failed: retired migration ledger is referenced")
for table, columns in {
    "scx_ocpx_log_callback": ("partner_action_type", "standard_action_type", "action_time", "currency_amount"),
    "scx_ocpx_report_mat_temp": ("first_activation", "first_order", "currency_amount"),
    "scx_ocpx_report_mat": ("first_activation", "first_order", "currency_amount"),
}.items():
    body = table_body(table)
    for column in columns:
        if column not in body:
            raise SystemExit(f"V106 schema gate failed: {table} missing {column}")

admin_pom = (root / "scx-backend/scx-ocpx-admin/pom.xml").read_text(encoding="utf-8")
engine_pom = (root / "scx-backend/scx-ocpx-engine/pom.xml").read_text(encoding="utf-8")
boot_pom = (root / "scx-backend/scx-boot/pom.xml").read_text(encoding="utf-8")
if "<artifactId>scx-ocpx-core</artifactId>" not in admin_pom or "<artifactId>scx-ocpx-engine</artifactId>" in admin_pom:
    raise SystemExit("module boundary gate failed: Admin must depend on Core, not Engine")
if "<artifactId>scx-ocpx-core</artifactId>" not in engine_pom:
    raise SystemExit("module boundary gate failed: Engine must depend on Core")
if "<artifactId>scx-ocpx-engine</artifactId>" in boot_pom:
    raise SystemExit("module boundary gate failed: Boot must not depend on Engine")

boot_application = (root / "scx-backend/scx-boot/src/main/java/com/yzytech/scx/ScxApplication.java").read_text(
    encoding="utf-8"
)
if "@SpringBootApplication" not in boot_application:
    raise SystemExit("module boundary gate failed: Boot must use standard SpringBootApplication scanning")
if "FilterType.REGEX" in boot_application or "@ComponentScan" in boot_application:
    raise SystemExit("module boundary gate failed: Boot must not use custom component-scan exclusions")

feedback = (java_root / "feedback/model/FeedbackReq.java").read_text(encoding="utf-8")
for field in ("mId", "tId", "aId", "lId"):
    if not re.search(rf"private\s+String\s+{field}\s*;", feedback):
        raise SystemExit(f"OCPX route ID gate failed: {field} is not a SCX String primary key")
for retired_field in ("chid", "mid", "dspid", "linkid"):
    if re.search(rf"private\s+String\s+{retired_field}\s*;", feedback):
        raise SystemExit(f"OCPX route ID gate failed: retired field {retired_field} remains")
for column in ("m_id", "t_id", "a_id", "l_id"):
    if schema.count(column) < 5:
        raise SystemExit(f"OCPX route ID gate failed: schema column {column} is incomplete")

redis_constants = (java_root / "shared/constant/RedisConstants.java").read_text(encoding="utf-8")
for key_prefix in (
    'ROUTE_RATIO_PREFIX = "ocpx:route:ratio:"',
    'ROUTE_ORIGINAL_COUNT_PREFIX = "ocpx:route:original-count:"',
    'ROUTE_DELIVERED_COUNT_PREFIX = "ocpx:route:delivered-count:"',
    'ROUTE_DEDUCTION_PREFIX = "ocpx:route:deduction:"',
):
    if key_prefix not in redis_constants:
        raise SystemExit(f"OCPX Redis route key gate failed: missing {key_prefix}")
deduction_service = (java_root / "callback/service/DeductionDecisionService.java").read_text(encoding="utf-8")
for required_fragment in ("DefaultRedisScript", "originalField", "passedField", "decisionKey", "factId"):
    if required_fragment not in deduction_service:
        raise SystemExit(f"V113 atomic media deduction gate failed: missing {required_fragment}")
if "adm:u:dsp:" in redis_constants:
    raise SystemExit("OCPX Redis route key gate failed: retired adm:u:dsp key space remains")

v98 = (
    root / "scx-backend/scx-boot/src/main/resources/db/migration/V98__rename_ocpx_route_ids.sql"
).read_text(encoding="utf-8")
route_tables = (
    "scx_ocpx_log_feedback",
    "scx_ocpx_log_feedback_day",
    "scx_ocpx_log_callback",
    "scx_ocpx_report_mat_temp",
    "scx_ocpx_report_mat",
)
for table in route_tables:
    if len(re.findall(rf"ALTER\s+TABLE\s+{table}\b", v98, re.IGNORECASE)) != 1:
        raise SystemExit(f"OCPX route ID gate failed: V98 must alter {table} exactly once")
for old_column, new_column in (
    ("channel_id", "m_id"),
    ("monitor_id", "t_id"),
    ("dsp_id", "a_id"),
    ("link_id", "l_id"),
):
    changes = re.findall(
        rf"CHANGE\s+COLUMN\s+{old_column}\s+{new_column}\b",
        v98,
        re.IGNORECASE,
    )
    if len(changes) != len(route_tables):
        raise SystemExit(
            f"OCPX route ID gate failed: V98 must rename {old_column} to {new_column} "
            f"in all {len(route_tables)} route tables"
        )

application_yml = (engine / "src/main/resources/application.yml").read_text(encoding="utf-8")
application_dev_yml = (engine / "src/main/resources/application-dev.yml").read_text(encoding="utf-8")
application_prod_yml = (engine / "src/main/resources/application-prod.yml").read_text(encoding="utf-8")
for fragment in ("port: 10000", "context-path: /ocpx", "active: dev", "thread: 1"):
    if fragment not in application_yml:
        raise SystemExit(f"legacy data-layer gate failed: missing application fragment {fragment}")
if "name: http://127.0.0.1:10000/ocpx" not in application_dev_yml:
    raise SystemExit("configuration gate failed: dev callback domain is missing")
if "name: https://ocpx.zhongsenyuedong.cn/ocpx" not in application_prod_yml:
    raise SystemExit("configuration gate failed: prod callback domain is missing")
for path, source in (("application-dev.yml", application_dev_yml), ("application-prod.yml", application_prod_yml)):
    if "protocols:" in source or "allowed-hosts:" in re.sub(r"^  rta-relay:\n(?:    .*\n)*", "", source, flags=re.MULTILINE):
        raise SystemExit(f"configuration gate failed: database-managed protocol mapping or allowlist remains in {path}")
    if "media-ids:" in source:
        raise SystemExit(f"configuration gate failed: retired WEIFENG media mapping found in {path}")

v115 = (
    root / "scx-backend/scx-boot/src/main/resources/db/migration/V115__database_driven_upstream_protocol_config.sql"
).read_text(encoding="utf-8")
for required_fragment in (
    "ADD COLUMN upstream_protocol VARCHAR(32) NULL",
    "CREATE TABLE scx_ocpx_protocol_allowed_host",
    "uk_ocpx_protocol_allowed_host",
    "ocpx:protocol-config:manage",
    "r.role_code='ROLE_ADMIN'",
):
    if required_fragment not in v115:
        raise SystemExit(f"V115 migration gate failed: missing {required_fragment}")
if "scx_schema_migration" in v115:
    raise SystemExit("V115 migration gate failed: retired migration ledger is referenced")

v120 = (
    root / "scx-backend/scx-boot/src/main/resources/db/migration/V120__add_gravity_upstream_protocol.sql"
).read_text(encoding="utf-8")
for required_fragment in (
    "'TEMPLATE_GET','AERIM','WEIFENG','GRAVITY'",
    "'AERIM','WEIFENG','GRAVITY'",
    "'GRAVITY','backend.gravity-engine.com'",
):
    if required_fragment not in v120:
        raise SystemExit(f"V120 GRAVITY migration gate failed: missing {required_fragment}")
if "scx_schema_migration" in v120:
    raise SystemExit("V120 GRAVITY migration gate failed: retired migration ledger is referenced")

v124 = (
    root / "scx-backend/scx-boot/src/main/resources/db/migration/V124__add_renren_video_upstream_protocol.sql"
).read_text(encoding="utf-8")
for required_fragment in (
    "'TEMPLATE_GET','AERIM','WEIFENG','GRAVITY','RENREN_VIDEO'",
    "'AERIM','WEIFENG','GRAVITY','RENREN_VIDEO'",
):
    if required_fragment not in v124:
        raise SystemExit(f"V124 RENREN_VIDEO migration gate failed: missing {required_fragment}")
if "INSERT INTO" in v124 or "userId" in v124 or "scx_schema_migration" in v124:
    raise SystemExit("V124 RENREN_VIDEO migration gate failed: migration must not seed configuration or use ledger")

v127 = (
    root / "scx-backend/scx-boot/src/main/resources/db/migration/V127__add_phoenix_upstream_protocol_and_events.sql"
).read_text(encoding="utf-8")
for required_fragment in (
    "'TEMPLATE_GET','AERIM','WEIFENG','GRAVITY','RENREN_VIDEO','PHOENIX'",
    "'AERIM','WEIFENG','GRAVITY','RENREN_VIDEO','PHOENIX'",
    "'FIRST_ACTIVATION','FIRST_ORDER','DOWNLOAD','ACTIVITY','ADDICTION'",
    "ADD COLUMN download_count BIGINT",
    "ADD COLUMN activity_count BIGINT",
    "ADD COLUMN addiction_count BIGINT",
    "ADD COLUMN download INT",
    "ADD COLUMN activity INT",
    "ADD COLUMN addiction INT",
):
    if required_fragment not in v127:
        raise SystemExit(f"V127 PHOENIX migration gate failed: missing {required_fragment}")
if "INSERT INTO" in v127 or "scx_schema_migration" in v127:
    raise SystemExit("V127 PHOENIX migration gate failed: migration must not seed configuration or use ledger")

v130 = (
    root / "scx-backend/scx-boot/src/main/resources/db/migration/V130__add_weifeng_rta_adapter.sql"
).read_text(encoding="utf-8")
for required_fragment in (
    "DROP CHECK ck_ocpx_rta_profile_adapter",
    "adapter_code IN ('MOCK','WEIFENG_RTA')",
):
    if required_fragment not in v130:
        raise SystemExit(f"V130 WEIFENG_RTA migration gate failed: missing {required_fragment}")
if any(fragment in v130 for fragment in ("CREATE TABLE", "DROP TABLE", "INSERT INTO", "scx_schema_migration")):
    raise SystemExit("V130 WEIFENG_RTA migration gate failed: migration must only widen the adapter check")

current_schema = (
    root / "scx-backend/scx-boot/src/main/resources/db/schema/scx_current_schema.sql"
).read_text(encoding="utf-8")
for required_fragment in (
    "'TEMPLATE_GET','AERIM','WEIFENG','GRAVITY'",
    "'AERIM','WEIFENG','GRAVITY'",
    "'GRAVITY','backend.gravity-engine.com'",
):
    if required_fragment not in current_schema:
        raise SystemExit(f"current schema GRAVITY gate failed: missing {required_fragment}")
for required_fragment in (
    "'TEMPLATE_GET','AERIM','WEIFENG','GRAVITY','RENREN_VIDEO'",
    "'AERIM','WEIFENG','GRAVITY','RENREN_VIDEO'",
):
    if required_fragment not in current_schema:
        raise SystemExit(f"current schema RENREN_VIDEO gate failed: missing {required_fragment}")
if re.search(r"VALUES.*RENREN_VIDEO", current_schema, re.IGNORECASE | re.DOTALL):
    raise SystemExit("current schema RENREN_VIDEO gate failed: allowed host must remain empty by default")
for required_fragment in (
    "'TEMPLATE_GET','AERIM','WEIFENG','GRAVITY','RENREN_VIDEO','PHOENIX'",
    "'AERIM','WEIFENG','GRAVITY','RENREN_VIDEO','PHOENIX'",
    "'FIRST_ACTIVATION','FIRST_ORDER','DOWNLOAD','ACTIVITY','ADDICTION'",
    "download_count BIGINT NOT NULL DEFAULT 0",
    "activity_count BIGINT NOT NULL DEFAULT 0",
    "addiction_count BIGINT NOT NULL DEFAULT 0",
    "download INT NOT NULL DEFAULT 0",
    "activity INT NOT NULL DEFAULT 0",
    "addiction INT NOT NULL DEFAULT 0",
):
    if required_fragment not in current_schema:
        raise SystemExit(f"current schema PHOENIX gate failed: missing {required_fragment}")
if re.search(r"VALUES.*PHOENIX", current_schema, re.IGNORECASE | re.DOTALL):
    raise SystemExit("current schema PHOENIX gate failed: allowed host must remain empty by default")
if "CREATE TABLE IF NOT EXISTS scx_ocpx_rta_profile" in current_schema:
    raise SystemExit("Retired RTA tables must not be created in the current schema")


aerim_delivery = (
    engine / "src/main/java/com/yzytech/scx/ocpx/feedback/delivery/aerim/AerimUpstreamDeliveryService.java"
).read_text(encoding="utf-8")
for fragment in ("OcpxPromotionLinkIndex", "promotionLink.getLinkUrl()", "feedback.getLId()"):
    if fragment not in aerim_delivery:
        raise SystemExit(f"AERIM source gate failed: missing promotion-link fragment {fragment}")
if "OcpxAdvertiserIndex" in aerim_delivery or "getBidUrl()" in aerim_delivery:
    raise SystemExit("AERIM source gate failed: advertiser-level URL routing remains")

weifeng_delivery = (
    engine
    / "src/main/java/com/yzytech/scx/ocpx/feedback/delivery/weifeng/WeifengUpstreamDeliveryService.java"
).read_text(encoding="utf-8")
for fragment in ("OcpxPromotionLinkIndex", "promotionLink.getLinkUrl()", 'expandedParameters.get("aid")',
                 'expandedParameters.get("did")'):
    if fragment not in weifeng_delivery:
        raise SystemExit(f"WEIFENG source gate failed: missing promotion-link fragment {fragment}")
for retired_fragment in ("getMediaIds()", "getBizId()", "OcpxMonitorTaskIndex"):
    if retired_fragment in weifeng_delivery:
        raise SystemExit(f"WEIFENG source gate failed: retired aid/did mapping found: {retired_fragment}")

gravity_delivery = (
    engine
    / "src/main/java/com/yzytech/scx/ocpx/feedback/delivery/gravity/GravityUpstreamDeliveryService.java"
).read_text(encoding="utf-8")
for fragment in (
    'dynamic.put("idfa", feedback.getIdfa())',
    'put(dynamic, "android_id", feedback.getAndroidid())',
    'dynamic.put("callback", buildCallbackUrl(feedback))',
    'ActionTypeEnum.ACTIVATE.name()',
):
    if fragment not in gravity_delivery:
        raise SystemExit(f"GRAVITY source gate failed: missing {fragment}")
if "iafa" in gravity_delivery:
    raise SystemExit("GRAVITY source gate failed: misspelled iafa macro found")
if "ocpx.gravity.enabled" in gravity_delivery or "GRAVITY_DISABLED" in gravity_delivery:
    raise SystemExit("GRAVITY source gate failed: retired static enablement switch found")

renren_delivery = (
    engine
    / "src/main/java/com/yzytech/scx/ocpx/feedback/delivery/renrenvideo/RenrenVideoUpstreamDeliveryService.java"
).read_text(encoding="utf-8")
for fragment in (
    "RenrenVideoTemplateContract.requireValid",
    "OcpxPromotionLinkIndex",
    'macros.put("__PASS_THROUGH__", link.getLinkExt())',
    'macros.put("__CALLBACK__", buildCallbackUrl(feedback))',
    'case 201 -> "RENREN_VIDEO_INVALID_USER_ID"',
    'case 300 -> "RENREN_VIDEO_SYSTEM_ERROR"',
):
    if fragment not in renren_delivery:
        raise SystemExit(f"RENREN_VIDEO source gate failed: missing {fragment}")
for forbidden_fragment in ("Bearer ", "bearer-token", "renren.video.enabled", "RENREN_VIDEO_ENABLED"):
    if forbidden_fragment in renren_delivery:
        raise SystemExit(f"RENREN_VIDEO source gate failed: forbidden static secret or switch {forbidden_fragment}")
renren_callback = (
    engine
    / "src/main/java/com/yzytech/scx/ocpx/callback/web/renrenvideo/RenrenVideoCallbackAdapter.java"
).read_text(encoding="utf-8")
for fragment in ("ActionTypeEnum.ACTIVATE", "ActionTypeEnum.REGISTER", "ActionTypeEnum.NEXT_STAY",
                 "ActionTypeEnum.PAYMENT", "ActionTypeEnum.FIRST_ACTIVATION", "decryptCallback"):
    if fragment not in renren_callback:
        raise SystemExit(f"RENREN_VIDEO callback gate failed: missing {fragment}")

phoenix_template = (
    core
    / "src/main/java/com/yzytech/scx/ocpx/protocol/PhoenixTemplateContract.java"
).read_text(encoding="utf-8")
for fragment in (
    'HOST = "api.feather.ifeng.com"',
    'CLICK_PATH = "/d/cli"',
    'IMPRESSION_PATH = "/d/imp"',
    "FIXED_PARAMETER_NAMES",
    '"__OAIDMD5__"',
    "PHOENIX_TEMPLATE_UNKNOWN_PARAMETER",
    "PHOENIX_TEMPLATE_REDIRECT_FORBIDDEN",
):
    if fragment not in phoenix_template:
        raise SystemExit(f"PHOENIX template gate failed: missing {fragment}")
phoenix_delivery = (
    engine
    / "src/main/java/com/yzytech/scx/ocpx/feedback/delivery/phoenix/PhoenixUpstreamDeliveryService.java"
).read_text(encoding="utf-8")
for fragment in (
    "PhoenixTemplateContract.requireValid",
    "PhoenixEndpointPolicy",
    "EventTypeEnum.CLICK",
    "EventTypeEnum.IMP",
    'macros.put("__REQID__", feedback.getReqId())',
    'macros.put("__OAIDMD5__", oaidMd5)',
    "encryptCallback",
    "case 10001",
    "case 10007",
    "Integer.valueOf(0)",
):
    if fragment not in phoenix_delivery:
        raise SystemExit(f"PHOENIX delivery gate failed: missing {fragment}")
if 'PhoenixTemplateContract.IMPRESSION_PATH' not in phoenix_delivery:
    raise SystemExit("PHOENIX delivery gate failed: impression path missing")
phoenix_callback = (
    engine
    / "src/main/java/com/yzytech/scx/ocpx/callback/web/phoenix/PhoenixCallbackAdapter.java"
).read_text(encoding="utf-8")
for fragment in (
    "ActionTypeEnum.ACTIVATE",
    "ActionTypeEnum.EVOKE",
    "ActionTypeEnum.REGISTER",
    "ActionTypeEnum.PAYMENT",
    "ActionTypeEnum.NEXT_STAY",
    "ActionTypeEnum.FIRST_ORDER",
    "ActionTypeEnum.DOWNLOAD",
    "ActionTypeEnum.ACTIVITY",
    "ActionTypeEnum.ADDICTION",
    "decryptCallback",
    "OcpxUpstreamProtocol.PHOENIX",
    "PhoenixTemplateContract.requireValid",
):
    if fragment not in phoenix_callback:
        raise SystemExit(f"PHOENIX callback gate failed: missing {fragment}")
for path, source in (("application-dev.yml", application_dev_yml), ("application-prod.yml", application_prod_yml)):
    if re.search(r"(?m)^\s+gravity:\s*$", source):
        raise SystemExit(f"GRAVITY configuration gate failed: retired static switch found in {path}")
    if "advertisers: {}" in source:
        raise SystemExit(f"credential configuration gate failed: redundant empty advertiser map found in {path}")
for path, source in (
    ("application.yml", application_yml),
    ("application-dev.yml", application_dev_yml),
    ("application-prod.yml", application_prod_yml),
):
    if re.search(r"\$\{(?:SCX|ROCKETMQ|XXL_JOB|XXL_SSO)_", source):
        raise SystemExit(f"configuration gate failed: runtime environment placeholder found in {path}")
    if ": KIMI" in source or "\n    media:" in source:
        raise SystemExit(f"configuration gate failed: retired KIMI/MI configuration found in {path}")

nginx_template = (
    root / "scripts/bt-deploy/templates/nginx-scx-ocpx-engine-rewrite.conf"
).read_text(encoding="utf-8")
if "location = /rta {" in nginx_template or "^/ocpx/rta/(requests|callbacks/" not in nginx_template:
    raise SystemExit("RTA must expose only the new asynchronous endpoints")

weifeng_location = "location = /ocpx/weifeng/callback {"
if nginx_template.count(weifeng_location) != 1:
    raise SystemExit("Nginx gate failed: WEIFENG callback must have one exact location")
weifeng_prefix = (
    weifeng_location
    + "\n    limit_except GET { deny all; }"
    + "\n    proxy_pass http://127.0.0.1:10000;"
)
if weifeng_prefix not in nginx_template:
    raise SystemExit("Nginx gate failed: WEIFENG callback must be GET-only and proxy to Engine")

gravity_location = "location = /ocpx/gravity/callback {"
gravity_prefix = (
    gravity_location
    + "\n    limit_except GET { deny all; }"
    + "\n    proxy_pass http://127.0.0.1:10000;"
)
if nginx_template.count(gravity_location) != 1 or gravity_prefix not in nginx_template:
    raise SystemExit("Nginx gate failed: GRAVITY callback must be GET-only and proxy to Engine")

renren_location = "location = /ocpx/renren-video/callback {"
renren_prefix = (
    renren_location
    + "\n    limit_except GET { deny all; }"
    + "\n    proxy_pass http://127.0.0.1:10000;"
)
if nginx_template.count(renren_location) != 1 or renren_prefix not in nginx_template:
    raise SystemExit("Nginx gate failed: RENREN_VIDEO callback must be GET-only and proxy to Engine")

phoenix_location = "location = /ocpx/phoenix/callback {"
phoenix_block = """location = /ocpx/phoenix/callback {
    limit_except GET { deny all; }
    proxy_pass http://127.0.0.1:10000;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 10s;
    proxy_send_timeout 30s;
    proxy_read_timeout 30s;
    client_max_body_size 1m;
}"""
if nginx_template.count(phoenix_location) != 1 or phoenix_block not in nginx_template:
    raise SystemExit("Nginx gate failed: PHOENIX callback must be one exact GET-only production route")
if "location = /d/cli" in nginx_template or "location ^~ /d/cli" in nginx_template or "location = /d/imp" in nginx_template:
    raise SystemExit("Nginx gate failed: Phoenix outbound /d/cli and /d/imp must not be inbound locations")

test_callback_location = "location = /ocpx/test-callback {"
for template_path in (
    "scripts/bt-deploy/templates/nginx-scx-ocpx-engine-rewrite.conf",
    "scripts/bt-deploy/templates/nginx-scx-ocpx-engine-ip-uat-rewrite.conf",
):
    template = (root / template_path).read_text(encoding="utf-8")
    if template.count(test_callback_location) != 1:
        raise SystemExit(f"Nginx gate failed: {template_path} must expose one exact test callback location")
    expected_proxy = (
        test_callback_location
        + "\n    limit_except GET { deny all; }"
        + "\n    proxy_pass http://127.0.0.1:10000;"
    )
    if expected_proxy not in template:
        raise SystemExit(f"Nginx gate failed: {template_path} test callback must be GET-only and proxy to Engine")
    if template.count(gravity_location) != 1 or gravity_prefix not in template:
        raise SystemExit(f"Nginx gate failed: {template_path} GRAVITY callback must be GET-only and proxy to Engine")
    if template.count(renren_location) != 1 or renren_prefix not in template:
        raise SystemExit(f"Nginx gate failed: {template_path} RENREN_VIDEO callback must be GET-only and proxy to Engine")
    if "ip-uat" in template_path and phoenix_location in template:
        raise SystemExit(f"Nginx gate failed: {template_path} must not expose the production PHOENIX route")

frontend_nginx = (root / "scx-fronted/scripts/deploy/scx-fronted.conf").read_text(encoding="utf-8")
frontend_test_proxy = (
    test_callback_location
    + "\n        limit_except GET { deny all; }"
    + "\n        proxy_pass http://scx-ocpx-engine:10000;"
)
if frontend_nginx.count(test_callback_location) != 1 or frontend_test_proxy not in frontend_nginx:
    raise SystemExit("Nginx gate failed: container frontend must proxy one GET-only test callback route")
frontend_gravity_proxy = (
    gravity_location
    + "\n        limit_except GET { deny all; }"
    + "\n        proxy_pass http://scx-ocpx-engine:10000;"
)
if frontend_nginx.count(gravity_location) != 1 or frontend_gravity_proxy not in frontend_nginx:
    raise SystemExit("Nginx gate failed: container frontend must proxy one GET-only GRAVITY callback route")
frontend_renren_proxy = (
    renren_location
    + "\n        limit_except GET { deny all; }"
    + "\n        proxy_pass http://scx-ocpx-engine:10000;"
)
if frontend_nginx.count(renren_location) != 1 or frontend_renren_proxy not in frontend_nginx:
    raise SystemExit("Nginx gate failed: container frontend must proxy one GET-only RENREN_VIDEO callback route")
if phoenix_location in frontend_nginx or "location = /d/cli" in frontend_nginx or "location = /d/imp" in frontend_nginx:
    raise SystemExit("Nginx gate failed: container frontend must not expose the PHOENIX route")

entrypoint = (java_root / "ScxOcpxEngineApplication.java").read_text(encoding="utf-8")
for mapper_package in ("com.yzytech.scx.ocpx.configuration.mapper", "com.yzytech.scx.ocpx.report.mapper"):
    if mapper_package not in entrypoint:
        raise SystemExit(f"feature-package gate failed: MapperScan is missing {mapper_package}")
if "com.yzytech.scx.ocpx.dao.mapper" in entrypoint:
    raise SystemExit("feature-package gate failed: retired MapperScan remains")

# 当前数据面公开入口保留 GET；V130 允许受网关白名单保护的 RTA POST 入口。
for file in java_root.rglob("*Controller.java"):
    source = file.read_text(encoding="utf-8")
    mutating = ["@PostMapping", "@PutMapping", "@PatchMapping", "@DeleteMapping"]
    if any(annotation in source for annotation in mutating) and file.name != "RelayController.java":
        raise SystemExit(f"GET-only gate failed: mutating mapping found in {file}")

rta_controller = (java_root / "rta/relay/RelayController.java").read_text(encoding="utf-8")
if '@RequestMapping("/rta")' not in rta_controller or not re.search(r'value\s*=\s*"/requests"', rta_controller):
    raise SystemExit("RTA gate failed: the internal RTA POST entry is missing")
if any(annotation in rta_controller for annotation in ("@PutMapping", "@PatchMapping", "@DeleteMapping")):
    raise SystemExit("RTA gate failed: the Engine RTA controller exposes an unsupported mutating route")

print("OCPX management and generic data-layer gate passed")
PY
