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
    "scx_ocpx_advertiser_account",
    "scx_ocpx_creative_promotion_link",
    "scx_ocpx_billing_config_audit",
    "scx_ocpx_event_fact",
    "scx_ocpx_billing_entry",
    "scx_ocpx_hourly_metric",
    "scx_ocpx_flow_log",
    "scx_ocpx_log_feedback",
    "scx_ocpx_log_feedback_day",
    "scx_ocpx_log_callback",
    "scx_ocpx_report_mat_temp",
    "scx_ocpx_report_mat",
}
actual = re.findall(r"CREATE TABLE IF NOT EXISTS\s+(scx_ocpx_[a-z0-9_]+)", schema, re.IGNORECASE)
if set(actual) != expected_business | expected_data or len(actual) != 18:
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
        "required": ("advertiser_name", "account_user_id varchar(64)", "credential_config_key", "bid_url", "macro_params"),
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
        "required": ("promotion_link_id", "media_id", "ratio", "is_delete"),
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
    "callback/model/CallbackReq.java",
    "callback/web/UpstreamCallbackController.java",
	"callback/web/aerim/AerimCallbackController.java",
    "callback/web/weifeng/WeifengCallbackController.java",
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
    "configuration/task/ResetRedisKeyTask.java",
    "configuration/credential/PartnerCredentialProperties.java",
    "configuration/credential/PartnerCredentialResolver.java",
    "protocol/registry/AdvertiserProtocolRegistry.java",
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
    "advertiserCode", "mediaCode", "adapterCode", "credentialVersion",
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
):
    if key_prefix not in redis_constants:
        raise SystemExit(f"OCPX Redis route key gate failed: missing {key_prefix}")
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
    if "ad.adapi.cn" not in source:
        raise SystemExit(f"configuration gate failed: WEIFENG host allowlist is missing in {path}")
    if "media-ids:" in source:
        raise SystemExit(f"configuration gate failed: retired WEIFENG media mapping found in {path}")

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

entrypoint = (java_root / "ScxOcpxEngineApplication.java").read_text(encoding="utf-8")
for mapper_package in ("com.yzytech.scx.ocpx.configuration.mapper", "com.yzytech.scx.ocpx.report.mapper"):
    if mapper_package not in entrypoint:
        raise SystemExit(f"feature-package gate failed: MapperScan is missing {mapper_package}")
if "com.yzytech.scx.ocpx.dao.mapper" in entrypoint:
    raise SystemExit("feature-package gate failed: retired MapperScan remains")

# 当前数据面公开入口全部是 GET；任何写方法映射都必须先经过新协议评审。
for file in java_root.rglob("*Controller.java"):
    source = file.read_text(encoding="utf-8")
    mutating = ["@PostMapping", "@PutMapping", "@PatchMapping", "@DeleteMapping"]
    if any(annotation in source for annotation in mutating):
        raise SystemExit(f"GET-only gate failed: mutating mapping found in {file}")

rta_pattern = re.compile(
    r"(?:\b(?:RTA|rta)\b|rta_mode|rtaMode|handleRta|\bRta[A-Z]|_rta|rta_)",
)
for relative in (
    "scx-backend/scx-ocpx-core/src",
    "scx-backend/scx-ocpx-admin/src",
    "scx-backend/scx-ocpx-engine/src",
    "scx-backend/scx-boot/src/main/resources/db/schema/scx_current_schema.sql",
    "scx-fronted/apps/web-ele/src",
    "scx-fronted/apps/web-ele/e2e",
    "docs/接口文档",
    "docs/广告平台设计/contracts",
    "docs/广告平台设计/ocpx-aerim-模块实现规格",
):
    path = root / relative
    files = path.rglob("*") if path.is_dir() else [path]
    for file in files:
        if file.is_file() and file.suffix in {".java", ".json", ".md", ".sql", ".ts", ".xml", ".yml", ".yaml"}:
            if rta_pattern.search(file.read_text(encoding="utf-8")):
                raise SystemExit(f"RTA retirement gate failed: active reference remains in {file}")

print("OCPX management and generic data-layer gate passed")
PY
