<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  getAttributionReviews,
  getMedia,
  getTrackingTasks,
  reviewAttribution,
} from '#/api/ad';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdAttributionReview' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const reviewStatus = ref('PENDING');
const overLimit = ref<string>('');
const callbackStatus = ref('');
const taskId = ref('');
const dates = ref<string[]>([]);
const tasks = ref<any[]>([]);
const rows = ref<any[]>([]);
const loading = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);

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

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const result = await getAttributionReviews({
      current: current.value,
      mediaId: mediaId.value,
      overLimit:
        overLimit.value === '' ? undefined : overLimit.value === 'true',
      reviewStatus: reviewStatus.value || undefined,
      callbackStatus: callbackStatus.value || undefined,
      taskId: taskId.value || undefined,
      startDate: dates.value[0] || undefined,
      endDate: dates.value[1] || undefined,
      size: size.value,
    });
    rows.value = result.records || [];
    total.value = result.total || 0;
  } finally {
    loading.value = false;
  }
}

async function decide(row: any, status: 'APPROVED' | 'REDUCED') {
  let reason: string | undefined;
  if (status === 'REDUCED') {
    const { value } = await ElMessageBox.prompt(
      '审减会从效果报表中移除该转化，并创建一条冲正回传任务。',
      '填写审减原因',
      {
        inputValidator: (text) => Boolean(text?.trim()) || '必须填写审减原因',
      },
    );
    reason = value;
  } else {
    await ElMessageBox.confirm(
      row.overLimit
        ? '通过超量转化会将其计入后续日报，并生成原始回传任务。'
        : '确认该转化审核通过？',
      '审核通过',
      { type: 'warning' },
    );
  }
  await reviewAttribution(row.id, mediaId.value, { reason, status });
  ElMessage.success(
    status === 'APPROVED' ? '已审核通过' : '已审减并创建冲正回传',
  );
  await load();
}

function reviewTag(value: string) {
  if (value === 'APPROVED') return 'success';
  if (value === 'REDUCED') return 'danger';
  return 'warning';
}

function callbackTag(value: string) {
  if (value === 'SUCCEEDED') return 'success';
  if (value === 'DEAD') return 'danger';
  if (value === 'RETRYING' || value === 'PAUSED') return 'warning';
  return 'info';
}

watch(
  [mediaId, reviewStatus, overLimit, callbackStatus, taskId, dates],
  () => {
    current.value = 1;
    load();
  },
  { deep: true },
);
watch(mediaId, loadTasks);
bootstrap().then(async () => {
  await loadTasks();
  await load();
});
</script>

<template>
  <main class="mvp-page">
    <header class="mvp-hero">
      <div>
        <div class="mvp-kicker">M5 / Settlement Control</div>
        <h1 class="mvp-title">转化审核与审减</h1>
        <p class="mvp-subtitle">
          超量转化不会自动回传；审核通过后进入原始回传，审减后从后续日报口径剔除并创建冲正任务。
        </p>
      </div>
      <div class="mvp-control">
        <div class="mb-2 text-xs text-white/50">媒体安全范围</div>
        <el-select v-model="mediaId" class="w-full">
          <el-option
            v-for="item in media"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </div>
    </header>

    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-select
            v-model="reviewStatus"
            clearable
            placeholder="全部审核状态"
            style="width: 150px"
          >
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已审减" value="REDUCED" />
          </el-select>
          <el-select
            v-model="overLimit"
            clearable
            placeholder="全部限量结果"
            style="width: 150px"
          >
            <el-option label="仅超量" value="true" />
            <el-option label="未超量" value="false" />
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
          <el-select
            v-model="callbackStatus"
            clearable
            placeholder="全部回传状态"
            style="width: 150px"
          >
            <el-option label="待发送" value="PENDING" />
            <el-option label="重试中" value="RETRYING" />
            <el-option label="已成功" value="SUCCEEDED" />
            <el-option label="已暂停" value="PAUSED" />
            <el-option label="已死信" value="DEAD" />
          </el-select>
          <el-date-picker
            v-model="dates"
            end-placeholder="结束日期"
            start-placeholder="开始日期"
            style="width: 250px"
            type="daterange"
            value-format="YYYY-MM-DD"
          />
          <el-button @click="load">查询</el-button>
        </div>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="转化事件" min-width="190">
          <template #default="{ row }">
            <div class="mvp-code">{{ row.conversionEventId }}</div>
            <div class="text-xs text-gray-500">
              {{ row.conversionEventType }} · {{ row.conversionOccurredAt }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="任务" min-width="180">
          <template #default="{ row }">
            <div>{{ row.taskName }}</div>
            <div class="mvp-code text-xs">{{ row.taskCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="点击归因" min-width="175">
          <template #default="{ row }">
            <div class="mvp-code">{{ row.clickEventId || '—' }}</div>
            <div class="text-xs text-gray-500">
              {{ row.clickOccurredAt || '未匹配' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="超量" width="90">
          <template #default="{ row }">
            <el-tag :type="row.overLimit ? 'danger' : 'success'" effect="plain">
              {{ row.overLimit ? '超量' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核结论" width="110">
          <template #default="{ row }">
            <el-tag :type="reviewTag(row.reviewStatus)" effect="plain">
              {{ row.reviewStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审减原因" min-width="170">
          <template #default="{ row }">{{ row.reviewReason || '—' }}</template>
        </el-table-column>
        <el-table-column label="最新媒体回传" min-width="175">
          <template #default="{ row }">
            <el-tag
              v-if="row.callbackStatus"
              :type="callbackTag(row.callbackStatus)"
              effect="plain"
            >
              {{ row.callbackActionType }} · {{ row.callbackStatus }}
            </el-tag>
            <span v-else>未创建</span>
            <div v-if="row.callbackStatus" class="mt-1 text-xs text-gray-500">
              尝试 {{ row.callbackAttempts || 0 }} 次 ·
              {{ row.callbackUpdatedAt }}
            </div>
            <el-tooltip
              v-if="row.callbackLastErrorCode"
              :content="
                row.callbackLastErrorMessage || row.callbackLastErrorCode
              "
            >
              <div class="mvp-code mt-1 text-xs text-red-500">
                {{ row.callbackLastErrorCode }}
              </div>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column align="right" fixed="right" label="操作" width="150">
          <template #default="{ row }">
            <template v-if="row.reviewStatus === 'PENDING'">
              <el-button
                v-access:code="'ad:attribution:review'"
                link
                type="primary"
                @click="decide(row, 'APPROVED')"
              >
                通过
              </el-button>
              <el-button
                v-access:code="'ad:attribution:review'"
                link
                type="danger"
                @click="decide(row, 'REDUCED')"
              >
                审减
              </el-button>
            </template>
            <span v-else class="text-xs text-gray-400">{{
              row.reviewedBy || '已完成'
            }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">当前筛选范围内暂无归因转化</div>
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
  </main>
</template>
