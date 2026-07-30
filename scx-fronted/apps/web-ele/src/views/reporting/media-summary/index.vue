<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { computed, ref, watch } from 'vue';

import {
  getMaster,
  getMedia,
  getReportBreakdown,
  getTrackingTasks,
} from '#/api/ad';

import SummaryExportButton from '../components/SummaryExportButton.vue';
import SummaryToolbar from '../components/SummaryToolbar.vue';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdMediaSummary' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const dates = ref<string[]>([]);
const products = ref<any[]>([]);
const channels = ref<any[]>([]);
const documents = ref<any[]>([]);
const tasks = ref<any[]>([]);
const productId = ref('');
const channelId = ref('');
const documentId = ref('');
const taskId = ref('');
const loading = ref(false);
const channelRows = ref<any[]>([]);
const documentRows = ref<any[]>([]);
const taskRows = ref<any[]>([]);
const activeView = ref<'CHANNEL' | 'DOCUMENT' | 'TASK'>('CHANNEL');

const rows = computed(() => {
  if (activeView.value === 'DOCUMENT') return documentRows.value;
  if (activeView.value === 'TASK') return taskRows.value;
  return channelRows.value;
});

const totals = computed(() => {
  const total = { activate: 0, click: 0, impression: 0, pay: 0, register: 0 };
  for (const row of rows.value) {
    total.activate += Number(row.attributedActivateCount || 0);
    total.click += Number(row.uniqueClickCount || 0);
    total.impression += Number(row.impressionCount || 0);
    total.pay += Number(row.attributedPayCount || 0);
    total.register += Number(row.attributedRegisterCount || 0);
  }
  return total;
});

function rate(numerator: number, denominator: number) {
  return denominator ? `${((numerator / denominator) * 100).toFixed(2)}%` : '—';
}

async function load() {
  const [startDate, endDate] = dates.value;
  if (!mediaId.value || !startDate || !endDate) return;
  loading.value = true;
  try {
    const base = {
      channelId: channelId.value || undefined,
      documentId: documentId.value || undefined,
      endDate,
      mediaId: mediaId.value,
      productId: productId.value || undefined,
      startDate,
      taskId: taskId.value || undefined,
    };
    const [channels, documents, tasks] = await Promise.all([
      getReportBreakdown({ ...base, dimension: 'CHANNEL' }),
      getReportBreakdown({ ...base, dimension: 'DOCUMENT' }),
      getReportBreakdown({ ...base, dimension: 'TASK' }),
    ]);
    channelRows.value = channels;
    documentRows.value = documents;
    taskRows.value = tasks;
  } finally {
    loading.value = false;
  }
}

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
  await loadOptions();
  const end = new Date().toISOString().slice(0, 10);
  const start = new Date(Date.now() - 6 * 86_400_000)
    .toISOString()
    .slice(0, 10);
  dates.value = [start, end];
  await load();
}

async function loadOptions() {
  if (!mediaId.value) return;
  const params = { current: 1, mediaId: mediaId.value, size: 200 };
  const [productResult, channelResult, documentResult, taskResult] =
    await Promise.all([
      getMaster('products', params),
      getMaster('media-channels', params),
      getMaster('integration-documents', params),
      getTrackingTasks(params),
    ]);
  products.value = productResult.records || [];
  channels.value = channelResult.records || [];
  documents.value = documentResult.records || [];
  tasks.value = taskResult.records || [];
}

function resetFilters() {
  productId.value = '';
  channelId.value = '';
  documentId.value = '';
  taskId.value = '';
}

watch(mediaId, async () => {
  resetFilters();
  await loadOptions();
  await load();
});
watch([dates, productId, channelId, documentId, taskId], load, { deep: true });
bootstrap();
</script>

