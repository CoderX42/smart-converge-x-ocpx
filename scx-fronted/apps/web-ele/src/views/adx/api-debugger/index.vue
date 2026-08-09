<script lang="ts" setup>
import type {
  ApiDebugCase,
  ApiDebugExecution,
  ApiDebugExecutionLog,
  ApiDebugPreflight,
  ApiDebugRecentLog,
  ApiDebugRequest,
  ApiDebugTarget,
} from '#/api/adx';

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  authorizeApiDebug,
  authorizeApiDebugLogs,
  deleteApiDebugCase,
  executeApiDebug,
  executeMultipartApiDebug,
  getApiDebugCases,
  getApiDebugExecution,
  getApiDebugExecutionLogs,
  getApiDebugExecutions,
  getApiDebugRecentLogs,
  getApiDebugTargets,
  preflightApiDebug,
  saveApiDebugCase,
} from '#/api/adx';

import {
  formatJsonText,
  formatResponseText,
  maskSensitiveText,
  resolveIntegratedTarget,
  serializeRequestBody,
} from './model';

defineOptions({ name: 'AdxApiDebugger' });

type PairRow = { enabled: boolean; key: string; value: string };

const terminalStates = new Set(['FAILED', 'PARTIAL', 'SUCCEEDED', 'UNKNOWN']);
const mode = ref<'CUSTOM' | 'INTEGRATED'>('INTEGRATED');
const scenario = ref<'DOWNSTREAM_TO_UPSTREAM' | 'UPSTREAM_TO_DOWNSTREAM'>(
  'DOWNSTREAM_TO_UPSTREAM',
);
const method = ref<ApiDebugRequest['method']>('POST');
const bodyType = ref<ApiDebugRequest['bodyType']>('JSON');
const requestUrl = ref('');
const timeoutSeconds = ref(5);
const targets = ref<ApiDebugTarget[]>([]);
const targetId = ref('');
const queryRows = ref<PairRow[]>([emptyRow()]);
const headerRows = ref<PairRow[]>([
  { enabled: true, key: 'Accept', value: 'application/json' },
]);
const formRows = ref<PairRow[]>([emptyRow()]);
const requestBody = ref(
  JSON.stringify(
    {
      channel_id: '',
      clickid: `debug-${crypto.randomUUID()}`,
      event: 'CLICK',
      event_time: Math.floor(Date.now() / 1000),
      monitor_id: '',
      oaid: `debug-device-${crypto.randomUUID()}`,
      os: 'android',
      schema_version: '1.0',
    },
    null,
    2,
  ),
);
const files = ref<File[]>([]);
const sending = ref(false);
const refreshing = ref(false);
const currentExecution = ref<ApiDebugExecution>();
const executionLogs = ref<ApiDebugExecutionLog[]>([]);
const responseTab = ref('body');
const logTab = ref('execution');
const preflight = ref<ApiDebugPreflight>();
const confirmVisible = ref(false);
const password = ref('');
const confirmation = ref('');
const casesVisible = ref(false);
const historyVisible = ref(false);
const cases = ref<ApiDebugCase[]>([]);
const history = ref<ApiDebugExecution[]>([]);
const historyTotal = ref(0);
const saveCaseVisible = ref(false);
const caseName = ref('');
const caseDescription = ref('');
const rawAuthVisible = ref(false);
const rawPassword = ref('');
const rawAuthorization = ref('');
const rawSource = ref<'ENGINE' | 'MANAGEMENT'>('MANAGEMENT');
const rawLevel = ref('');
const rawLogger = ref('');
const rawKeyword = ref('');
const rawLogs = ref<ApiDebugRecentLog[]>([]);
const rawCursor = ref(0);
const rawLoading = ref(false);
const autoFollow = ref(true);
const showBodySecrets = ref(false);
const showResponseSecrets = ref(false);
const rawConsole = ref<HTMLElement>();
let pollTimer: ReturnType<typeof setTimeout> | undefined;
let rawPollTimer: ReturnType<typeof setTimeout> | undefined;

const selectedTarget = computed(() =>
  targets.value.find((item) => item.monitorId === targetId.value),
);
const environment = computed(() => preflight.value?.environment ?? '当前环境');
const riskType = computed(() =>
  mode.value === 'INTEGRATED' ? 'danger' : 'warning',
);
const responseBody = computed(() => {
  return formatResponseText(currentExecution.value?.responseBody);
});
const displayedResponseBody = computed(() =>
  showResponseSecrets.value
    ? responseBody.value
    : maskSensitiveText(responseBody.value),
);
const displayedResponseHeaders = computed(() =>
  showResponseSecrets.value
    ? (currentExecution.value?.responseHeaders ?? '{}')
    : maskSensitiveText(currentExecution.value?.responseHeaders ?? '{}'),
);

function emptyRow(): PairRow {
  return { enabled: true, key: '', value: '' };
}

function addRow(rows: PairRow[]) {
  rows.push(emptyRow());
}

function removeRow(rows: PairRow[], index: number) {
  rows.splice(index, 1);
  if (rows.length === 0) rows.push(emptyRow());
}

function rowsToRecord(rows: PairRow[]) {
  return Object.fromEntries(
    rows
      .filter((row) => row.enabled && row.key.trim())
      .map((row) => [row.key.trim(), row.value]),
  );
}

