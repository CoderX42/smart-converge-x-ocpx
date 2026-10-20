<script setup lang="ts">
import type { RtaOpenTarget } from './types';

import type { RtaCheck, RtaProtocol, RtaRoute, RtaRow } from '#/api/ocpx/rta';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteRta,
  queryRta,
  rtaOptions,
  rtaProtocols,
  rtaReadiness,
  saveRta,
} from '#/api/ocpx/rta';

import {
  checkLevel,
  protocols as protocolLabels,
  routeStatus,
  segmentState,
  tagOf,
} from '../labels';
import RtaTestDialog from '../test-dialog.vue';
import { openPermission, openTargetOf } from './types';
import { bindingErrors } from './validation';

const props = defineProps<{ taskId: string }>();
const emit = defineEmits<{ open: [RtaOpenTarget]; saved: [] }>();
const { hasAccessByCodes } = useAccess();
const canEdit = computed(() => hasAccessByCodes(['ocpx:rta:bindings:edit']));
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const testTaskId = ref('');
const binding = ref<RtaRow>();
const route = ref<RtaRoute>();
const notices = ref<RtaCheck[]>([]);
const protocols = ref<RtaProtocol[]>([]);
const upstreams = ref<Array<{ id: string; name: string; protocol?: string }>>(
  [],
);
const form = reactive<RtaRow>({});
const selectedProtocol = computed(() => {
  const code =
    upstreams.value.find((item) => item.id === form.upstreamId)?.protocol ||
    binding.value?.protocol;
  return protocols.value.find((item) => item.code === code);
});
const mediaProtocol = computed(
  () => route.value?.mediaProtocol || binding.value?.mediaProtocol,
);
const isOppo = computed(() => mediaProtocol.value === 'OPPO');
const bound = computed(() => Number(binding.value?.version || 0) > 0);
const canTest = computed(
  () =>
    bound.value &&
    isOppo.value &&
    route.value?.upstreamProtocol === 'WEIFENG_RTA',
);
const checks = computed(() => [
  ...(route.value?.checks || []),
  ...notices.value,
]);
const segments = computed(() => {
  const r = route.value;
  if (!r) return [];
  return [
    {
      fix: openTargetOf('media', r),
      label: '媒体接入',
      name: r.mediaName,
      protocol: r.mediaProtocol,
      segment: r.media,
    },
    {
      fix: openTargetOf('upstream', r),
      label: '广告主接入',
      name: r.upstreamName || r.advertiserName,
      protocol: r.upstreamProtocol,
      segment: r.upstream,
    },
    {
      fix: undefined,
      label: '任务绑定',
      name: r.monitorName,
      protocol: undefined,
      segment: r.binding,
    },
  ];
});

function fixOf(check: RtaCheck) {
  return route.value ? openTargetOf(check.fixTarget, route.value) : undefined;
}

function canOpen(target?: RtaOpenTarget): target is RtaOpenTarget {
  return !!target && hasAccessByCodes([openPermission(target)]);
}

function fill(row?: RtaRow) {
  for (const key of Object.keys(form)) delete form[key as keyof RtaRow];
  Object.assign(
    form,
    {
      enabled: false,
      externalTaskId: '',
      monitorTaskId: props.taskId,
      oppoPkg: '',
      productId: '',
      upstreamId: '',
      version: 0,
    },
    row,
    { enabled: Boolean(row?.enabled), monitorTaskId: props.taskId },
  );
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [page, readiness] = await Promise.all([
      queryRta('bindings', {
        current: 1,
        monitorTaskId: props.taskId,
        size: 1,
      }),
      rtaReadiness(props.taskId),
    ]);
    binding.value = page.records.find(
      (row) => row.monitorTaskId === props.taskId,
    );
    route.value = readiness.route;
    notices.value = readiness.notices;
    fill(binding.value);
    upstreams.value = await rtaOptions('upstreams', {
      advertiserId: readiness.route.advertiserId,
    });
  } catch (error_) {
    error.value =
      error_ instanceof Error ? error_.message : '加载失败，请重新打开';
  } finally {
    loading.value = false;
  }
}

function changeUpstream() {
  form.productId = '';
  form.externalTaskId = '';
}

async function save() {
  const errors = bindingErrors(
    form,
    selectedProtocol.value,
    mediaProtocol.value,
  );
  if (errors.length > 0) {
    ElMessage.warning(errors[0]!);
    return;
  }
  saving.value = true;
  try {
    await saveRta('bindings', form);
    ElMessage.success('任务 RTA 绑定已保存');
    await load();
    emit('saved');
  } finally {
    saving.value = false;
  }
}

