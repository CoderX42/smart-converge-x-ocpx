<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { ref, watch } from 'vue';

import { getAttributionResults, getCallbackResults, getMedia } from '#/api/ad';
import { shanghaiDate } from '#/utils/report-date';

import SummaryToolbar from './SummaryToolbar.vue';

import '../../delivery/components/mvp-page.css';

const props = defineProps<{ mode: 'ATTRIBUTION' | 'CALLBACK' }>();

const media = ref<Media[]>([]);
const mediaId = ref('');
const dates = ref<string[]>([shanghaiDate(-6), shanghaiDate()]);
const rows = ref<any[]>([]);
const loading = ref(false);
const status = ref('');
const current = ref(1);
const size = ref(20);
const total = ref(0);

async function load() {
  const [startDate, endDate] = dates.value;
  if (!mediaId.value || !startDate || !endDate) return;
  loading.value = true;
  try {
    const query = {
      current: current.value,
      endDate,
      mediaId: mediaId.value,
      size: size.value,
      startDate,
      status: status.value || undefined,
    };
    const result =
      props.mode === 'CALLBACK'
        ? await getCallbackResults(query)
        : await getAttributionResults(query);
    rows.value = result.records || [];
    total.value = result.total || 0;
  } finally {
    loading.value = false;
  }
}

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
  await load();
}

function reset() {
  status.value = '';
}

watch(
  [mediaId, dates, status],
  () => {
    current.value = 1;
    load();
  },
  { deep: true },
);
bootstrap();
</script>

<template>
  <main class="mvp-page">
    <SummaryToolbar
      v-model:media-id="mediaId"
      v-model:dates="dates"
      :media="media"
      @reset="reset"
    />
    <el-card v-loading="loading" class="mvp-card" shadow="never">
      <template #header>
        <div class="mvp-toolbar">
          <div>
            <div class="font-semibold">
              {{ mode === 'CALLBACK' ? '回传归因结果' : '事件归因结果' }}
            </div>
            <div class="mt-1 text-xs text-gray-500">
              直接读取事件、归因和回传事实，不使用日报汇总替代明细。
            </div>
          </div>
          <div class="flex gap-2">
            <el-select
              v-model="status"
              class="!w-36"
              clearable
              placeholder="状态"
            >
              <template v-if="mode === 'CALLBACK'">
                <el-option label="待处理" value="PENDING" />
                <el-option label="重试中" value="RETRYING" />
                <el-option label="成功" value="SUCCEEDED" />
                <el-option label="终止" value="DEAD" />
              </template>
              <template v-else>
                <el-option label="已归因" value="ATTRIBUTED" />
                <el-option label="未归因" value="UNATTRIBUTED" />
                <el-option label="无效" value="INVALID" />
              </template>
            </el-select>
            <el-button @click="load">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="rows" row-key="callbackJobId || attributionId">
        <el-table-column label="事件" min-width="190">
          <template #default="{ row }">
            <div>{{ row.eventType }} · {{ row.eventId }}</div>
            <div class="mvp-code text-xs">{{ row.rawEventId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="任务" min-width="150">
          <template #default="{ row }">
            <div>{{ row.taskName || row.taskId }}</div>
            <div class="mvp-code text-xs">{{ row.taskId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="发生时间" prop="occurredAt" min-width="170" />
        <template v-if="mode === 'ATTRIBUTION'">
          <el-table-column label="状态" prop="status" width="110" />
          <el-table-column label="原因码" prop="reasonCode" min-width="140" />
          <el-table-column label="Revision" prop="revision" width="95" />
          <el-table-column
            label="命中点击"
            prop="clickEventId"
            min-width="170"
          />
          <el-table-column label="审减" min-width="145">
            <template #default="{ row }">
              {{ row.overLimit ? '超限' : '正常' }} / {{ row.reviewStatus }}
            </template>
          </el-table-column>
          <el-table-column
            label="归因时间"
            prop="attributedAt"
            min-width="170"
          />
        </template>
        <template v-else>
          <el-table-column label="目标" prop="targetRef" min-width="150" />
          <el-table-column label="状态" prop="status" width="110" />
          <el-table-column label="次数" prop="attempts" width="80" />
          <el-table-column label="最后错误" min-width="220">
            <template #default="{ row }">
              {{ row.lastErrorCode || '—' }}
              <span v-if="row.lastErrorMessage">
                · {{ row.lastErrorMessage }}</span
              >
            </template>
          </el-table-column>
          <el-table-column
            label="下次重试"
            prop="nextRetryAt"
            min-width="170"
          />
          <el-table-column
            label="完成时间"
            prop="completedAt"
            min-width="170"
          />
        </template>
        <template #empty>
          <div class="mvp-empty">当前范围内没有事实记录</div>
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
