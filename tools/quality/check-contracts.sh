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
    "weifeng-callback-req-v1.schema.json",
    "gravity-callback-req-v1.schema.json",
    "scx-ocpx-v2.postman_collection.json",
    "management-v107.md",
    "management-v108.md",
    "management-v110.md",
    "management-v111.md",
    "management-v112.md",
    "management-v113.md",
    "management-v114.md",
    "management-v115.md",
    "management-v116.md",
    "management-v117.md",
    "management-v118.md",
    "management-v119.md",
    "management-v120.md",
    "management-v122.md",
    "creative-save-v111.schema.json",
    "monitor-task-billing-v111.schema.json",
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
data_plane_paths = {"/feedback", "/callback", "/aerim/callback", "/weifeng/callback", "/gravity/callback", "/test-callback"}
management_path = "/advertisers/account-role/permissions"
recharge_path = "/advertisers/{id}/recharge"
deduction_path = "/advertisers/{id}/deduct"
dashboard_path = "/dashboard/summary"
v111_paths = {
    "/creatives", "/creatives/assets", "/creatives/{id}",
    "/creatives/{id}/promotion-links", "/monitor-tasks",
    "/monitor-tasks/{id}", "/billing-entries",
}
v112_paths = {
    "/monitor-tasks/{id}/test-links:generate",
    "/monitor-tasks/{id}/test-links:execute",
    "/monitor-tasks/{id}/test-results/{requestId}",
}
v113_paths = {
    "/deduction-rules", "/deduction-rules/{id}",
    "/reports/summary", "/reports/summary:export",
}
v114_paths = {
    "/monitor-tasks/{id}/integration/feedback-requests",
    "/monitor-tasks/{id}/integration/callbacks:send",
}
v115_paths = {"/protocol-configs", "/protocol-configs/{protocol}/allowed-hosts"}
v116_paths = {"/flow-logs/{id}/request-detail"}
v118_paths = {"/callback-records"}
v119_paths = {"/media-macros"}
assert set(paths) == data_plane_paths | {management_path, recharge_path, deduction_path, dashboard_path} | v111_paths | v112_paths | v113_paths | v114_paths | v115_paths | v116_paths | v118_paths | v119_paths
feedback_parameters = {item["name"] for item in paths["/feedback"]["get"]["parameters"]}
assert {"mId", "tId", "aId", "lId"} <= feedback_parameters
assert not ({"chid", "mid", "dspid", "linkid"} & feedback_parameters)
assert "400" in paths["/feedback"]["get"]["responses"]
assert all(set(paths[path]) <= {"get", "parameters"} for path in data_plane_paths)
assert all("requestBody" not in operation for path in data_plane_paths for operation in paths[path].values() if isinstance(operation, dict))
management = paths[management_path]
assert {"get", "put"} <= set(management)
assert management["put"]["requestBody"]["content"]["application/json"]["schema"]["$ref"].endswith("AdvertiserRolePermissionsUpdate")
assert management["get"]["security"] == [{"saToken": []}]
assert management["put"]["security"] == [{"saToken": []}]
recharge = paths[recharge_path]["post"]
assert recharge["security"] == [{"saToken": []}]
assert recharge["requestBody"]["content"]["application/json"]["schema"]["$ref"].endswith("AdvertiserRechargeRequest")
deduction = paths[deduction_path]["post"]
assert deduction["security"] == [{"saToken": []}]
assert deduction["requestBody"]["content"]["application/json"]["schema"]["$ref"].endswith("AdvertiserDeductionRequest")
dashboard = paths[dashboard_path]["get"]
assert dashboard["security"] == [{"saToken": []}]
dashboard_parameters = {item["name"]: item for item in dashboard["parameters"]}
assert dashboard_parameters["startDate"]["required"] is False
assert dashboard_parameters["endDate"]["required"] is False
assert {"get", "post"} <= set(paths["/creatives"])
assert {"get", "post"} <= set(paths["/creatives/assets"])
assert {"get", "put", "delete"} <= set(paths["/creatives/{id}"])
assert {"get", "put"} <= set(paths["/creatives/{id}/promotion-links"])
assert set(paths["/monitor-tasks"]) >= {"post"}
assert set(paths["/monitor-tasks/{id}"]) >= {"put"}
assert set(paths["/billing-entries"]) >= {"get"}
assert set(paths["/monitor-tasks/{id}/test-links:generate"]) == {"post", "servers"}
assert set(paths["/monitor-tasks/{id}/test-links:execute"]) == {"post", "servers"}
assert set(paths["/monitor-tasks/{id}/test-results/{requestId}"]) == {"get", "servers"}
assert set(paths["/monitor-tasks/{id}/integration/feedback-requests"]) == {"get", "servers"}
assert set(paths["/monitor-tasks/{id}/integration/callbacks:send"]) == {"post", "servers"}
assert {"get", "post"} <= set(paths["/deduction-rules"])
assert {"get", "put", "delete"} <= set(paths["/deduction-rules/{id}"])
assert set(paths["/reports/summary"]) == {"get", "servers"}
assert set(paths["/reports/summary:export"]) == {"get", "servers"}
assert set(paths["/protocol-configs"]) == {"get", "servers"}
assert set(paths["/protocol-configs/{protocol}/allowed-hosts"]) == {"put", "servers"}
assert set(paths["/flow-logs/{id}/request-detail"]) == {"get", "servers"}
assert set(paths["/callback-records"]) == {"get", "servers"}
assert set(paths["/media-macros"]) == {"get", "servers"}
protocol_list = paths["/protocol-configs"]["get"]
protocol_update = paths["/protocol-configs/{protocol}/allowed-hosts"]["put"]
assert protocol_list["security"] == [{"saToken": []}]
assert protocol_update["security"] == [{"saToken": []}]
assert protocol_update["requestBody"]["content"]["application/json"]["schema"]["$ref"].endswith("ProtocolAllowedHostsUpdate")
assert paths["/creatives"]["post"]["requestBody"]["content"]["application/json"]["schema"]["$ref"].endswith("CreativeSaveRequest")
assert paths["/creatives/assets"]["post"]["requestBody"]["content"].get("multipart/form-data")
assert paths["/monitor-tasks/{id}"]["put"]["requestBody"]["content"]["application/json"]["schema"]["$ref"].endswith("MonitorTaskSaveRequest")
test_generate = paths["/monitor-tasks/{id}/test-links:generate"]["post"]
test_execute = paths["/monitor-tasks/{id}/test-links:execute"]["post"]
test_result = paths["/monitor-tasks/{id}/test-results/{requestId}"]["get"]
integration_feedback = paths["/monitor-tasks/{id}/integration/feedback-requests"]["get"]
integration_callback = paths["/monitor-tasks/{id}/integration/callbacks:send"]["post"]
flow_log_request_detail = paths["/flow-logs/{id}/request-detail"]["get"]
callback_records = paths["/callback-records"]["get"]
assert test_generate["security"] == [{"saToken": []}]
assert test_execute["security"] == [{"saToken": []}]
assert test_result["security"] == [{"saToken": []}]
assert integration_feedback["security"] == [{"saToken": []}]
assert integration_callback["security"] == [{"saToken": []}]
assert flow_log_request_detail["security"] == [{"saToken": []}]
assert callback_records["security"] == [{"saToken": []}]
callback_record_parameters = {item["name"]: item for item in callback_records["parameters"]}
assert {"startDate", "endDate", "advertiserId", "promotionLinkId", "mediaId", "monitorTaskId", "eventCode", "targetConversion", "deductionDecision", "requestId", "current", "size"} == set(callback_record_parameters)
assert callback_record_parameters["requestId"]["schema"]["maxLength"] == 128
assert callback_record_parameters["size"]["schema"]["maximum"] == 100
assert test_generate["requestBody"]["content"]["application/json"]["schema"]["$ref"].endswith("MonitorTaskTestGenerateRequest")
assert test_execute["requestBody"]["content"]["application/json"]["schema"]["$ref"].endswith("MonitorTaskTestExecuteRequest")
assert integration_callback["requestBody"]["content"]["application/json"]["schema"]["$ref"].endswith("MonitorTaskIntegrationCallbackRequest")
billing_entry_types = next(item for item in paths["/billing-entries"]["get"]["parameters"] if item["name"] == "entryType")["schema"]["enum"]
assert set(billing_entry_types) == {"RECHARGE", "MANUAL_DEBIT", "CLICK_CHARGE", "CONVERSION_CHARGE"}
schemas = openapi.get("components", {}).get("schemas", {})
assert {
    "AdvertiserRolePermissionsUpdate", "AdvertiserRolePermissions", "AdvertiserRoleMenuNode",
    "AdvertiserRechargeRequest", "AdvertiserRechargeResult", "AdvertiserDeductionRequest",
    "AdvertiserDeductionResult", "DashboardSummary", "DashboardMetric",
    "CreativeSaveRequest", "Creative", "CreativeAsset", "MonitorTaskSaveRequest",
    "BillingEntry", "BillingEntryPageEnvelope", "MonitorTaskTestGenerateRequest",
    "MonitorTaskTestExecuteRequest", "MonitorTaskTestPreview", "MonitorTaskTestExecution",
    "MonitorTaskTestResult", "MonitorTaskTestTimelineItem", "MonitorTaskTestCallbackFact",
    "MediaCallbackRatioRuleSaveRequest", "MediaCallbackRatioRule",
    "DeductionSummaryRow", "DeductionSummaryPage",
    "MonitorTaskIntegrationFeedback", "MonitorTaskIntegrationCallbackRequest",
    "MonitorTaskIntegrationCallbackResult"
    , "ProtocolAllowedHostsUpdate", "ProtocolConfig", "ProtocolConfigEnvelope",
    "ProtocolConfigListEnvelope"
    , "FlowLogRequestDetail", "FlowLogRequestDetailEnvelope"
    , "FlowLogStage", "MediaMacro", "MediaMacroListEnvelope"
    , "CallbackEventCode", "CallbackRecord", "CallbackRecordPageEnvelope"
    , "GravityCallbackSuccess", "GravityCallbackFailure"
} <= set(schemas)
assert set(schemas["ProtocolAllowedHostsUpdate"]["required"]) == {"allowedHosts"}
assert schemas["ProtocolAllowedHostsUpdate"]["properties"]["allowedHosts"]["maxItems"] == 50
assert set(schemas["ProtocolConfig"]["properties"]["protocol"]["enum"]) == {"AERIM", "WEIFENG", "GRAVITY"}
assert schemas["GravityCallbackSuccess"]["properties"]["code"]["const"] == 200
assert schemas["GravityCallbackFailure"]["properties"]["code"]["const"] == 400
assert set(schemas["AdvertiserRolePermissionsUpdate"]["required"]) == {"menuIds", "allDataScope"}
assert set(schemas["AdvertiserRechargeRequest"]["required"]) == {"requestId", "amount"}
assert set(schemas["AdvertiserDeductionRequest"]["required"]) == {"requestId", "amount", "remark"}
ratio_schema = schemas["MediaCallbackRatioRuleSaveRequest"]["properties"]["ratio"]
assert ratio_schema["minimum"] == 0 and ratio_schema["maximum"] == 1
assert {"callbackCount", "deductionPassedCount", "deductionDeductedCount", "deductionPendingCount", "basePrice"} <= set(schemas["DeductionSummaryRow"]["properties"])
assert set(schemas["DeductionSummaryPage"]["properties"]["deductionCoverageStatus"]["enum"]) == {"NONE", "PARTIAL", "FULL"}
assert "callback_url" not in schemas["MonitorTaskTestGenerateRequest"]["properties"]
assert "callback_url" not in schemas["MonitorTaskTestExecuteRequest"]["properties"]
assert "targetUrl" not in schemas["MonitorTaskTestExecuteRequest"]["properties"]
assert "callbackToken" in schemas["MonitorTaskTestExecuteRequest"]["properties"]
assert not ({"url", "callbackUrl", "targetUrl"} & set(schemas["MonitorTaskIntegrationCallbackRequest"]["properties"]))
assert "manualCallbackAllowed" in schemas["MonitorTaskIntegrationFeedback"]["required"]
assert schemas["MonitorTaskIntegrationFeedback"]["properties"]["manualCallbackAllowed"]["type"] == "boolean"
assert {"model", "osVersion", "ipv6", "caid2"} <= set(schemas["FlowLogRequestDetail"]["properties"])
assert {"resultStatus", "partnerHttpStatus", "message", "durationMs", "responsePreview"} <= set(schemas["FlowLogRequestDetail"]["properties"])
assert schemas["FlowLogRequestDetail"]["properties"]["responsePreview"]["maxLength"] == 4096
assert {"traceId", "coverageStatus", "stages"} <= set(schemas["FlowLogRequestDetail"]["properties"])
assert set(schemas["FlowLogStage"]["properties"]["stageCode"]["enum"]) == {"LEGACY", "MEDIA_INGRESS", "ADVERTISER_DELIVERY", "ADVERTISER_CALLBACK", "CALLBACK_ACCOUNTING", "MEDIA_DEDUCTION", "MEDIA_DELIVERY"}
assert paths["/media-macros"]["get"]["security"] == [{"saToken": []}]
assert set(schemas["CallbackRecord"]["properties"]["deductionDecision"]["enum"]) == {"PENDING", "PASSED", "DEDUCTED", None}
assert set(callback_record_parameters["deductionDecision"]["schema"]["enum"]) == {"PENDING", "PASSED", "DEDUCTED", "UNAVAILABLE"}
assert set(schemas["CallbackRecord"]["properties"]["billingStatus"]["enum"]) == {"NOT_APPLICABLE", "POSTED", "REJECTED_INSUFFICIENT_BALANCE"}
assert {"EVOKE_C", "LAHUO", "FIRST_ORDER"} <= set(schemas["CallbackEventCode"]["enum"])
assert schemas["MonitorTaskTestPreview"]["properties"]["event"]["const"] == "CLK"
assert set(schemas["MonitorTaskTestResult"]["properties"]["state"]["enum"]) == {
    "WAITING", "REJECTED", "UPSTREAM_PENDING", "UPSTREAM_RESPONDED", "UPSTREAM_FAILED",
    "CALLBACK_PROCESSING", "CALLBACK_FAILED", "CALLBACK_DEDUCTED", "DOWNSTREAM_PENDING", "COMPLETED"
}
assert set(schemas["DashboardSummary"]["properties"]["metrics"]["required"]) == {
    "SPEND", "IMPRESSION", "CLICK", "CONVERSION", "CTR", "CVR", "CPM", "CPC", "CPA"
}
assert all("/v1/" not in path for path in paths), "v1 data-plane paths must not return"
assert all(not path.startswith("/open-api/events/v1") for path in paths), "historical event API must not be active"
webhooks = openapi.get("webhooks", {})
assert "downstreamConversionGet" in webhooks, "standard downstream callback webhook must be documented"
assert set(webhooks["downstreamConversionGet"]) == {"get"}
assert "requestBody" not in webhooks["downstreamConversionGet"]["get"]