function effectiveUrl() {
  if (mode.value === 'INTEGRATED') return requestUrl.value;
  const url = new URL(requestUrl.value);
  for (const [key, value] of Object.entries(rowsToRecord(queryRows.value))) {
    url.searchParams.set(key, value);
  }
  return url.toString();
}

function effectiveBody() {
  return serializeRequestBody(
    bodyType.value,
    requestBody.value,
    rowsToRecord(formRows.value),
  );
}

function buildRequest(): ApiDebugRequest {
  return {
    body: effectiveBody(),
    bodyType: bodyType.value,
    headers: rowsToRecord(headerRows.value),
    method: method.value,
    mode: mode.value,
    monitorId: mode.value === 'INTEGRATED' ? targetId.value : undefined,
    scenario: mode.value === 'INTEGRATED' ? scenario.value : undefined,
    timeoutSeconds: timeoutSeconds.value,
    url: effectiveUrl(),
  };
}

function formatJson() {
  try {
    requestBody.value = formatJsonText(requestBody.value);
    ElMessage.success('JSON 格式正确');
  } catch {
    ElMessage.error('JSON 格式不正确，请检查后再发送');
  }
}

async function startPreflight() {
  if (mode.value === 'INTEGRATED' && !targetId.value) {
    ElMessage.warning('请先选择已发布路由');
    return;
  }
  if (!requestUrl.value) {
    ElMessage.warning('请输入请求地址');
    return;
  }
  sending.value = true;
  try {
    preflight.value = await preflightApiDebug(buildRequest());
    password.value = '';
    confirmation.value = '';
    confirmVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '预检失败');
  } finally {
    sending.value = false;
  }
}

async function confirmExecution() {
  if (!preflight.value) return;
  if (confirmation.value !== preflight.value.confirmationPhrase) {
    ElMessage.error('确认短语不匹配');
    return;
  }
  sending.value = true;
  try {
    const authorization = await authorizeApiDebug({
      confirmationPhrase: confirmation.value,
      draftId: preflight.value.draftId,
      password: password.value,
      purpose: 'EXECUTE',
    });
    currentExecution.value =
      bodyType.value === 'FORM_DATA' && files.value.length > 0
        ? await executeMultipartApiDebug(
            preflight.value.draftId,
            authorization.token,
            files.value,
          )
        : await executeApiDebug(preflight.value.draftId, authorization.token);
    executionLogs.value = [];
    confirmVisible.value = false;
    logTab.value = 'execution';
    schedulePoll(100);
  } finally {
    sending.value = false;
  }
}

function schedulePoll(delay = 800) {
  if (pollTimer) clearTimeout(pollTimer);
  pollTimer = setTimeout(pollExecution, delay);
}

async function pollExecution() {
  if (!currentExecution.value) return;
  refreshing.value = true;
  try {
    const id = currentExecution.value.id;
    const after = executionLogs.value.at(-1)?.sequence ?? 0;
    const [execution, logs] = await Promise.all([
      getApiDebugExecution(id),
      getApiDebugExecutionLogs(id, after),
    ]);
    currentExecution.value = execution;
    executionLogs.value.push(...logs);
    if (!terminalStates.has(execution.status)) schedulePoll();
  } finally {
    refreshing.value = false;
  }
}

async function loadTargets() {
  targets.value = await getApiDebugTargets();
  if (!targetId.value && targets.value[0])
    targetId.value = targets.value[0].monitorId;
}

async function openCases() {
  cases.value = await getApiDebugCases();
  casesVisible.value = true;
}

async function openHistory() {
  const page = await getApiDebugExecutions({ current: 1, size: 50 });
  history.value = page.records;
  historyTotal.value = page.total;
  historyVisible.value = true;
}

function loadCase(item: ApiDebugCase) {
  try {
    const saved = JSON.parse(item.requestJson) as ApiDebugRequest;
    mode.value = saved.mode;
    scenario.value = saved.scenario ?? 'DOWNSTREAM_TO_UPSTREAM';
    targetId.value = saved.monitorId ?? '';
    method.value = saved.method;
    requestUrl.value = saved.url;
    bodyType.value = saved.bodyType;
    requestBody.value = saved.body ?? '';
    files.value = [];
    timeoutSeconds.value = saved.timeoutSeconds;
    headerRows.value = Object.entries(saved.headers).map(([key, value]) => ({
      enabled: true,
      key,
      value,
    }));
    if (headerRows.value.length === 0) headerRows.value = [emptyRow()];
    casesVisible.value = false;
  } catch {
    ElMessage.error('用例内容无效');
  }
}

async function saveCase() {
  if (!caseName.value.trim()) return;
  await saveApiDebugCase({
    caseName: caseName.value,
    description: caseDescription.value,
    mode: mode.value,
    monitorId: mode.value === 'INTEGRATED' ? targetId.value : undefined,
    requestJson: JSON.stringify(buildRequest()),
    scenario: mode.value === 'INTEGRATED' ? scenario.value : undefined,
  });
  saveCaseVisible.value = false;
  ElMessage.success('调试用例已保存并共享给管理员');
}

