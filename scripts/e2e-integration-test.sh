#!/bin/bash
# ============================================================
# SCX 上下游全链路联调测试
# 覆盖: 管理配置 → 事件上报 → 归因 → 回传 → 报表
# 用法: bash scripts/e2e-integration-test.sh [BASE_URL]
#       默认 BASE_URL=http://localhost:9999/boot
# ============================================================
set -euo pipefail

BASE="${1:-http://localhost:9999/boot}"
CHANNEL_CLIENT_ID="${SCX_CHANNEL_CLIENT_ID:?必须通过环境变量提供 SCX_CHANNEL_CLIENT_ID}"
CHANNEL_KEY_VERSION="${SCX_CHANNEL_KEY_VERSION:?必须通过环境变量提供 SCX_CHANNEL_KEY_VERSION}"
CHANNEL_SECRET="${SCX_CHANNEL_SECRET:?必须通过环境变量提供 SCX_CHANNEL_SECRET}"
MEDIA="media_sandbox"
TASK="demo-task"
USERNAME="system"
PASSWORD="123456"
PASS=0
FAIL=0

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

check() { if [ "$1" = "0" ]; then PASS=$((PASS+1)); echo -e "  ${GREEN}✅ $2${NC}"; else FAIL=$((FAIL+1)); echo -e "  ${RED}❌ $2 (code=$1)${NC}"; echo "    $3"; fi; }
info()  { echo -e "\n${YELLOW}═══ $1 ═══${NC}"; }

# ============================================================
# 0. 前置检查
# ============================================================
info "0. 环境检查"
HTTP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/actuator/health" 2>/dev/null || echo "000")
if [ "$HTTP" = "200" ]; then check "0" "后端健康检查(HTTP $HTTP)"; else check "1" "后端健康检查" "HTTP $HTTP"; fi

# ============================================================
# 1. 登录获取 Token
# ============================================================
info "1. 管理后台登录"

ENCODE_KEY="yzytech2026yzytech"
ENCPWD=$(node -e "
const crypto=require('crypto');
const k=crypto.createHash('sha256').update('$ENCODE_KEY').digest();
const c=crypto.createCipheriv('aes-256-ecb',k,null);
c.setAutoPadding(true);
console.log(c.update('$PASSWORD','utf8','base64')+c.final('base64'));
" 2>/dev/null)

RESP=$(curl -s -X POST "$BASE/auth/token/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=$USERNAME" \
  --data-urlencode "password=$ENCPWD" 2>/dev/null)

TOKEN=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['tokenValue'])" 2>/dev/null || echo "")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])" 2>/dev/null || echo "1")
check "$CODE" "系统管理员登录" "$RESP"

if [ -z "$TOKEN" ]; then
  echo -e "${RED}登录失败，无法继续后续测试${NC}"
  exit 1
fi

AUTH="satoken: $TOKEN"

# ============================================================
# 2. 管理配置链路（上游）
# ============================================================
info "2. 上游配置链路（管理后台 API）"

api_code() {
  curl -s "$1" -H "$AUTH" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','?'))" 2>/dev/null
}

# 媒体范围
C=$(api_code "$BASE/subjects/media?current=1&size=10")
check "$C" "媒体列表"

# 主数据
C=$(api_code "$BASE/ad/upstream-customers?current=1&mediaId=$MEDIA")
check "$C" "上游客户"

C=$(api_code "$BASE/ad/partners?current=1&mediaId=$MEDIA")
check "$C" "合作方"

C=$(api_code "$BASE/ad/products?current=1&mediaId=$MEDIA")
check "$C" "产品"

C=$(api_code "$BASE/ad/media-channels?current=1&mediaId=$MEDIA")
check "$C" "媒体渠道"

C=$(api_code "$BASE/ad/integration-documents?current=1&mediaId=$MEDIA")
check "$C" "接入文档"

# 追踪链接 & 事件映射
C=$(api_code "$BASE/ad/tracking-links?current=1&mediaId=$MEDIA")
check "$C" "追踪链接"
LINK_ID=$(curl -s "$BASE/ad/tracking-links?current=1&mediaId=$MEDIA" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['records'][0]['id'])" 2>/dev/null)
C=$(api_code "$BASE/ad/tracking-links/$LINK_ID/event-mappings")
check "$C" "事件映射配置"

# 监测任务
C=$(api_code "$BASE/ad/tracking-tasks?current=1&mediaId=$MEDIA")
check "$C" "监测任务"

# RTA
C=$(api_code "$BASE/ad/rta-endpoints?current=1&mediaId=$MEDIA")
check "$C" "RTA端点"