for name in ("feedback-req-v2.schema.json", "callback-req-v2.schema.json", "aerim-callback-req-v1.schema.json", "weifeng-callback-req-v1.schema.json", "gravity-callback-req-v1.schema.json", "creative-save-v111.schema.json", "monitor-task-billing-v111.schema.json"):
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
assert {"媒体 Feedback GET", "广告主 Callback GET", "AERIM Callback GET", "微风互动 Callback GET", "引力 Callback GET"} <= item_names
methods = {item.get("name"): item.get("request", {}).get("method") for item in postman.get("item", [])}
assert methods.get("读取共享广告主角色权限") == "GET"
assert methods.get("全量替换共享广告主角色权限") == "PUT"
assert methods.get("广告主幂等充值") == "POST"
assert methods.get("广告主幂等扣减") == "POST"
assert methods.get("首页真实数据摘要") == "GET"
assert methods.get("查询创意列表") == "GET"
assert methods.get("上传创意素材") == "POST"
assert methods.get("创建图片创意") == "POST"
assert methods.get("替换创意推广链接绑定") == "PUT"
assert methods.get("修改监测任务计费配置") == "PUT"
assert methods.get("查询广告主账务流水") == "GET"
assert methods.get("查询上游协议允许域名") == "GET"
assert methods.get("替换 AERIM 允许域名") == "PUT"
assert methods.get("替换 GRAVITY 允许域名") == "PUT"
role_update = next(item for item in postman["item"] if item.get("name") == "全量替换共享广告主角色权限")
role_update_body = json.loads(role_update["request"]["body"]["raw"])
assert set(role_update_body) == {"menuIds", "allDataScope"}
assert role_update_body["allDataScope"] is False
recharge_item = next(item for item in postman["item"] if item.get("name") == "广告主幂等充值")
recharge_body = json.loads(recharge_item["request"]["body"]["raw"])
assert set(recharge_body) == {"requestId", "amount", "remark"}
assert recharge_body["amount"] > 0
deduction_item = next(item for item in postman["item"] if item.get("name") == "广告主幂等扣减")
deduction_body = json.loads(deduction_item["request"]["body"]["raw"])
assert set(deduction_body) == {"requestId", "amount", "remark"}
assert deduction_body["amount"] > 0
assert deduction_body["remark"].strip()
creative_item = next(item for item in postman["item"] if item.get("name") == "创建图片创意")
creative_body = json.loads(creative_item["request"]["body"]["raw"])
assert set(creative_body) == {"advertiserId", "creativeName", "sourceMaterialId", "status", "promotionLinkIds", "remark"}
assert "assetUrl" not in creative_body and "checksumSha256" not in creative_body
billing_item = next(item for item in postman["item"] if item.get("name") == "修改监测任务计费配置")
billing_body = json.loads(billing_item["request"]["body"]["raw"])
assert {"billingEnabled", "billingType", "unitPrice", "conversionEvent"} <= set(billing_body)
protocol_item = next(item for item in postman["item"] if item.get("name") == "替换 AERIM 允许域名")
protocol_body = json.loads(protocol_item["request"]["body"]["raw"])
assert protocol_body == {"allowedHosts": ["d.aerim.cn", "c.aerim.cn"]}
gravity_protocol_item = next(item for item in postman["item"] if item.get("name") == "替换 GRAVITY 允许域名")
gravity_protocol_body = json.loads(gravity_protocol_item["request"]["body"]["raw"])
assert gravity_protocol_body == {"allowedHosts": ["backend.gravity-engine.com"]}
serialized = json.dumps(postman, ensure_ascii=False)
assert "/ocpx/feedback" in serialized
assert "/ocpx/callback" in serialized
assert "/ocpx/aerim/callback" in serialized
assert "/ocpx/weifeng/callback" in serialized
assert "/ocpx/gravity/callback" in serialized
assert "/boot/ocpx/advertisers/account-role/permissions" in serialized
assert "/boot/ocpx/protocol-configs" in serialized
assert "/boot/ocpx/advertisers/{{advertiserId}}/recharge" in serialized
assert "/boot/ocpx/advertisers/{{advertiserId}}/deduct" in serialized
assert "/boot/ocpx/dashboard/summary" in serialized
assert "/boot/ocpx/creatives" in serialized
assert "/boot/ocpx/monitor-tasks/{{monitorTaskId}}" in serialized
assert "/boot/ocpx/billing-entries" in serialized
assert postman_variables.get("callbackKid") == "v1"
assert serialized.count("kid={{callbackKid}}") >= 4
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
