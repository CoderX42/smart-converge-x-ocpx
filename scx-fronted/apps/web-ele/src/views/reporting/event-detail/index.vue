<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { ref, watch } from 'vue';

import {
  getEventDetails,
  getMaskedClickDetail,
  getMedia,
  getTrackingTasks,
} from '#/api/ad';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdEventDetail' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const reportDate = ref(new Date().toISOString().slice(0, 10));
const eventType = ref('');
const taskId = ref('');
const eventId = ref('');
const dedupStatus = ref('');
const attributionStatus = ref('');
const tasks = ref<any[]>([]);
const rows = ref<any[]>([]);
const loading = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const sensitive = ref<any>({});
const sensitiveVisible = ref(false);
const sensitiveLoading = ref(false);

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function loadTasks() {
  if (!mediaId.value) return;
  const result = await getTrackingTasks({
    current: 1,
    mediaId: mediaId.value,
    size: 200,
  });
  tasks.value = result.records || [];
  if (taskId.value && !tasks.value.some((item) => item.id === taskId.value)) {
    taskId.value = '';
  }
}

async function openSensitive(row: any) {
  sensitiveVisible.value = true;
  sensitive.value = {};
  sensitiveLoading.value = true;
  try {
    sensitive.value = await getMaskedClickDetail(row.rawEventId, mediaId.value);
  } finally {
    sensitiveLoading.value = false;
  }
}

async function load() {
  if (!mediaId.value || !reportDate.value) return;
  loading.value = true;
  try {
    const result = await getEventDetails({
      current: current.value,
      date: reportDate.value,
      eventType: eventType.value || undefined,
      mediaId: mediaId.value,
      taskId: taskId.value || undefined,
      eventId: eventId.value.trim() || undefined,
      dedupStatus: dedupStatus.value || undefined,
      attributionStatus: attributionStatus.value || undefined,
      size: size.value,
    });
    rows.value = result.records || [];
    total.value = result.total || 0;
  } finally {
    loading.value = false;
  }
}

function tagType(status?: string) {
  if (status === 'ATTRIBUTED' || status === 'ELIGIBLE') return 'success';
  if (status === 'DEDUPLICATED' || status === 'UNATTRIBUTED') return 'warning';
  if (status === 'REJECTED') return 'danger';
  return 'info';
}

watch(
  [mediaId, reportDate, eventType, taskId, dedupStatus, attributionStatus],
  () => {
    current.value = 1;
    load();
  },
);
watch(mediaId, loadTasks);
bootstrap().then(async () => {
  await loadTasks();
  await load();
});
</script>

<template>
  <main class="mvp-page">
    <div class="mvp-scope-toolbar">
      <div class="mvp-control">
        <div class="mvp-scope-label mb-2 text-xs">媒体安全范围</div>
        <el-select v-model="mediaId" class="w-full">
          <el-option
            v-for="item in media"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </div>
    </div>

    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-date-picker
            v-model="reportDate"
            type="date"
            value-format="YYYY-MM-DD"
          />
          <el-select
            v-model="eventType"
            clearable
            placeholder="全部事件"
            style="width: 150px"
          >
            <el-option
              v-for="item in [
                'IMPRESSION',
                'CLICK',
                'ACTIVATE',
                'REGISTER',
                'PAY',
              ]"
              :key="item"
              :value="item"
            />
          </el-select>
          <el-select
            v-model="taskId"
            clearable
            filterable
            placeholder="全部任务"
            style="width: 190px"
          >
            <el-option
              v-for="item in tasks"
              :key="item.id"
              :label="`${item.name} (${item.code})`"
              :value="item.id"
            />
          </el-select>
          <el-input
            v-model="eventId"
            clearable
            placeholder="事件 ID 或 click_id"
            style="width: 190px"
            @keyup.enter="load"
          />
          <el-select
            v-model="dedupStatus"
            clearable
            placeholder="全部去重结论"
            style="width: 150px"
          >
            <el-option label="有效" value="ELIGIBLE" />
            <el-option label="重复" value="DEDUPLICATED" />
          </el-select>
          <el-select
            v-model="attributionStatus"
            clearable
            placeholder="全部归因结论"
            style="width: 150px"
          >
            <el-option label="已归因" value="ATTRIBUTED" />
            <el-option label="未归因" value="UNATTRIBUTED" />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
        <div class="text-xs text-gray-400">received_at 为系统审计时间</div>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="rawEventId">
        <el-table-column label="事件" min-width="190">
          <template #default="{ row }">
            <strong>{{ row.eventType }}</strong>
            <div class="mvp-code mt-1">{{ row.eventId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="任务" min-width="150">
          <template #default="{ row }">
            {{ row.taskCode }}
            <div class="mvp-code mt-1">{{ row.source }}</div>
          </template>
        </el-table-column>
        <el-table-column label="设备标识" min-width="150">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.deviceKeyMasked || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="接入" width="120">
          <template #default="{ row }">
            <el-tag effect="plain" :type="tagType(row.ingestionStatus)">
              {{ row.ingestionStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="去重" width="130">
          <template #default="{ row }">
            <el-tag
              v-if="row.dedupStatus"
              effect="plain"
              :type="tagType(row.dedupStatus)"
            >
              {{ row.dedupStatus }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="归因" width="130">
          <template #default="{ row }">
            <el-tag
              v-if="row.attributionStatus"
              effect="plain"
              :type="tagType(row.attributionStatus)"
            >
              {{ row.attributionStatus }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="结论原因" min-width="160">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.reasonCode || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发生时间" prop="occurredAt" min-width="180" />
        <el-table-column label="接收时间" prop="receivedAt" min-width="180" />
        <el-table-column align="right" fixed="right" label="操作" width="105">
          <template #default="{ row }">
            <el-button
              v-if="row.eventType === 'CLICK'"
              v-access:code="'ad:event-sensitive:read'"
              link
              type="primary"
              @click="openSensitive(row)"
            >
              字段摘要
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">筛选范围内暂无事件事实</div>
        </template>
      </el-table>
      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          background
          layout="total, prev, pager, next"
          :total="total"
          @change="load"
        />
      </div>
    </el-card>
    <el-dialog
      v-model="sensitiveVisible"
      title="点击字段摘要（已脱敏）"
      width="620px"
    >
      <el-descriptions v-loading="sensitiveLoading" :column="2" border>
        <el-descriptions-item label="click_id">
          <span class="mvp-code">{{ sensitive.clickIdMasked || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="OAID">
          <span class="mvp-code">{{ sensitive.oaidMasked || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="OAID_MD5">
          <span class="mvp-code">{{ sensitive.oaidMd5Masked || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="IP">
          <span class="mvp-code">{{ sensitive.ipMasked || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="设备型号">
          {{ sensitive.deviceModel || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="计划 ID">
          <span class="mvp-code">{{ sensitive.campaignIdMasked || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="创意 ID">
          <span class="mvp-code">{{ sensitive.creativeIdMasked || '—' }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="sensitiveVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </main>
</template>