# ============================================================
# 3. 事件上报链路（下游 → SCX）
# ============================================================
info "3. 事件上报链路（HMAC签名 → 点击 → 幂等 → 转化）"

# 签名工具
hmac_sign() {
	local endpoint="$1" body="$2"
  local ts="$(date +%s)000"
  local nc="nc-${ts}-$(openssl rand -hex 4)"
  local canon=$(python3 -c "
import json,hashlib
r=json.loads('''$body''')
et=r['eventType'].upper()
p={'schemaVersion':r['schemaVersion'],'eventId':r['eventId'],'taskCode':r['taskCode'],'eventType':et,'occurredAt':r['occurredAt'],'device':r['device'],'businessEventKey':r.get('businessEventKey'),'callbackRef':r.get('callbackRef'),'callbackUrl':r.get('callbackUrl'),'attributes':dict(sorted(r.get('attributes',{}).items()))}
print(json.dumps(p,separators=(',',':'),ensure_ascii=False))
" 2>/dev/null)
  local ph=$(echo -n "$canon" | openssl dgst -sha256 | awk '{print $2}')
	local signing_input=$(printf 'POST\n%s\n%s\n%s\n%s' "$endpoint" "$ts" "$nc" "$ph")
	local sg=$(echo -n "$signing_input" | openssl dgst -sha256 -hmac "$CHANNEL_SECRET" | awk '{print $2}')
  echo "$ts|$nc|$sg"
}

send_event() {
  local endpoint="$1" body="$2"
	local sig=$(hmac_sign "$endpoint" "$body")
  local ts=$(echo "$sig" | cut -d'|' -f1)
  local nc=$(echo "$sig" | cut -d'|' -f2)
  local sg=$(echo "$sig" | cut -d'|' -f3)
  curl -s -X POST "$BASE$endpoint" \
    -H "Content-Type: application/json" \
		-H "X-Client-Id: $CHANNEL_CLIENT_ID" \
		-H "X-Key-Version: $CHANNEL_KEY_VERSION" \
    -H "X-Timestamp: $ts" \
    -H "X-Nonce: $nc" \
    -H "X-Sign: $sg" \
    -d "$body" 2>/dev/null
}

# 生成设备哈希
DEVICE_RAW="e2e-device-$(date +%s)"
DEVICE_HASH=$(echo -n "$DEVICE_RAW" | openssl dgst -sha256 | awk '{print $2}')
echo "  已生成不可逆测试设备哈希"

# 3.1 发送 CLICK
CLICK_ID="e2e-click-$(date +%s)"
CLICK_BODY='{"schemaVersion":"1.1","eventId":"'$CLICK_ID'","taskCode":"'$TASK'","eventType":"CLICK","occurredAt":"2026-07-21T16:00:00+08:00","device":{"platform":"ANDROID","idType":"OAID","hashAlgorithm":"SHA256","idHash":"'$DEVICE_HASH'","idVersion":null},"attributes":{"model":"TestPhone","os":"android"}}'

RESP=$(send_event "/open-api/events/v1/clicks" "$CLICK_BODY")
C=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])" 2>/dev/null)
DUP=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['duplicateDelivery'])" 2>/dev/null)
STS=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['acceptanceStatus'])" 2>/dev/null)
check "$C" "CLICK上报 → $STS" "$RESP"

# 3.2 幂等测试 — 相同 eventId 再次发送
RESP2=$(send_event "/open-api/events/v1/clicks" "$CLICK_BODY")
C2=$(echo "$RESP2" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])" 2>/dev/null)
DUP2=$(echo "$RESP2" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['duplicateDelivery'])" 2>/dev/null)
if [ "$DUP2" = "True" ]; then check "0" "幂等保护: duplicateDelivery=true"; else check "1" "幂等保护" "$RESP2"; fi

# 3.3 发送 ACTIVATE（带 businessEventKey）
ACT_ID="e2e-activate-$(date +%s)"
BIZ_KEY="e2e-biz-$(date +%s)"
ACT_BODY='{"schemaVersion":"1.1","eventId":"'$ACT_ID'","taskCode":"'$TASK'","eventType":"ACTIVATE","occurredAt":"2026-07-21T16:05:00+08:00","device":{"platform":"ANDROID","idType":"OAID","hashAlgorithm":"SHA256","idHash":"'$DEVICE_HASH'","idVersion":null},"businessEventKey":"'$BIZ_KEY'","attributes":{}}'

RESP3=$(send_event "/open-api/events/v1/conversions" "$ACT_BODY")
C3=$(echo "$RESP3" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])" 2>/dev/null)
check "$C3" "ACTIVATE转化已可靠受理" "$RESP3"

