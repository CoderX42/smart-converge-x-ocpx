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
    "aerim-callback-req-v1.schema.json",
    "scx-ocpx-v2.postman_collection.json",
]
missing = [name for name in required if not (root / name).is_file()]
if missing:
    raise SystemExit(f"missing contract files: {', '.join(missing)}")
with (root / "openapi.yaml").open(encoding="utf-8") as handle:
    openapi = yaml.safe_load(handle)
assert openapi.get("openapi") == "3.1.0", "OpenAPI must be 3.1.0"
paths = openapi.get("paths", {})
server_urls = {server.get("url") for server in openapi.get("servers", [])}
assert "https://{domain}/ocpx" in server_urls
assert "http://localhost:10000/ocpx" in server_urls
assert all("/rtb" not in (url or "") for url in server_urls), "retired /rtb base path must not return"
assert set(paths) == {"/feedback", "/callback", "/aerim/callback"}
feedback_parameters = {item["name"] for item in paths["/feedback"]["get"]["parameters"]}
assert {"mId", "tId", "aId", "lId"} <= feedback_parameters
assert not ({"chid", "mid", "dspid", "linkid"} & feedback_parameters)
assert "400" in paths["/feedback"]["get"]["responses"]
assert all(set(operations) <= {"get", "parameters"} for operations in paths.values())
assert all("requestBody" not in operation for operations in paths.values() for operation in operations.values() if isinstance(operation, dict))
assert all("/v1/" not in path for path in paths), "v1 data-plane paths must not return"
assert all(not path.startswith("/open-api/events/v1") for path in paths), "historical event API must not be active"
webhooks = openapi.get("webhooks", {})
assert "downstreamConversionGet" in webhooks, "standard downstream callback webhook must be documented"
assert set(webhooks["downstreamConversionGet"]) == {"get"}
assert "requestBody" not in webhooks["downstreamConversionGet"]["get"]

for name in ("feedback-req-v2.schema.json", "callback-req-v2.schema.json", "aerim-callback-req-v1.schema.json"):
    with (root / name).open(encoding="utf-8") as handle:
        schema = json.load(handle)
    jsonschema.Draft202012Validator.check_schema(schema)
    if name == "feedback-req-v2.schema.json":
        assert set(schema["required"]) >= {"mId", "tId", "aId", "lId"}
        assert not ({"chid", "mid", "dspid", "linkid"} & set(schema["properties"]))
        assert schema.get("additionalProperties") is False

with (root / "scx-ocpx-v2.postman_collection.json").open(encoding="utf-8") as handle:
    postman = json.load(handle)
postman_variables = {item.get("key"): item.get("value") for item in postman.get("variable", [])}
assert postman_variables.get("baseUrl") == "http://localhost:10000"
assert "/rtb" not in json.dumps(postman, ensure_ascii=False), "retired /rtb path must not return"
item_names = {item.get("name") for item in postman.get("item", [])}
assert {"媒体 Feedback GET", "广告主 Callback GET", "AERIM Callback GET"} <= item_names
assert all(item.get("request", {}).get("method") == "GET" for item in postman.get("item", []))
serialized = json.dumps(postman, ensure_ascii=False)
assert "/ocpx/feedback" in serialized
assert "/ocpx/callback" in serialized
assert "/ocpx/aerim/callback" in serialized
assert "currency_amount=123.45" in serialized
assert "mId={{mediaId}}" in serialized
assert "tId={{monitorTaskId}}" in serialized
assert "aId={{advertiserId}}" in serialized
assert "lId={{promotionLinkId}}" in serialized
assert not any(name in serialized for name in ("chid=", "mid=", "dspid=", "linkid="))
assert "/ocpx/general/feedback" not in serialized
assert "/ocpx/union/callback" not in serialized

print("OCPX contracts validated")
PY