async function removeCase(item: ApiDebugCase) {
  await ElMessageBox.confirm(`确认删除用例“${item.caseName}”？`, '删除用例', {
    type: 'warning',
  });
  await deleteApiDebugCase(item.id);
  cases.value = await getApiDebugCases();
}

function loadHistory(item: ApiDebugExecution) {
  currentExecution.value = item;
  historyVisible.value = false;
  executionLogs.value = [];
  void pollExecution();
}

function selectFiles(event: Event) {
  const input = event.target as HTMLInputElement;
  files.value = [...(input.files ?? [])];
}

async function authorizeRawLogs() {
  const result = await authorizeApiDebugLogs(rawPassword.value);
  rawAuthorization.value = result.token;
  rawAuthVisible.value = false;
  rawPassword.value = '';
  rawCursor.value = 0;
  rawLogs.value = [];
  await loadRawLogs();
}

async function openRawLogs() {
  logTab.value = 'raw';
  if (rawAuthorization.value) {
    await loadRawLogs();
  } else {
    rawAuthVisible.value = true;
  }
}

function handleLogTabChange(name: number | string) {
  if (name === 'raw') void openRawLogs();
}

async function loadRawLogs() {
  if (!rawAuthorization.value) {
    rawAuthVisible.value = true;
    return;
  }
  rawLoading.value = true;
  try {
    const result = await getApiDebugRecentLogs(rawAuthorization.value, {
      cursor: rawCursor.value,
      keyword: rawKeyword.value || undefined,
      level: rawLevel.value || undefined,
      logger: rawLogger.value || undefined,
      source: rawSource.value,
    });
    rawLogs.value.push(...result.records);
    rawCursor.value = result.nextCursor;
    if (autoFollow.value) {
      await nextTick();
      rawConsole.value?.scrollTo({
        behavior: 'smooth',
        top: rawConsole.value.scrollHeight,
      });
    }
  } catch {
    rawAuthorization.value = '';
    rawAuthVisible.value = true;
  } finally {
    rawLoading.value = false;
    scheduleRawPoll();
  }
}

function scheduleRawPoll(delay = 2000) {
  if (rawPollTimer) clearTimeout(rawPollTimer);
  if (autoFollow.value && logTab.value === 'raw' && rawAuthorization.value) {
    rawPollTimer = setTimeout(loadRawLogs, delay);
  }
}

watch([targetId, scenario], () => {
  if (mode.value !== 'INTEGRATED' || !selectedTarget.value) return;
  const target = selectedTarget.value;
  const locked = resolveIntegratedTarget(target, scenario.value);
  method.value = locked.method;
  bodyType.value = locked.bodyType;
  requestUrl.value = locked.url;
  requestBody.value =
    scenario.value === 'DOWNSTREAM_TO_UPSTREAM'
      ? JSON.stringify(
          {
            channel_id: target.channelCode,
            clickid: `debug-${crypto.randomUUID()}`,
            event: 'CLICK',
            event_time: Math.floor(Date.now() / 1000),
            monitor_id: target.monitorId,
            oaid: `debug-device-${crypto.randomUUID()}`,
            os: 'android',
            schema_version: '1.0',
          },
          null,
          2,
        )
      : JSON.stringify({ callback_ref: '', event: 'ACTIVATE' }, null, 2);
});

watch(mode, (value) => {
  if (value === 'INTEGRATED') {
    if (selectedTarget.value) targetId.value = selectedTarget.value.monitorId;
  } else {
    method.value = 'GET';
    requestUrl.value = 'https://';
    bodyType.value = 'NONE';
  }
});

watch(bodyType, (value) => {
  if (value !== 'FORM_DATA') files.value = [];
});

watch([rawSource, rawLevel], () => {
  rawCursor.value = 0;
  rawLogs.value = [];
  if (rawAuthorization.value) void loadRawLogs();
});

watch(autoFollow, (enabled) => {
  if (enabled) scheduleRawPoll(100);
  else if (rawPollTimer) clearTimeout(rawPollTimer);
});

onMounted(loadTargets);
onBeforeUnmount(() => {
  if (pollTimer) clearTimeout(pollTimer);
  if (rawPollTimer) clearTimeout(rawPollTimer);
});
</script>