<template>
  <main class="mvp-page">
    <SummaryToolbar
      v-model:media-id="mediaId"
      v-model:dates="dates"
      :media="media"
      @reset="resetFilters"
    />

    <section class="mb-4 grid grid-cols-2 gap-3 md:grid-cols-5">
      <div class="mvp-metric">
        <strong>{{ totals.impression }}</strong
        ><span>曝光</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.click }}</strong
        ><span>点击 · CTR {{ rate(totals.click, totals.impression) }}</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.activate }}</strong
        ><span>激活 · CVR {{ rate(totals.activate, totals.click) }}</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.register }}</strong
        ><span>注册</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.pay }}</strong
        ><span>付费 · 激活付费率 {{ rate(totals.pay, totals.activate) }}</span>
      </div>
    </section>

    <el-card v-loading="loading" class="mvp-card" shadow="never">
      <template #header>
        <div class="mvp-toolbar">
          <div>
            <div class="font-semibold">媒体交叉归因</div>
            <div class="mt-1 text-xs text-gray-500">
              切换视图不改变授权范围；无权任务不会参与汇总。
            </div>
          </div>
          <div class="flex gap-2">
            <el-button-group>
              <el-button
                :type="activeView === 'CHANNEL' ? 'primary' : 'default'"
                @click="activeView = 'CHANNEL'"
              >
                投放渠道
              </el-button>
              <el-button
                :type="activeView === 'DOCUMENT' ? 'primary' : 'default'"
                @click="activeView = 'DOCUMENT'"
              >
                媒体文档
              </el-button>
              <el-button
                :type="activeView === 'TASK' ? 'primary' : 'default'"
                @click="activeView = 'TASK'"
              >
                监测任务
              </el-button>
            </el-button-group>
            <el-button @click="load">刷新</el-button>
            <SummaryExportButton
              :media-id="mediaId"
              dimension="MEDIA"
              :end-date="dates[1]"
              :start-date="dates[0]"
            />
          </div>
        </div>
        <div
          class="mvp-filters border-t border-gray-100 pt-3 dark:border-white/10"
        >
          <el-select
            v-model="productId"
            class="!w-40"
            clearable
            filterable
            placeholder="产品"
          >
            <el-option
              v-for="item in products"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-model="channelId"
            class="!w-40"
            clearable
            filterable
            placeholder="投放渠道"
          >
            <el-option
              v-for="item in channels"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-model="documentId"
            class="!w-48"
            clearable
            filterable
            placeholder="媒体文档"
          >
            <el-option
              v-for="item in documents"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-model="taskId"
            class="!w-48"
            clearable
            filterable
            placeholder="监测任务"
          >
            <el-option
              v-for="item in tasks"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </div>
      </template>
      <el-table :data="rows" row-key="dimensionId">
        <el-table-column label="归集对象" min-width="240">
          <template #default="{ row }">
            <div class="font-medium">{{ row.dimensionName }}</div>
            <div class="mvp-code">{{ row.dimensionId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="曝光" prop="impressionCount" width="92" />
        <el-table-column label="点击" prop="uniqueClickCount" width="92" />
        <el-table-column
          label="有效点击"
          prop="eligibleClickCount"
          width="104"
        />
        <el-table-column
          label="激活"
          prop="attributedActivateCount"
          width="92"
        />
        <el-table-column
          label="注册"
          prop="attributedRegisterCount"
          width="92"
        />
        <el-table-column label="付费" prop="attributedPayCount" width="92" />
        <el-table-column label="下单" prop="attributedOrderCount" width="92" />
        <el-table-column label="次留" prop="nextDayReturnCount" width="92" />
        <el-table-column label="激活率" width="100">
          <template #default="{ row }">
            {{ rate(row.attributedActivateCount, row.eligibleClickCount) }}
          </template>
        </el-table-column>
        <el-table-column label="付费率" width="100">
          <template #default="{ row }">
            {{ rate(row.attributedPayCount, row.attributedActivateCount) }}
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">
            该范围内没有成功日报快照，请先生成效果日报。
          </div>
        </template>
      </el-table>
    </el-card>
  </main>
</template>
