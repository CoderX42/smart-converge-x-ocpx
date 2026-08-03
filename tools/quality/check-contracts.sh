#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONTRACT_DIR="${ROOT_DIR}/docs/广告平台设计/contracts"

python3 - "${CONTRACT_DIR}" <<'PY'
import json
import pathlib
import sys

import jsonschema
import yaml

root = pathlib.Path(sys.argv[1])
required = [
    "openapi.yaml",
    "asyncapi.yaml",
    "feedback-req-v1.schema.json",
    "callback-req-v1.schema.json",
    "scx-ocpx-v1.postman_collection.json",
]
missing = [name for name in required if not (root / name).is_file()]
if missing:
    raise SystemExit(f"missing contract files: {', '.join(missing)}")
uat_scripts = [root.parent / "uat" / name for name in ("sign_request.py", "verify_request.py")]
missing_scripts = [str(path) for path in uat_scripts if not path.is_file()]
if missing_scripts:
    raise SystemExit(f"missing UAT scripts: {', '.join(missing_scripts)}")
for path in uat_scripts:
    compile(path.read_text(encoding="utf-8"), str(path), "exec")

with (root / "openapi.yaml").open(encoding="utf-8") as handle:
    openapi = yaml.safe_load(handle)
assert openapi.get("openapi") == "3.1.0", "OpenAPI must be 3.1.0"
paths = openapi.get("paths", {})
assert "/open-api/ocpx/v1/channels/{channelCode}/feedback" in paths
assert "/open-api/ocpx/v1/upstreams/coco/callback" in paths
assert all(not path.startswith("/open-api/events/v1") for path in paths), "historical event API must not be active"
webhooks = openapi.get("webhooks", {})
assert "downstreamCallback" in webhooks, "standard downstream callback webhook must be documented"
callback_operation = webhooks["downstreamCallback"].get("post", {})
assert callback_operation.get("requestBody", {}).get("content", {}).get("application/json", {}).get("schema", {}).get("$ref") == "./callback-req-v1.schema.json"

with (root / "asyncapi.yaml").open(encoding="utf-8") as handle:
    asyncapi = yaml.safe_load(handle)
assert asyncapi.get("asyncapi") == "3.0.0", "AsyncAPI must be 3.0.0"
assert "feedbackPartition" in asyncapi.get("channels", {})

for name in ("feedback-req-v1.schema.json", "callback-req-v1.schema.json"):
    with (root / name).open(encoding="utf-8") as handle:
        schema = json.load(handle)
    jsonschema.Draft202012Validator.check_schema(schema)

with (root / "scx-ocpx-v1.postman_collection.json").open(encoding="utf-8") as handle:
    postman = json.load(handle)
assert len(postman.get("item", [])) == 2, "Postman collection must cover feedback and Coco callback"

print("OCPX contracts validated")
PY