<template>
  <main class="debug-page">
    <header class="workbench-header">
      <div>
        <div class="eyebrow">ADX / INTEGRATION WORKBENCH</div>
        <h1>接口调试工作台</h1>
        <p>从同一条执行时间线验证管理面、OCPX 引擎与真实上下游通信。</p>
      </div>
      <div class="header-actions">
        <el-tag effect="dark" :type="riskType">{{ environment }}</el-tag>
        <el-button @click="openCases">用例</el-button>
        <el-button @click="openHistory">历史</el-button>
        <el-button
          v-access:code="'adx:api-debug:case:write'"
          @click="saveCaseVisible = true"
        >
          保存用例
        </el-button>
      </div>
    </header>

    <el-alert
      class="privacy-alert"
      :closable="false"
      show-icon
      type="warning"
      title="安全例外：请求头、请求体和文本响应将在管理员之间明文共享并保留 7 天；日志仍强制脱敏。"
    />

    <section class="mode-strip" aria-label="执行模式">
      <button
        class="mode-option"
        :class="[{ active: mode === 'INTEGRATED' }]"
        type="button"
        @click="mode = 'INTEGRATED'"
      >
        <strong>已接入链路</strong>
        <span>真实事件、扣费、队列和上下游投递</span>
      </button>
      <button
        class="mode-option"
        :class="[{ active: mode === 'CUSTOM' }]"
        type="button"
        @click="mode = 'CUSTOM'"
      >
        <strong>自定义请求</strong>
        <span>从管理端访问受限公网 HTTPS 目标</span>
      </button>
    </section>

    <section class="workspace-grid">
      <el-card class="panel request-panel" shadow="never">
        <template #header>
          <div class="panel-title">
            <div><span>01</span><strong>请求配置</strong></div>
            <el-tag v-if="mode === 'INTEGRATED'" type="danger" effect="plain">
              完整业务链路
            </el-tag>
          </div>
        </template>

        <div v-if="mode === 'INTEGRATED'" class="target-grid">
          <el-form-item label="已发布路由">
            <el-select v-model="targetId" filterable placeholder="选择监测路由">
              <el-option
                v-for="target in targets"
                :key="target.monitorId"
                :label="`${target.monitorName} · ${target.channelName} → ${target.advertiserName}`"
                :value="target.monitorId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="调试方向">
            <el-select v-model="scenario">
              <el-option
                label="下游反馈 → SCX → 上游"
                value="DOWNSTREAM_TO_UPSTREAM"
              />
              <el-option
                label="上游回调 → SCX → 下游"
                value="UPSTREAM_TO_DOWNSTREAM"
              />
            </el-select>
          </el-form-item>
          <div v-if="selectedTarget" class="route-facts">
            <span>v{{ selectedTarget.routeVersion }}</span>
            <span>{{ selectedTarget.downstreamAdapterCode }}</span>
            <span>{{ selectedTarget.upstreamAdapterCode }}</span>
            <span :class="{ billable: selectedTarget.billingMode === 'CPC' }">
              {{ selectedTarget.billingMode }} · ¥{{ selectedTarget.unitPrice }}
            </span>
          </div>
        </div>

        <div class="request-line">
          <el-select
            v-model="method"
            :disabled="mode === 'INTEGRATED'"
            aria-label="请求方法"
          >
            <el-option
              v-for="item in [
                'GET',
                'POST',
                'PUT',
                'PATCH',
                'DELETE',
                'HEAD',
                'OPTIONS',
              ]"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
          <el-input
            v-model="requestUrl"
            :readonly="mode === 'INTEGRATED'"
            aria-label="请求地址"
            placeholder="https://api.example.com/path"
          />
          <el-select v-model="timeoutSeconds" aria-label="超时时间">
            <el-option
              v-for="item in [1, 3, 5, 10]"
              :key="item"
              :label="`${item}s`"
              :value="item"
            />
          </el-select>
        </div>

        <el-tabs class="request-tabs">
          <el-tab-pane label="Query">
            <div
              v-for="(row, index) in queryRows"
              :key="index"
              class="pair-row"
            >
              <el-checkbox v-model="row.enabled" aria-label="启用参数" />
              <el-input v-model="row.key" placeholder="参数名" />
              <el-input v-model="row.value" placeholder="参数值" />
              <el-button
                aria-label="删除参数"
                text
                @click="removeRow(queryRows, index)"
                >删除</el-button
              >
            </div>
            <el-button text type="primary" @click="addRow(queryRows)"
              >添加参数</el-button
            >
          </el-tab-pane>
          <el-tab-pane label="Headers">
            <div
              v-for="(row, index) in headerRows"
              :key="index"
              class="pair-row"
            >
              <el-checkbox v-model="row.enabled" aria-label="启用请求头" />
              <el-input v-model="row.key" placeholder="Header" />
              <el-input
                v-model="row.value"
                show-password
                type="password"
                placeholder="值（默认遮罩）"
              />
              <el-button
                aria-label="删除请求头"
                text
                @click="removeRow(headerRows, index)"
                >删除</el-button
              >
            </div>
            <el-button text type="primary" @click="addRow(headerRows)"
              >添加请求头</el-button
            >
          </el-tab-pane>
          <el-tab-pane label="Body">
            <div class="body-toolbar">
              <el-radio-group
                v-model="bodyType"
                :disabled="mode === 'INTEGRATED'"
              >
                <el-radio-button value="NONE">None</el-radio-button>
                <el-radio-button value="JSON">JSON</el-radio-button>
                <el-radio-button value="FORM_URLENCODED">Form</el-radio-button>
                <el-radio-button value="FORM_DATA">FormData</el-radio-button>
                <el-radio-button value="TEXT">Text</el-radio-button>
              </el-radio-group>
              <el-button
                v-if="bodyType === 'JSON'"
                text
                type="primary"
                @click="formatJson"
              >
                格式化 JSON
              </el-button>
              <el-button text @click="showBodySecrets = !showBodySecrets">
                {{ showBodySecrets ? '遮罩请求体' : '查看请求体明文' }}
              </el-button>
            </div>
            <div
              v-if="bodyType === 'FORM_DATA' || bodyType === 'FORM_URLENCODED'"
            >
              <div
                v-for="(row, index) in formRows"
                :key="index"
                class="pair-row"
              >
                <el-checkbox v-model="row.enabled" aria-label="启用表单字段" />
                <el-input v-model="row.key" placeholder="字段名" />
                <el-input
                  v-model="row.value"
                  :show-password="!showBodySecrets"
                  :type="showBodySecrets ? 'text' : 'password'"
                  placeholder="字段值（默认遮罩）"
                />
                <el-button text @click="removeRow(formRows, index)"
                  >删除</el-button
                >
              </div>
              <el-button text type="primary" @click="addRow(formRows)"
                >添加字段</el-button
              >
              <label v-if="bodyType === 'FORM_DATA'" class="file-picker">
                <span>选择临时文件</span>
                <input multiple type="file" @change="selectFiles" />
                <small>{{
                  files.length > 0
                    ? `已选择 ${files.length} 个文件`
                    : '文件不会持久化'
                }}</small>
              </label>
            </div>
            <el-input
              v-else-if="bodyType !== 'NONE'"
              v-model="requestBody"
              class="code-input"
              :class="{ 'masked-input': !showBodySecrets }"
              :rows="16"
              resize="vertical"
              type="textarea"
            />
            <el-empty v-else :image-size="48" description="此请求不发送 Body" />
          </el-tab-pane>
        </el-tabs>

        <div class="send-bar">
          <span v-if="selectedTarget && mode === 'INTEGRATED'">
            {{ selectedTarget.channelCode }} / {{ selectedTarget.monitorId }}
          </span>
          <el-button
            v-access:code="'adx:api-debug:execute'"
            type="primary"
            :loading="sending"
            @click="startPreflight"
          >
            服务端预检并发送
          </el-button>
        </div>
      </el-card>

      <el-card class="panel response-panel" shadow="never">
        <template #header>
          <div class="panel-title">
            <div><span>02</span><strong>响应结果</strong></div>
            <div v-if="currentExecution" class="response-stats">
              <el-button
                text
                @click="showResponseSecrets = !showResponseSecrets"
              >
                {{ showResponseSecrets ? '遮罩敏感字段' : '查看响应明文' }}
              </el-button>
              <el-tag
                :type="
                  currentExecution.responseStatus &&
                  currentExecution.responseStatus < 400
                    ? 'success'
                    : 'danger'
                "
              >
                HTTP {{ currentExecution.responseStatus ?? '—' }}
              </el-tag>
              <span>{{ currentExecution.durationMs ?? '—' }} ms</span>
              <span>{{ currentExecution.responseSize ?? 0 }} B</span>
            </div>
          </div>
        </template>

        <div v-if="currentExecution" class="execution-banner">
          <div>
            <small>DEBUG EXECUTION</small>
            <code>{{ currentExecution.id }}</code>
          </div>
          <el-tag
            effect="dark"
            :type="
              terminalStates.has(currentExecution.status)
                ? 'success'
                : 'warning'
            "
          >
            {{ currentExecution.status }}
          </el-tag>
        </div>

        <el-tabs v-if="currentExecution" v-model="responseTab">
          <el-tab-pane label="Body" name="body">
            <pre class="response-code">{{
              displayedResponseBody || '无文本响应体'
            }}</pre>
          </el-tab-pane>
          <el-tab-pane label="Headers" name="headers">
            <pre class="response-code">{{ displayedResponseHeaders }}</pre>
          </el-tab-pane>
          <el-tab-pane label="执行信息" name="meta">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="状态">{{
                currentExecution.status
              }}</el-descriptions-item>
              <el-descriptions-item label="MIME">{{
                currentExecution.responseMime || '—'
              }}</el-descriptions-item>
              <el-descriptions-item label="SHA-256">{{
                currentExecution.responseSha256 || '—'
              }}</el-descriptions-item>
              <el-descriptions-item label="副作用">{{
                currentExecution.sideEffectSummary
              }}</el-descriptions-item>
              <el-descriptions-item label="错误">{{
                currentExecution.errorMessage || '—'
              }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
        </el-tabs>
        <div v-else class="response-empty">
          <div class="pulse-ring"></div>
          <strong>等待执行</strong>
          <p>完成服务端预检和二次确认后，响应与业务副作用会在这里持续更新。</p>
        </div>
      </el-card>
    </section>

    <el-card class="panel log-panel" shadow="never">
      <template #header>
        <div class="panel-title">
          <div><span>03</span><strong>双端诊断日志</strong></div>
          <el-button
            v-if="logTab === 'raw'"
            :loading="rawLoading"
            @click="loadRawLogs"
            >拉取新日志</el-button
          >
        </div>
      </template>
      <el-tabs v-model="logTab" @tab-change="handleLogTabChange">
        <el-tab-pane label="本次执行日志" name="execution">
          <div class="timeline-head">
            <span>序号</span><span>来源</span><span>阶段</span
            ><span>时间 / 内容</span>
          </div>
          <div
            v-if="executionLogs.length > 0"
            class="log-stream"
            aria-live="polite"
          >
            <div
              v-for="item in executionLogs"
              :key="item.sequence"
              class="log-line"
            >
              <code>{{ item.sequence }}</code>
              <el-tag
                size="small"
                :type="item.source === 'ENGINE' ? 'success' : 'primary'"
                >{{ item.source }}</el-tag
              >
              <strong>{{ item.phase }}</strong>
              <span
                ><time>{{ item.createTime }}</time
                >{{ item.message }}</span
              >
            </div>
          </div>
          <el-empty
            v-else
            :image-size="48"
            description="执行后将按同一 debugExecutionId 汇聚双端步骤"
          />
        </el-tab-pane>
        <el-tab-pane label="近期原始日志" name="raw">
          <div class="raw-filters">
            <el-segmented
              v-model="rawSource"
              :options="['MANAGEMENT', 'ENGINE']"
            />
            <el-select v-model="rawLevel" clearable placeholder="级别">
              <el-option
                v-for="item in ['DEBUG', 'INFO', 'WARN', 'ERROR']"
                :key="item"
                :label="item"
                :value="item"
              />
            </el-select>
            <el-input v-model="rawLogger" clearable placeholder="Logger" />
            <el-input v-model="rawKeyword" clearable placeholder="关键词" />
            <el-switch v-model="autoFollow" active-text="自动跟随" />
          </div>
          <div class="raw-warning">
            原始日志可包含其他业务上下文；页面始终执行强制脱敏，访问行为会被审计。
          </div>
          <div ref="rawConsole" class="raw-console">
            <div
              v-for="item in rawLogs"
              :key="`${item.source}-${item.sequence}`"
              class="raw-line"
            >
              <time>{{ item.timestamp }}</time
              ><b :data-level="item.level">{{ item.level }}</b
              ><code>{{ item.logger }}</code
              ><span>{{ item.message }}</span>
            </div>
            <div v-if="rawLogs.length === 0" class="console-empty">
              完成二次认证后读取最近 2 小时、最多 500 行。
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog
      v-model="confirmVisible"
      width="560"
      title="生产级执行确认"
      destroy-on-close
    >
      <el-alert
        :closable="false"
        show-icon
        :type="preflight?.billable ? 'error' : 'warning'"
        :title="preflight?.riskSummary"
      />
      <p class="side-effects">{{ preflight?.sideEffectSummary }}</p>
      <el-form label-position="top">
        <el-form-item label="当前账号密码">
          <el-input
            v-model="password"
            autocomplete="current-password"
            show-password
            type="password"
          />
        </el-form-item>
        <el-form-item label="输入确认短语">
          <code class="confirmation-phrase">{{
            preflight?.confirmationPhrase
          }}</code>
          <el-input v-model="confirmation" autocomplete="off" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="danger" :loading="sending" @click="confirmExecution"
          >确认真实执行</el-button
        >
      </template>
    </el-dialog>

    <el-dialog v-model="rawAuthVisible" width="480" title="查看近期原始日志">
      <el-alert
        title="授权有效 5 分钟，并绑定当前登录会话与 IP。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form-item class="raw-password" label="当前账号密码">
        <el-input
          v-model="rawPassword"
          show-password
          type="password"
          @keyup.enter="authorizeRawLogs"
        />
      </el-form-item>
      <template #footer>
        <el-button @click="rawAuthVisible = false">取消</el-button>
        <el-button type="primary" @click="authorizeRawLogs">授权查看</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="saveCaseVisible" width="480" title="保存共享调试用例">
      <el-alert
        title="请求中的敏感值会按已确认策略明文保存，并对所有管理员可见。"
        type="warning"
        :closable="false"
      />
      <el-form label-position="top">
        <el-form-item label="用例名称"
          ><el-input v-model="caseName" maxlength="120"
        /></el-form-item>
        <el-form-item label="说明"
          ><el-input v-model="caseDescription" maxlength="500" type="textarea"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="saveCaseVisible = false">取消</el-button
        ><el-button type="primary" @click="saveCase">保存</el-button></template
      >
    </el-dialog>

    <el-drawer v-model="casesVisible" size="520" title="共享调试用例">
      <div class="drawer-list">
        <article v-for="item in cases" :key="item.id" class="drawer-item">
          <div>
            <strong>{{ item.caseName }}</strong>
            <p>{{ item.description || '无说明' }}</p>
            <small
              >{{ item.mode }} · {{ item.updatedBy }} ·
              {{ item.updateTime }}</small
            >
          </div>
          <div>
            <el-button type="primary" text @click="loadCase(item)"
              >加载</el-button
            ><el-button
              v-access:code="'adx:api-debug:case:write'"
              type="danger"
              text
              @click="removeCase(item)"
              >删除</el-button
            >
          </div>
        </article>
        <el-empty v-if="cases.length === 0" description="尚未保存调试用例" />
      </div>
    </el-drawer>

    <el-drawer
      v-model="historyVisible"
      size="620"
      :title="`请求历史 · ${historyTotal}`"
    >
      <div class="drawer-list">
        <article
          v-for="item in history"
          :key="item.id"
          class="drawer-item history-item"
          @click="loadHistory(item)"
        >
          <div>
            <strong>{{ item.requestMethod }} · {{ item.requestUrl }}</strong>
            <p>
              <el-tag size="small">{{ item.status }}</el-tag> HTTP
              {{ item.responseStatus ?? '—' }} · {{ item.durationMs ?? '—' }} ms
            </p>
            <small
              >{{ item.id }} · {{ item.createdBy }} ·
              {{ item.createTime }}</small
            >
          </div>
        </article>
        <el-empty
          v-if="history.length === 0"
          description="最近 7 天暂无调试记录"
        />
      </div>
    </el-drawer>
  </main>
</template>

<style scoped>
.debug-page {
  --debug-line: var(--el-border-color-light);
  --debug-surface: var(--el-bg-color);
  --debug-muted: var(--el-text-color-secondary);

  min-height: 100%;
  padding: 20px;
  color: var(--el-text-color-primary);
  background:
    linear-gradient(
      90deg,
      color-mix(in srgb, var(--el-color-primary) 5%, transparent) 1px,
      transparent 1px
    ),
    linear-gradient(
      color-mix(in srgb, var(--el-color-primary) 5%, transparent) 1px,
      transparent 1px
    ),
    var(--el-fill-color-extra-light);
  background-size: 28px 28px;
}

.workbench-header,
.panel-title,
.header-actions,
.request-line,
.send-bar,
.body-toolbar,
.response-stats,
.raw-filters {
  display: flex;
  align-items: center;
}

.workbench-header {
  gap: 20px;
  justify-content: space-between;
  margin-bottom: 16px;
}

.workbench-header h1 {
  margin: 2px 0 4px;
  font-size: 28px;
  letter-spacing: -0.03em;
}

.workbench-header p {
  margin: 0;
  color: var(--debug-muted);
}

.eyebrow {
  font:
    600 11px/1.4 ui-monospace,
    SFMono-Regular,
    Menlo,
    monospace;
  color: var(--el-color-primary);
  letter-spacing: 0.16em;
}

.header-actions {
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.privacy-alert {
  margin-bottom: 14px;
}

.mode-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.mode-option {
  min-height: 72px;
  padding: 14px 16px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--debug-surface);
  border: 1px solid var(--debug-line);
  border-radius: 10px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.mode-option:hover {
  border-color: var(--el-color-primary-light-5);
  transform: translateY(-1px);
}

.mode-option.active {
  border-color: var(--el-color-primary);
  box-shadow:
    inset 3px 0 var(--el-color-primary),
    0 8px 24px color-mix(in srgb, var(--el-color-primary) 10%, transparent);
}

.mode-option strong,
.mode-option span {
  display: block;
}

.mode-option span {
  margin-top: 6px;
  font-size: 13px;
  color: var(--debug-muted);
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(380px, 0.92fr);
  gap: 14px;
}

.panel {
  border-color: var(--debug-line);
  border-radius: 10px;
}

.panel-title {
  gap: 12px;
  justify-content: space-between;
}

.panel-title > div:first-child {
  display: flex;
  gap: 10px;
  align-items: center;
}

.panel-title span:first-child {
  font:
    700 11px ui-monospace,
    SFMono-Regular,
    Menlo,
    monospace;
  color: var(--el-color-primary);
}

.target-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
  padding: 12px;
  margin-bottom: 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--debug-line);
  border-radius: 8px;
}

