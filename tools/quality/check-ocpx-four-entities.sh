#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

SEARCH_PATHS=(
  "${ROOT_DIR}/scx-backend/scx-ocpx-engine/src"
  "${ROOT_DIR}/scx-backend/scx-ocpx-admin/src"
  "${ROOT_DIR}/scx-fronted/apps/web-ele/src"
  "${ROOT_DIR}/docs/广告平台设计/contracts"
  "${ROOT_DIR}/tools/performance"
  "${ROOT_DIR}/scx-backend/scx-boot/src/main/resources/db/schema/scx_current_schema.sql"
)

FORBIDDEN_PATTERN='FeedbackReqV1|CallbackReqV1|\bRouteSnapshot\b|RtaDecision|\brta\b|\b(product_id|app_link_id|channel_id|media_account_id|task_id|monitor_id)\b|scx_ocpx_(channel|integration_document|advertiser_credential_revision|advertiser_change_audit|app_link|app_link_change_audit|monitor_link|deduction_rule|hourly_report|adapter|rta_decision_log|monitor_event_binding|product_logo_object|product|product_event_policy|product_change_audit|media_account|media_credential|media_credential_revision|media_account_owner|media_account_change_audit|task|task_change_audit|task_event_binding|event_change_audit|business_audit|event_definition|media_event_mapping|monitor_task_event_binding|route_|protocol_snapshot|callback_ref|delivery_|deduction_decision|billing_entry|click_dedup|event_daily_quota|ingress_|hourly_fact|report_export|runtime_)|scx_api_debug_|adx:|ocpx:(product|app-link|channel|media-account|task|monitor|deduction|event|runtime|api-debug|scope|account):|ocpx:monitor-task:publish|ocpx:report:export|ocpx:advertiser:grant'

if rg -n --pcre2 --glob '!**/results/**' "${FORBIDDEN_PATTERN}" "${SEARCH_PATHS[@]}"; then
  echo "four-entity gate failed: active source still references a retired OCPX model" >&2
  exit 1
fi

python3 - "${ROOT_DIR}" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
schema = (root / "scx-backend/scx-boot/src/main/resources/db/schema/scx_current_schema.sql").read_text(encoding="utf-8")
business_tables = re.findall(
    r"CREATE TABLE IF NOT EXISTS\s+(scx_ocpx_[a-z0-9_]+).*?COMMENT='BUSINESS:",
    schema,
    re.IGNORECASE | re.DOTALL,
)
expected = {
    "scx_ocpx_advertiser",
    "scx_ocpx_promotion_link",
    "scx_ocpx_media",
    "scx_ocpx_monitor_task",
}
if set(business_tables) != expected or len(business_tables) != 4:
    raise SystemExit(f"four-entity gate failed: BUSINESS tables are {business_tables!r}")

required_schema = [
    "FOREIGN KEY (advertiser_id,promotion_link_id)",
    "CREATE TABLE IF NOT EXISTS scx_ocpx_flow_log",
    "'FEEDBACK','CALLBACK'",
    "'SUCCESS','FAILED'",
]
compact = re.sub(r"\s+", "", schema)
for fragment in required_schema:
    if re.sub(r"\s+", "", fragment) not in compact:
        raise SystemExit(f"four-entity gate failed: schema fragment missing: {fragment}")

v96 = (root / "scx-backend/scx-boot/src/main/resources/db/migration/V96__reset_ocpx_ad_business_to_four_entities.sql").read_text(encoding="utf-8")
for fragment in (
    "@SCX_CONFIRM_RESET_OCPX",
    "DATABASE()='smart_converge_x'",
    "four-entity reset is already applied",
    "@v96_sys_user_count",
    "@v96_sys_role_count",
    "source ../schema/scx_current_schema.sql",
):
    if fragment not in v96:
        raise SystemExit(f"four-entity gate failed: V96 guard missing: {fragment}")

ocpx_tables = re.findall(r"CREATE TABLE IF NOT EXISTS\s+(scx_ocpx_[a-z0-9_]+)", schema, re.IGNORECASE)
expected_ocpx_tables = expected | {"scx_ocpx_flow_log"}
if set(ocpx_tables) != expected_ocpx_tables or len(ocpx_tables) != 5:
    raise SystemExit(f"four-entity gate failed: OCPX tables are {ocpx_tables!r}")

engine = root / "scx-backend/scx-ocpx-engine"
java_root = engine / "src/main/java/com/yzytech/scx/ocpx"
expected_java = {
    "OcpxAdvertiser.java",
    "OcpxPromotionLink.java",
    "OcpxMedia.java",
    "OcpxMonitorTask.java",
    "ScxOcpxEngineApplication.java",
}
actual_java = {path.name for path in java_root.rglob("*.java")}
if actual_java != expected_java:
    raise SystemExit(f"four-entity gate failed: engine Java sources are {sorted(actual_java)!r}")

entity_java = expected_java - {"ScxOcpxEngineApplication.java"}
for path in java_root.rglob("*.java"):
    source = path.read_text(encoding="utf-8")
    is_valid_entity = path.name in entity_java and "@TableName(" in source
    is_valid_application = path.name == "ScxOcpxEngineApplication.java" and "@SpringBootApplication" in source
    if path.parent != java_root or "package com.yzytech.scx.ocpx;" not in source or not (is_valid_entity or is_valid_application):
        raise SystemExit(f"four-entity gate failed: invalid entity source: {path}")

expected_support_files = {
    "src/test": {"ScxOcpxEngineApplicationTest.java"},
    "src/main/resources": {"application.yml"},
}
for relative, expected_files in expected_support_files.items():
    directory = engine / relative
    files = {path.name for path in directory.rglob("*") if path.is_file()} if directory.exists() else set()
    if files != expected_files:
        raise SystemExit(f"four-entity gate failed: unexpected {relative} files: {sorted(files)!r}")

application_yml = (engine / "src/main/resources/application.yml").read_text(encoding="utf-8")
if not re.search(r"(?m)^\s*port:\s*10000\s*$", application_yml):
    raise SystemExit("four-entity gate failed: engine port must be 10000")
if not re.search(r"(?m)^\s*base-path:\s*/ocpx\s*$", application_yml):
    raise SystemExit("four-entity gate failed: engine context path must be /ocpx")

print("OCPX four-entity static gate passed")
PY
