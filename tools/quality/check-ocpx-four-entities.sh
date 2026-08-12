#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

python3 - "${ROOT_DIR}" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
schema = (root / "scx-backend/scx-boot/src/main/resources/db/schema/scx_current_schema.sql").read_text(encoding="utf-8")
business = re.findall(
    r"CREATE TABLE IF NOT EXISTS\s+(scx_ocpx_[a-z0-9_]+).*?COMMENT='BUSINESS:",
    schema,
    re.IGNORECASE | re.DOTALL,
)
expected_business = {
    "scx_ocpx_advertiser",
    "scx_ocpx_promotion_link",
    "scx_ocpx_media",
    "scx_ocpx_monitor_task",
}
if set(business) != expected_business or len(business) != 4:
    raise SystemExit(f"four-entity gate failed: BUSINESS tables are {business!r}")

expected_data = {
    "scx_ocpx_flow_log",
    "scx_ocpx_log_feedback",
    "scx_ocpx_log_feedback_day",
    "scx_ocpx_log_callback",
    "scx_ocpx_report_mat_temp",
    "scx_ocpx_report_mat",
}
actual = re.findall(r"CREATE TABLE IF NOT EXISTS\s+(scx_ocpx_[a-z0-9_]+)", schema, re.IGNORECASE)
if set(actual) != expected_business | expected_data or len(actual) != 10:
    raise SystemExit(f"four-entity gate failed: OCPX tables are {actual!r}")
if "scx_ocpx_report_hourly" in schema:
    raise SystemExit("legacy data-layer gate failed: obsolete hourly table remains")

retired = re.compile(
    r"scx_ocpx_(channel|app_link|monitor_link|deduction_rule|hourly_report|product|media_account|media_credential|task)(?:\W|$)"
)
for relative in (
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
required = {
    "ScxOcpxEngineApplication.java",
    "bean/FeedbackReq.java",
    "bean/CallbackReq.java",
    "bean/LogData.java",
    "controller/general/GeneralController.java",
    "controller/general/GeneralParamAdapter.java",
    "controller/general/GeneralServiceImpl.java",
    "manager/union/UnionController.java",
    "manager/union/UnionParamAdapter.java",
    "manager/union/UnionServiceImpl.java",
    "service/impl/FeedbackServiceImpl.java",
    "service/impl/CallbackServiceImpl.java",
    "service/helper/RedisHelper.java",
    "task/StorageTask.java",
    "task/ResetRedisKeyTask.java",
    "entity/OcpxAdvertiser.java",
    "entity/OcpxPromotionLink.java",
    "entity/OcpxMedia.java",
    "entity/OcpxMonitorTask.java",
}
actual_sources = {p.relative_to(java_root).as_posix() for p in java_root.rglob("*.java")}
missing = required - actual_sources
if missing:
    raise SystemExit(f"legacy data-layer gate failed: missing sources {sorted(missing)!r}")

mapped = []
for file in java_root.rglob("*.java"):
    if "@TableName(" in file.read_text(encoding="utf-8"):
        mapped.append(file.relative_to(java_root).as_posix())
expected_mapped = {
    "entity/OcpxAdvertiser.java",
    "entity/OcpxPromotionLink.java",
    "entity/OcpxMedia.java",
    "entity/OcpxMonitorTask.java",
}
if set(mapped) != expected_mapped:
    raise SystemExit(f"four-entity gate failed: Engine entities are {sorted(mapped)!r}")

feedback = (java_root / "bean/FeedbackReq.java").read_text(encoding="utf-8")
for field in ("chid", "mid", "dspid", "linkid"):
    if not re.search(rf"private\s+String\s+{field}\s*;", feedback):
        raise SystemExit(f"legacy data-layer gate failed: {field} is not a SCX String primary key")

application_yml = (engine / "src/main/resources/application.yml").read_text(encoding="utf-8")
for fragment in ("port: 10000", "context-path: /ocpx", "thread: 1", "name: http://127.0.0.1:10000/ocpx"):
    if fragment not in application_yml:
        raise SystemExit(f"legacy data-layer gate failed: missing application fragment {fragment}")
if re.search(r"\$\{(?:SCX|ROCKETMQ|XXL_JOB|XXL_SSO)_", application_yml):
    raise SystemExit("configuration gate failed: runtime environment placeholder found")

for file in java_root.rglob("*Controller.java"):
    source = file.read_text(encoding="utf-8")
    if any(annotation in source for annotation in ("@PostMapping", "@PutMapping", "@PatchMapping", "@DeleteMapping")):
        raise SystemExit(f"GET-only gate failed: mutating mapping found in {file}")

print("OCPX four-entity and legacy-compatible data-layer gate passed")
PY