.route-facts {
  display: flex;
  flex-wrap: wrap;
  grid-column: 1/-1;
  gap: 8px;
}

.route-facts span {
  padding: 3px 7px;
  font:
    11px ui-monospace,
    SFMono-Regular,
    Menlo,
    monospace;
  color: var(--debug-muted);
  border: 1px solid var(--debug-line);
  border-radius: 4px;
}

.route-facts .billable {
  color: var(--el-color-danger);
  border-color: var(--el-color-danger-light-5);
}

.request-line {
  gap: 8px;
}

.request-line > .el-select:first-child {
  flex: none;
  width: 112px;
}

.request-line > .el-select:last-child {
  flex: none;
  width: 82px;
}

.request-tabs {
  margin-top: 12px;
}

.pair-row {
  display: grid;
  grid-template-columns: 26px minmax(100px, 0.8fr) minmax(160px, 1.2fr) 52px;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.body-toolbar {
  flex-wrap: wrap;
  gap: 8px;
  justify-content: space-between;
  margin-bottom: 10px;
}

.code-input :deep(textarea),
.response-code,
.raw-console {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.code-input :deep(textarea) {
  line-height: 1.6;
  tab-size: 2;
}

.masked-input :deep(textarea) {
  -webkit-text-security: disc;
}

.file-picker {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px;
  margin-top: 12px;
  cursor: pointer;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
}

.file-picker input {
  max-width: 240px;
}

.file-picker small {
  color: var(--debug-muted);
}

.send-bar {
  justify-content: space-between;
  min-height: 52px;
  padding-top: 12px;
  margin-top: 14px;
  border-top: 1px solid var(--debug-line);
}

.send-bar > span {
  overflow: hidden;
  text-overflow: ellipsis;
  font:
    11px ui-monospace,
    SFMono-Regular,
    Menlo,
    monospace;
  color: var(--debug-muted);
  white-space: nowrap;
}

.response-stats {
  gap: 10px;
  font:
    12px ui-monospace,
    SFMono-Regular,
    Menlo,
    monospace;
  color: var(--debug-muted);
}

.execution-banner {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 11px 12px;
  margin-bottom: 10px;
  background: var(--el-fill-color-lighter);
  border-left: 3px solid var(--el-color-primary);
}

.execution-banner small,
.execution-banner code {
  display: block;
}

.execution-banner small {
  margin-bottom: 4px;
  font-size: 10px;
  color: var(--debug-muted);
  letter-spacing: 0.12em;
}

.execution-banner code {
  font-size: 12px;
  word-break: break-all;
}

.response-code {
  min-height: 400px;
  max-height: 620px;
  padding: 14px;
  overflow: auto;
  line-height: 1.55;
  color: var(--el-text-color-primary);
  word-break: normal;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  background: var(--el-fill-color-dark);
  border: 1px solid var(--debug-line);
  border-radius: 8px;
}

.response-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 500px;
  color: var(--debug-muted);
  text-align: center;
}

.response-empty strong {
  margin-top: 18px;
  font-size: 18px;
  color: var(--el-text-color-primary);
}

.response-empty p {
  max-width: 340px;
}

.pulse-ring {
  width: 54px;
  height: 54px;
  border: 2px solid var(--el-color-primary-light-7);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: pulse-ring 1.2s ease-in-out infinite;
}

.log-panel {
  margin-top: 14px;
}

.timeline-head,
.log-line {
  display: grid;
  grid-template-columns: 60px 110px 180px minmax(240px, 1fr);
  gap: 10px;
  align-items: start;
}

.timeline-head {
  padding: 0 10px 8px;
  font-size: 11px;
  color: var(--debug-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.log-stream {
  max-height: 360px;
  overflow: auto;
  border: 1px solid var(--debug-line);
  border-radius: 8px;
}

.log-line {
  min-height: 46px;
  padding: 9px 10px;
  font-size: 13px;
  border-bottom: 1px solid var(--debug-line);
}

.log-line:last-child {
  border-bottom: 0;
}

.log-line time {
  display: block;
  margin-bottom: 3px;
  font-size: 11px;
  color: var(--debug-muted);
}

.raw-filters {
  flex-wrap: wrap;
  gap: 8px;
}

.raw-filters > .el-select {
  width: 110px;
}

.raw-filters > .el-input {
  width: 190px;
}

.raw-warning {
  margin: 10px 0;
  font-size: 12px;
  color: var(--el-color-warning);
}

.raw-console {
  min-height: 220px;
  max-height: 400px;
  padding: 10px;
  overflow: auto;
  font-size: 12px;
  color: #d6e2f0;
  background: #0b1220;
  border-radius: 8px;
}

.raw-line {
  display: grid;
  grid-template-columns: 185px 52px 220px minmax(300px, 1fr);
  gap: 8px;
  padding: 4px 2px;
  border-bottom: 1px solid rgb(148 163 184 / 10%);
}

.raw-line time,
.raw-line code {
  color: #8ca3bd;
}

.raw-line b[data-level='ERROR'] {
  color: #fb7185;
}

.raw-line b[data-level='WARN'] {
  color: #fbbf24;
}

.raw-line b[data-level='INFO'] {
  color: #60a5fa;
}

.console-empty {
  display: grid;
  place-items: center;
  min-height: 200px;
  color: #8ca3bd;
}

.side-effects {
  line-height: 1.6;
  color: var(--debug-muted);
}

.confirmation-phrase {
  display: block;
  padding: 8px;
  margin-bottom: 8px;
  color: var(--el-color-danger);
  word-break: break-all;
  background: var(--el-color-danger-light-9);
  border-radius: 6px;
}

.raw-password {
  margin-top: 18px;
}

.drawer-list {
  display: grid;
  gap: 10px;
}

.drawer-item {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 14px;
  border: 1px solid var(--debug-line);
  border-radius: 8px;
}

.drawer-item p {
  margin: 6px 0;
  color: var(--debug-muted);
}

.drawer-item small {
  color: var(--debug-muted);
}

.history-item {
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.history-item:hover {
  background: var(--el-fill-color-lighter);
  border-color: var(--el-color-primary);
}

@keyframes pulse-ring {
  0%,
  100% {
    opacity: 0.7;
    transform: scale(0.94);
  }

  50% {
    opacity: 1;
    transform: scale(1.06);
  }
}

@media (prefers-reduced-motion: reduce) {
  .pulse-ring,
  .mode-option {
    transition: none;
    animation: none;
  }
}

@media (max-width: 1100px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .response-code,
  .response-empty {
    min-height: 280px;
  }

  .target-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .debug-page {
    padding: 12px;
  }

  .workbench-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    justify-content: flex-start;
  }

  .mode-strip {
    grid-template-columns: 1fr;
  }

  .request-line {
    flex-direction: column;
    align-items: stretch;
  }

  .request-line > .el-select:first-child,
  .request-line > .el-select:last-child {
    width: 100%;
  }

  .pair-row {
    grid-template-columns: 26px 1fr 48px;
  }

  .pair-row > .el-input:nth-of-type(2) {
    grid-column: 2/3;
  }

  .timeline-head {
    display: none;
  }

  .log-line {
    grid-template-columns: 48px 92px 1fr;
  }

  .log-line > span {
    grid-column: 1/-1;
  }

  .raw-line {
    grid-template-columns: 1fr 52px;
  }

  .raw-line code,
  .raw-line span {
    grid-column: 1/-1;
  }

  .target-grid {
    grid-template-columns: 1fr;
  }
}
</style>