# 3.4 发送 REGISTER
REG_ID="e2e-register-$(date +%s)"
BIZ_KEY2="e2e-biz2-$(date +%s)"
REG_BODY='{"schemaVersion":"1.1","eventId":"'$REG_ID'","taskCode":"'$TASK'","eventType":"REGISTER","occurredAt":"2026-07-21T16:10:00+08:00","device":{"platform":"ANDROID","idType":"OAID","hashAlgorithm":"SHA256","idHash":"'$DEVICE_HASH'","idVersion":null},"businessEventKey":"'$BIZ_KEY2'","attributes":{}}'

RESP4=$(send_event "/open-api/events/v1/conversions" "$REG_BODY")
C4=$(echo "$RESP4" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])" 2>/dev/null)
check "$C4" "REGISTER转化" "$RESP4"

# 3.5 发送 PAY
PAY_ID="e2e-pay-$(date +%s)"
BIZ_KEY3="e2e-biz3-$(date +%s)"
PAY_BODY='{"schemaVersion":"1.1","eventId":"'$PAY_ID'","taskCode":"'$TASK'","eventType":"PAY","occurredAt":"2026-07-21T16:15:00+08:00","device":{"platform":"ANDROID","idType":"OAID","hashAlgorithm":"SHA256","idHash":"'$DEVICE_HASH'","idVersion":null},"businessEventKey":"'$BIZ_KEY3'","attributes":{}}'

RESP5=$(send_event "/open-api/events/v1/conversions" "$PAY_BODY")
C5=$(echo "$RESP5" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])" 2>/dev/null)
check "$C5" "PAY转化" "$RESP5"

# ============================================================
# 4. 归因 & 回传验证（管理后台查询）
# ============================================================
info "4. 归因与回传验证"

C=$(api_code "$BASE/ad/attributions?current=1&size=20&mediaId=$MEDIA")
ATTR_TOTAL=$(curl -s "$BASE/ad/attributions?current=1&size=20&mediaId=$MEDIA" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['total'])" 2>/dev/null)
check "$C" "归因结果 → ${ATTR_TOTAL}条"

C=$(api_code "$BASE/ad/callback-jobs?current=1&size=20&mediaId=$MEDIA")
CB_TOTAL=$(curl -s "$BASE/ad/callback-jobs?current=1&size=20&mediaId=$MEDIA" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['total'])" 2>/dev/null)
check "$C" "回传任务 → ${CB_TOTAL}条"

C=$(api_code "$BASE/ad/callback-jobs/outbox?current=1&size=20&mediaId=$MEDIA")
OUT_TOTAL=$(curl -s "$BASE/ad/callback-jobs/outbox?current=1&size=20&mediaId=$MEDIA" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['total'])" 2>/dev/null)
check "$C" "Outbox事件 → ${OUT_TOTAL}条"

C=$(api_code "$BASE/ad/callback-jobs/stats?mediaId=$MEDIA")
check "$C" "队列统计"

# ============================================================
# 5. 报表验证
# ============================================================
info "5. 报表验证"

TODAY=$(date +%Y-%m-%d)
C=$(api_code "$BASE/ad/reports/details?current=1&size=5&mediaId=$MEDIA")
EVT_TOTAL=$(curl -s "$BASE/ad/reports/details?current=1&size=5&mediaId=$MEDIA" -H "$AUTH" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['total'])" 2>/dev/null)
check "$C" "事件明细 → ${EVT_TOTAL}条"

C=$(api_code "$BASE/ad/reports/breakdowns?dimension=DATE&startDate=$TODAY&endDate=$TODAY&mediaId=$MEDIA")
check "$C" "数据汇总(当日)"

C=$(api_code "$BASE/ad/reports/overview?startDate=$TODAY&endDate=$TODAY&mediaId=$MEDIA")
check "$C" "仪表盘概览"

C=$(api_code "$BASE/ad/export-tasks?current=1&size=10&mediaId=$MEDIA")
check "$C" "导出任务"

# ============================================================
# 6. 权限验证
# ============================================================
info "6. 安全验证"
C1=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/subjects/media" 2>/dev/null)
if [ "$C1" = "401" ]; then check "0" "无Token拒绝(401)"; else check "1" "无Token拒绝" "HTTP $C1"; fi

# ============================================================
# 结果汇总
# ============================================================
echo ""
echo "=========================================="
echo -e "  结果: ${GREEN}${PASS} 通过${NC} / $((PASS+FAIL)) 总计"
if [ "$FAIL" -eq 0 ]; then
  echo -e "  ${GREEN}上下游全链路联调通过 ✅${NC}"
else
  echo -e "  ${RED}${FAIL} 项失败，请检查上方错误信息${NC}"
fi
echo "=========================================="
