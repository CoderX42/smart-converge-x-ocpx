<script setup lang="ts">
import { computed } from 'vue';

import { ElMessage } from 'element-plus';

const props = defineProps<{
  credentialRef?: string;
  notifyUrl?: string;
  protocol?: string;
  requestUrl?: string;
}>();

const deviceKeys =
  'oaid、oaidMd5、imei、imeiMd5、idfa、idfaMd5、androidId、androidIdMd5、macMd5、caid、caidVersion、caidMd5、preCaid、preCaidVersion、preCaidMd5';
const contextKeys = 'os、osv、ip、ipv6、ua、model';

function pathOf(url?: string) {
  try {
    return url ? new URL(url).pathname : '/rta/requests';
  } catch {
    return '/rta/requests';
  }
}

const isOppo = computed(() => props.protocol === 'OPPO');
const keyId = computed(() => props.credentialRef || '<凭据引用>');
const requestPath = computed(() => pathOf(props.requestUrl));
const signingText = computed(() =>
  [
    'POST',
    requestPath.value,
    keyId.value,
    '<X-Rta-Timestamp>',
    '<X-Rta-Nonce>',
    'hex(sha256(请求体原文))',
  ].join('\n'),
);
const requestSample = `{
  "requestId": "media-req-0001",
  "taskIds": ["<监测任务 ID>"],
  "device": { "oaidMd5": "<32 位小写 MD5>" },
  "context": { "os": "android", "ip": "1.2.3.4" }
}`;
const acceptedSample = `HTTP 202
{ "requestId": "media-req-0001", "relayRequestId": "<SCX 请求 ID>", "deadline": 1760000000000 }`;
const notifySample = `{
  "requestId": "media-req-0001",
  "relayRequestId": "<SCX 请求 ID>",
  "status": "ALLOW",
  "matchedTaskIds": ["<监测任务 ID>"],
  "reasonCode": "UPSTREAM_ALLOW",
  "expiresAt": 1760000300000
}`;
const pythonSample = computed(
  () => `import hashlib, hmac, json, time, uuid, requests

SECRET = "<通过约定渠道单独交付的密钥>"
KEY = "${keyId.value}"
URL = "${props.requestUrl || '<SCX RTA 地址>'}"
PATH = "${requestPath.value}"

body = json.dumps(${requestSample.replaceAll('\n', ' ')}, separators=(",", ":"))
ts = str(int(time.time() * 1000))
nonce = uuid.uuid4().hex
text = "\\n".join(["POST", PATH, KEY, ts, nonce, hashlib.sha256(body.encode()).hexdigest()])
sign = hmac.new(SECRET.encode(), text.encode(), hashlib.sha256).hexdigest()
resp = requests.post(URL, data=body.encode(), timeout=3, headers={
    "Content-Type": "application/json",
    "X-Rta-Key": KEY, "X-Rta-Timestamp": ts, "X-Rta-Nonce": nonce, "X-Rta-Sign": sign,
})
print(resp.status_code, resp.text)`,
);

const markdown = computed(() => {
  if (isOppo.value)
    return [
      '# SCX OPPO RTA 对接资料',
      `- RTA 地址：${props.requestUrl || '保存后生成'}`,
      '- 协议：OPPO 策略 ID 协议，同一请求内同步返回允许投放的策略 ID。',
      '- 每个监测任务对应一个策略 ID 与点击监测链接，请在监测任务的 RTA 配置中获取。',
      '- 签名密钥由服务端配置，通过约定渠道单独交付，本资料不包含密钥。',
    ].join('\n');
  return [
    '# SCX 通用异步 RTA 对接资料',
    '## 请求',
    `- 地址：POST ${props.requestUrl || '保存后生成'}`,
    '- Content-Type: application/json；不允许 URL 查询参数；请求体不超过 64 KB。',
    '- 返回 202 只表示受理，最终决策通过决策通知送达。',
    '## 签名请求头',
    `- X-Rta-Key：${keyId.value}`,
    '- X-Rta-Timestamp：13 位毫秒时间戳，与服务器时间相差不超过 5 分钟。',
    '- X-Rta-Nonce：16–128 位字母、数字、下划线或短横线，10 分钟内不可重复。',
    '- X-Rta-Sign：HMAC-SHA256(密钥, 待签名文本) 的小写十六进制。',
    String.raw`## 待签名文本（以 \n 连接）`,
    '```text',
    signingText.value,
    '```',
    '## 请求体',
    `- device 至少一项，可用字段：${deviceKeys}`,
    `- context 可选字段：${contextKeys}`,
    '- taskIds 最多 100 个，须为已绑定 RTA 的监测任务 ID。',
    '```json',
    requestSample,
    '```',
    '```text',
    acceptedSample,
    '```',
    '## 决策通知',
    `- SCX 以 POST 推送到：${props.notifyUrl || '媒体登记的决策通知地址'}`,
    '- 使用相同的签名方式，路径为通知地址的路径；媒体须返回 2xx，失败 30 秒内最多重试 4 次。',
    '- status 取值：ALLOW 放行、DENY 拒绝、ERROR 错误、TIMEOUT 超时。',
    '```json',
    notifySample,
    '```',
    '## Python 签名示例',
    '```python',
    pythonSample.value,
    '```',
    '> 密钥通过约定渠道单独交付，本资料不包含密钥。',
  ].join('\n');
});