async function unbind() {
  if (!binding.value || !bound.value) return;
  try {
    await ElMessageBox.confirm(
      `解绑后任务不再要求 RTA 准入${isOppo.value ? '；重新绑定会分配新的 OPPO 策略 ID，需重新交付给媒体' : ''}。`,
      '解除 RTA 绑定',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteRta('bindings', props.taskId, Number(binding.value.version));
  ElMessage.success('已解除 RTA 绑定');
  await load();
  emit('saved');
}

async function copy(text?: string) {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制');
  } catch {
    ElMessage.error('复制失败，请手动复制');
  }
}

onMounted(async () => {
  try {
    protocols.value = await rtaProtocols();
  } catch {
    error.value = '协议加载失败，请关闭后重试';
    return;
  }
  await load();
});
</script>

<template>
  <div v-loading="loading" class="rta-form">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="route">
      <div class="rta-binding__head">
        <div>
          <strong>{{ route.monitorName }}</strong>
          <span class="rta-hint">
            {{ route.advertiserName }} / {{ route.mediaName }}</span
          >
        </div>
        <el-tag :type="tagOf(routeStatus, route.status).type" effect="dark">{{
          tagOf(routeStatus, route.status).text
        }}</el-tag>
      </div>
      <div class="rta-chain">
        <div
          v-for="item in segments"
          :key="item.label"
          class="rta-chain__item"
          :class="[`is-${item.segment.state.toLowerCase()}`]"
        >
          <span class="rta-hint">{{ item.label }}</span>
          <strong>{{ item.name || '—' }}</strong>
          <span>
            <el-tag
              size="small"
              :type="tagOf(segmentState, item.segment.state).type"
              >{{ tagOf(segmentState, item.segment.state).text }}</el-tag
            >
            <el-tag
              v-if="item.protocol"
              size="small"
              class="ml-1"
              :type="tagOf(protocolLabels, item.protocol).type"
              >{{ tagOf(protocolLabels, item.protocol).text }}</el-tag
            >
          </span>
          <el-button
            v-if="canOpen(item.fix)"
            link
            type="primary"
            @click="emit('open', item.fix!)"
            >{{
              item.segment.state === 'MISSING' ? '去配置' : '查看配置'
            }}</el-button
          >
        </div>
      </div>

      <p class="rta-section-title">映射配置</p>
      <el-alert
        v-if="upstreams.length === 0"
        type="warning"
        :closable="false"
        title="该广告主还没有 RTA 接入，请先新增广告主接入再回来绑定。"
      />
      <el-form label-position="top" :disabled="!canEdit" @submit.prevent="save">
        <el-form-item label="广告主接入" required>
          <el-select
            v-model="form.upstreamId"
            filterable
            placeholder="选择该广告主的 RTA 接入"
            @change="changeUpstream"
          >
            <el-option
              v-for="item in upstreams"
              :key="item.id"
              :label="`${item.name}（${tagOf(protocolLabels, item.protocol).text}）`"
              :value="item.id"
              :disabled="isOppo && item.protocol !== 'WEIFENG_RTA'"
            />
          </el-select>
          <p v-if="isOppo" class="rta-hint">OPPO 媒体只能绑定微风 RTA 接入。</p>
        </el-form-item>
        <el-form-item
          v-for="field in selectedProtocol?.bindingFields || []"
          :key="field.key"
          :label="field.label"
          required
        >
          <el-input
            v-model="form[field.key as 'productId' | 'externalTaskId']"
            maxlength="128"
          />
        </el-form-item>
        <el-alert
          v-if="selectedProtocol && selectedProtocol.bindingFields.length === 0"
          class="mb-4"
          :closable="false"
          title="使用所选接入的广告计划；保存时会校验 RTA 与推广链接的计划参数一致。"
        />
        <template v-if="isOppo">
          <el-form-item label="OPPO 策略 ID">
            <el-input
              :model-value="
                form.strategyId ? String(form.strategyId) : '首次保存后生成'
              "
              readonly
            />
          </el-form-item>
          <el-form-item label="投放应用包名">
            <el-input
              v-model="form.oppoPkg"
              maxlength="128"
              placeholder="转化回传时必填，如 com.example.app"
            />
          </el-form-item>
          <el-form-item v-if="binding?.clickUrl" label="OPPO 点击监测链接">
            <el-input
              :model-value="binding.clickUrl"
              type="textarea"
              :rows="3"
              readonly
            />
            <el-button
              class="mt-1"
              link
              type="primary"
              @click="copy(binding.clickUrl)"
              >复制点击监测链接</el-button
            >
          </el-form-item>
        </template>
        <el-form-item label="状态">
          <el-switch
            v-model="form.enabled"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <div class="rta-actions">
        <el-button
          v-if="canTest && hasAccessByCodes(['ocpx:rta:bindings:view'])"
          @click="testTaskId = taskId"
          >RTA 测试</el-button
        >
        <el-button
          v-if="canEdit && bound"
          type="danger"
          plain
          :disabled="saving"
          @click="unbind"
          >解除绑定</el-button
        >
        <el-button
          v-if="canEdit"
          type="primary"
          :loading="saving"
          :disabled="upstreams.length === 0"
          @click="save"
          >保存绑定</el-button
        >
      </div>

      <p class="rta-section-title">就绪检查</p>
      <ul class="rta-checks">
        <li v-for="check in checks" :key="check.code">
          <el-tag size="small" :type="tagOf(checkLevel, check.level).type">{{
            tagOf(checkLevel, check.level).text
          }}</el-tag>
          <div>
            <strong>{{ check.label }}</strong>
            <p v-if="check.hint" class="rta-hint">{{ check.hint }}</p>
          </div>
          <el-button
            v-if="check.level !== 'OK' && canOpen(fixOf(check))"
            link
            type="primary"
            @click="emit('open', fixOf(check)!)"
            >修复</el-button
          >
        </li>
      </ul>
    </template>
    <RtaTestDialog :task-id="testTaskId" @close="testTaskId = ''" />
  </div>
</template>

<style scoped src="./form.css"></style>
<style scoped>
.rta-binding__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rta-binding__head .rta-hint {
  margin-left: 8px;
}

.rta-chain {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.rta-chain__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-top: 3px solid var(--el-color-success);
  border-radius: 8px;
}

.rta-chain__item.is-missing {
  border-top-color: var(--el-color-info);
}

.rta-chain__item.is-disabled {
  border-top-color: var(--el-color-warning);
}

.rta-checks {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.rta-checks li {
  display: grid;
  grid-template-columns: 48px 1fr auto;
  gap: 8px;
  align-items: start;
  padding: 8px 10px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

@media (width <= 768px) {
  .rta-chain {
    grid-template-columns: 1fr;
  }
}
</style>
