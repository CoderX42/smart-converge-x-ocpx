<script setup lang="ts">
import type { RtaLabel } from '../labels';

import type { RtaRow } from '#/api/ocpx/rta';

import { computed } from 'vue';

import {
  decisionModes,
  decisionStatus,
  deliveryStatus,
  dispatchStatus,
  formatTime,
  protocols,
  reasonText,
  tagOf,
} from '../labels';

const props = defineProps<{ record: RtaRow }>();

type Step = {
  description: string;
  tag: RtaLabel;
  time: string;
  title: string;
};

const steps = computed<Step[]>(() => {
  const r = props.record;
  const pending = !r.status || r.status === 'PENDING';
  const sync = ['SYNC_READY', 'SYNC_RETURNED'].includes(r.deliveryStatus || '');
  return [
    {
      description: `媒体请求 ID：${r.requestId || '—'}；SCX 请求 ID：${r.relayRequestId || '—'}`,
      tag: { text: '已受理', type: 'success' },
      time: formatTime(r.receivedAt),
      title: '受理',
    },
    {
      description: `${tagOf(protocols, r.protocol).text} · ${decisionModes[r.decisionMode || ''] || '—'}；上游请求 ID：${r.providerRequestId || '—'}`,
      tag: tagOf(dispatchStatus, r.dispatchStatus),
      time: '',
      title: '派发上游',
    },
    {
      description: pending
        ? `须在 ${formatTime(r.deadline)} 前取得决策，否则判定超时`
        : `原因：${reasonText(r.reasonCode)}；命中任务：${r.matchedTaskIds?.join('、') || '无'}`,
      tag: tagOf(decisionStatus, r.status),
      time: pending ? '' : formatTime(r.completedAt),
      title: '决策',
    },
    {
      description: sync
        ? '在媒体的同一请求内同步返回策略'
        : `通知次数：${r.deliveryAttempts ?? 0}`,
      tag: tagOf(deliveryStatus, r.deliveryStatus),
      time: '',
      title: sync ? 'OPPO 同步返回' : '通知媒体',
    },
    {
      description:
        r.status === 'ALLOW'
          ? '失效前的点击、转化可通过 RTA 放行校验'
          : '未放行，后续点击、转化不会通过 RTA 校验',
      tag:
        r.status === 'ALLOW'
          ? { text: '放行中', type: 'success' }
          : { text: '不适用', type: 'info' },
      time: r.status === 'ALLOW' ? formatTime(r.expiresAt) : '',
      title: '放行失效',
    },
  ];
});
</script>

<template>
  <el-timeline>
    <el-timeline-item
      v-for="step in steps"
      :key="step.title"
      :type="step.tag.type"
      :timestamp="step.time"
      placement="top"
    >
      <div class="rta-step">
        <strong>{{ step.title }}</strong>
        <el-tag size="small" :type="step.tag.type">{{ step.tag.text }}</el-tag>
      </div>
      <p class="rta-step__desc">{{ step.description }}</p>
    </el-timeline-item>
  </el-timeline>
</template>

<style scoped>
.rta-step {
  display: flex;
  gap: 8px;
  align-items: center;
}

.rta-step__desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}
</style>