async function copy() {
  try {
    await navigator.clipboard.writeText(markdown.value);
    ElMessage.success('对接资料已复制为 Markdown，密钥请单独交付');
  } catch {
    ElMessage.error('复制失败，请手动选择内容复制');
  }
}
</script>

<template>
  <div class="rta-access-doc">
    <div class="rta-access-doc__head">
      <strong>对接资料</strong>
      <el-button size="small" type="primary" plain @click="copy"
        >复制为 Markdown</el-button
      >
    </div>
    <template v-if="isOppo">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="RTA 地址">
          <span class="break-all">{{ requestUrl || '保存后生成' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="决策方式"
          >同一请求内同步返回允许投放的策略 ID</el-descriptions-item
        >
        <el-descriptions-item label="策略与点击链接"
          >每个监测任务一个，在监测任务的 RTA 配置中获取</el-descriptions-item
        >
      </el-descriptions>
    </template>
    <template v-else>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="请求地址">
          <span class="break-all">POST {{ requestUrl || '保存后生成' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="X-Rta-Key">{{
          keyId
        }}</el-descriptions-item>
        <el-descriptions-item label="X-Rta-Timestamp"
          >13 位毫秒时间戳，误差不超过 5 分钟</el-descriptions-item
        >
        <el-descriptions-item label="X-Rta-Nonce"
          >16–128 位随机串，10 分钟内不可重复</el-descriptions-item
        >
        <el-descriptions-item label="X-Rta-Sign"
          >HMAC-SHA256 小写十六进制</el-descriptions-item
        >
      </el-descriptions>
      <p class="rta-access-doc__label">待签名文本（逐行以换行符连接）</p>
      <pre>{{ signingText }}</pre>
      <el-collapse>
        <el-collapse-item title="请求与受理示例" name="request">
          <p class="rta-access-doc__label">device 可用字段：{{ deviceKeys }}</p>
          <p class="rta-access-doc__label">
            context 可用字段：{{ contextKeys }}
          </p>
          <pre>{{ requestSample }}</pre>
          <pre>{{ acceptedSample }}</pre>
        </el-collapse-item>
        <el-collapse-item title="决策通知示例" name="notify">
          <p class="rta-access-doc__label">
            推送到 {{ notifyUrl || '决策通知地址' }}，签名方式相同；须返回
            2xx，30 秒内最多重试 4 次。
          </p>
          <pre>{{ notifySample }}</pre>
        </el-collapse-item>
        <el-collapse-item title="Python 签名示例" name="python">
          <pre>{{ pythonSample }}</pre>
        </el-collapse-item>
      </el-collapse>
    </template>
    <p class="rta-access-doc__label">
      密钥通过约定渠道单独交付，本资料不包含任何密钥。
    </p>
  </div>
</template>

<style scoped>
.rta-access-doc {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.rta-access-doc__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rta-access-doc__label {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

pre {
  padding: 10px 12px;
  margin: 0;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre;
  background: var(--el-bg-color);
  border-radius: 6px;
}
</style>
