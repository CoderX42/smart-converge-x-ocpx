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
    "feedback-req-v2.schema.json",
    "callback-req-v2.schema.json",
    "scx-ocpx-v2.postman_collection.json",
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
server_urls = {server.get("url") for server in openapi.get("servers", [])}
assert "https://{domain}/ocpx" in server_urls
assert "http://localhost:10000/ocpx" in server_urls
assert all("/rtb" not in (url or "") for url in server_urls), "retired /rtb base path must not return"
assert "/open-api/ocpx/v2/media/{mediaCode}/feedback" in paths
assert "/open-api/ocpx/v2/advertisers/{advertiserCode}/callback" in paths
assert "/c/{taskCode}" in paths
assert all("/v1/" not in path for path in paths), "v1 data-plane paths must not return"
assert all(not path.startswith("/open-api/events/v1") for path in paths), "historical event API must not be active"
webhooks = openapi.get("webhooks", {})
assert "downstreamCallback" in webhooks, "standard downstream callback webhook must be documented"
callback_operation = webhooks["downstreamCallback"].get("post", {})
assert callback_operation.get("requestBody", {}).get("content", {}).get("application/json", {}).get("schema", {}).get("$ref") == "./callback-req-v2.schema.json"

for name in ("feedback-req-v2.schema.json", "callback-req-v2.schema.json"):
    with (root / name).open(encoding="utf-8") as handle:
        schema = json.load(handle)
    jsonschema.Draft202012Validator.check_schema(schema)

with (root / "scx-ocpx-v2.postman_collection.json").open(encoding="utf-8") as handle:
    postman = json.load(handle)
postman_variables = {item.get("key"): item.get("value") for item in postman.get("variable", [])}
assert postman_variables.get("baseUrl") == "http://localhost:10000/ocpx"
assert "/rtb" not in json.dumps(postman, ensure_ascii=False), "retired /rtb path must not return"
item_names = {item.get("name") for item in postman.get("item", [])}
assert {"媒体 FeedbackReqV2", "广告主 CallbackV2", "公共监测地址"} <= item_names

print("OCPX contracts validated")
PY
