<script setup lang="ts">
import type { RtaTestPreview, RtaTestResult } from '#/api/ocpx/rta-test';

import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@boot/access';

import { ElMessage } from 'element-plus';

import {
  executeRtaTest,
  getRtaTestResult,
  previewRtaTest,
} from '#/api/ocpx/rta-test';

const props = defineProps<{ taskId?: string }>();
const emit = defineEmits<{ close: [] }>();
const router = useRouter();
const { hasAccessByCodes } = useAccess();
const preview = ref<RtaTestPreview>();
const result = ref<RtaTestResult>();
const oaid = ref('');
const loading = ref(false);
const running = ref(false);
const submitted = ref(false);
const error = ref('');
let generation = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
let polls = 0;
function stopPolling() {
  if (timer) clearTimeout(timer);
  timer = undefined;
}
function randomOaid() {
  oaid.value = globalThis.crypto.randomUUID();
}
async function reset() {
  const token = ++generation;
  stopPolling();
  polls = 0;
  preview.value = undefined;
  result.value = undefined;
  submitted.value = false;
  running.value = false;
  error.value = '';
  oaid.value = '';
  if (!props.taskId) return;
  loading.value = true;
  try {
    const data = await previewRtaTest(props.taskId);
    if (token === generation) preview.value = data;
  } catch {
    if (token === generation) error.value = '测试配置加载失败，请重新打开';
  } finally {
    if (token === generation) loading.value = false;
  }
}
watch(() => props.taskId, reset, { immediate: true });
onBeforeUnmount(() => {
  generation++;
  stopPolling();
});
const conclusion = computed(() => {
  if (!result.value)
    return submitted.value ? '请求已提交，等待查询结果' : '尚未执行';
  if (result.value.submission !== 'RECEIVED')
    return '同步响应未确认，请核对决策状态';
  if (result.value.ret !== 0) return 'RTA 请求异常';
  return result.value.agreed ? '允许投放' : '未获允许';
});
async function refresh(auto = false) {
  const id = preview.value?.requestId;
  const task = props.taskId;
  const token = generation;
  if (!task || !id || !submitted.value) return;
  stopPolling();
  try {
    const data = await getRtaTestResult(task, id);
    if (token !== generation) return;
    result.value = data;
    if (auto && ++polls < 15 && (!data.status || data.status === 'PENDING'))
      timer = setTimeout(() => void refresh(true), 1000);
  } catch {
    if (token === generation && !auto)
      ElMessage.error('结果暂不可查询，请稍后刷新；不会重发询量');
  }
}
async function execute() {
  if (!props.taskId || !preview.value || submitted.value || !oaid.value.trim())
    return;
  const id = preview.value.requestId;
  const task = props.taskId;
  const token = generation;
  running.value = true;
  submitted.value = true;
  error.value = '';
  try {
    const data = await executeRtaTest(task, id, oaid.value);
    if (token !== generation) return;
    result.value = data;
    void refresh(true);
  } catch {
    if (token === generation)
      error.value =
        '未取得执行结果，请刷新查询；为避免重复询量，本次不会自动重发。';
  } finally {
    if (token === generation) running.value = false;
  }
}
function openLogs() {
  if (!preview.value) return;
  emit('close');
  void router.push({
    path: '/ocpx/rta/logs',
    query: {
      monitorTaskId: preview.value.taskId,
      requestId: preview.value.requestId,
    },
  });
}
</script>
<template>
  <el-dialog
    :model-value="!!taskId"
    title="RTA 决策测试"
    width="min(860px, 96vw)"
    :close-on-click-modal="false"
    @close="emit('close')"
  >
    <div v-loading="loading" class="flex flex-col gap-4">
      <el-alert
        type="info"
        :closable="false"
        title="发送真实 RTA 询量，经过 OPPO 协议校验并请求微风；不发送点击或转化、不产生点击/转化扣费。请求会计入 RTA 记录和统计。"
      />
      <el-alert v-if="error" type="error" :closable="false" :title="error" />
      <template v-if="preview">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="监测任务">{{
            preview.monitorName
          }}</el-descriptions-item>
          <el-descriptions-item label="策略 ID">{{
            preview.strategyId ?? '未生成'
          }}</el-descriptions-item>
          <el-descriptions-item label="媒体"
            >{{ preview.mediaName }} /
            {{ preview.mediaProtocol }}</el-descriptions-item
          >
          <el-descriptions-item label="上游"
            >{{ preview.upstreamName }} /
            {{ preview.upstreamProtocol }}</el-descriptions-item
          >
          <el-descriptions-item label="微风计划"
            >aid={{ preview.plan.aid || '未配置' }} / did={{
              preview.plan.did || '未配置'
            }}</el-descriptions-item
          >
          <el-descriptions-item label="请求 ID">{{
            preview.requestId
          }}</el-descriptions-item>
          <el-descriptions-item label="请求地址" :span="2"
            ><span class="break-all">{{
              preview.endpoint
            }}</span></el-descriptions-item
          >
        </el-descriptions>
        <el-alert
          v-for="blocker in preview.blockers"
          :key="blocker"
          type="warning"
          :closable="false"
          :title="blocker"
        />
        <el-form label-width="90px" @submit.prevent="execute">
          <el-form-item label="OAID">
            <el-input
              v-model="oaid"
              :disabled="submitted"
              :maxlength="256"
              placeholder="填写联调设备 OAID；随机值仅验证格式，不能保证上游允许"
            />
          </el-form-item>
        </el-form>
        <div class="flex flex-wrap gap-2">
          <el-button :disabled="submitted" @click="randomOaid"
            >生成随机 OAID</el-button
          >
          <el-button
            type="primary"
            :loading="running"
            :disabled="submitted || preview.blockers.length > 0 || !oaid.trim()"
            @click="execute"
            >发送 RTA 测试</el-button
          >
          <el-button :disabled="!submitted || running" @click="refresh()"
            >刷新结果</el-button
          >
          <el-button :disabled="running" @click="reset">新建测试</el-button>
        </div>
        <el-divider content-position="left">{{ conclusion }}</el-divider>
        <el-descriptions v-if="result" :column="2" border>
          <el-descriptions-item label="HTTP">{{
            result.httpStatus ?? '未确认'
          }}</el-descriptions-item>
          <el-descriptions-item label="往返耗时"
            >{{ result.durationMs ?? '—' }} ms</el-descriptions-item
          >
          <el-descriptions-item label="上游决策状态">{{
            result.status || '暂未取得'
          }}</el-descriptions-item>
          <el-descriptions-item label="原因码">{{
            result.reasonCode || '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="SCX RTA ID">{{
            result.relayRequestId || '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="供应商请求 ID">{{
            result.providerRequestId || '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="同步状态">{{
            result.deliveryStatus || '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="上游派发次数">{{
            result.upstreamAttempts ?? '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="2">{{
            result.message || '执行中'
          }}</el-descriptions-item>
        </el-descriptions>
        <el-button
          v-if="submitted && hasAccessByCodes(['ocpx:rta:logs:view'])"
          link
          type="primary"
          @click="openLogs"
          >查看 RTA 流程日志</el-button
        >
        <p class="text-sm">
          结果保存24小时；成功流程日志沿用采样规则，可能稍后落库。此测试不能证明
          OPPO 公网接入或点击回传闭环已经完成。
        </p>
      </template>
    </div>
  </el-dialog>
</template>
