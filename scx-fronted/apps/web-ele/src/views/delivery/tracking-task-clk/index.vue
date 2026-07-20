<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { ElMessage } from 'element-plus';

import {
  getMaster,
  getMedia,
  getReportBreakdown,
  getTrackingLinks,
  getTrackingTasks,
  getTrackingTaskStatusHistory,
  getTrackingTaskRevisions,
} from '#/api/ad';

import '../components/mvp-page.css';

defineOptions({ name: 'AdTrackingTaskClk' });

const router = useRouter();
const media = ref<Media[]>([]);
const mediaId = ref('');
const rows = ref<any[]>([]);
const links = ref<any[]>([]);
const channels = ref<any[]>([]);
const loading = ref(false);
const historyDrawer = ref(false);
const current = ref(1);
const size = ref(10);
const total = ref(0);
const keyword = ref('');
const linkFilter = ref('');
const channelFilter = ref('');
const historyTask = ref<any>();
const revisions = ref<any[]>([]);
const statusHistory = ref<any[]>([]);
const todayMetrics = ref<Record<string, any>>({});

function todayDate() {
  return new Date().toISOString().slice(0, 10);
}

function optionName(list: any[], id: string) {
  return list.find((item) => item.id === id)?.name || id;
}

function ratePerTask(row: any, field: string) {
  return Number(todayMetrics.value[row.id]?.[field] || 0);
}

function trackingAddress(row: any) {
  const code = links.value.find((item) => item.id === row.linkId)?.code;
  return code ? `${window.location.origin}/api/boot/ad/r/${code}` : '—';
}

async function copyTrackingAddress(row: any) {
  const address = trackingAddress(row);
  if (address === '—') {
    ElMessage.warning('追踪链接尚未加载，请刷新后重试');
    return;
  }
  try {
    await navigator.clipboard.writeText(address);
    ElMessage.success('监测地址已复制');
  } catch {
    ElMessage.info(address);
  }
}

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function loadOptions() {
  if (!mediaId.value) return;
  const params = { current: 1, mediaId: mediaId.value, size: 200, status: 'ACTIVE' };
  const [linkResult, channelResult] = await Promise.all([
    getTrackingLinks(params),
    getMaster('media-channels', params),
  ]);
  links.value = linkResult.records || [];
  channels.value = channelResult.records || [];
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const today = todayDate();
    const [result, metrics] = await Promise.all([
      getTrackingTasks({
        current: current.value,
        keyword: keyword.value,
        mediaId: mediaId.value,
        size: size.value,
        status: '',
        linkId: linkFilter.value,
        channelId: channelFilter.value,
      }),
      getReportBreakdown({
        dimension: 'TASK',
        endDate: today,
        mediaId: mediaId.value,
        startDate: today,
      }),
    ]);
    rows.value = result.records || [];
    total.value = result.total || 0;
    todayMetrics.value = Object.fromEntries(
      (metrics || []).map((item: any) => [item.dimensionId, item]),
    );
  } finally {
    loading.value = false;
  }
}

async function openHistory(row: any) {
  historyTask.value = row;
  const [revisionResult, statusResult] = await Promise.all([
    getTrackingTaskRevisions(row.id),
    getTrackingTaskStatusHistory(row.id),
  ]);
  revisions.value = revisionResult || [];
  statusHistory.value = statusResult || [];
  historyDrawer.value = true;
}

function openDaily(row: any) {
  router.push({
    path: '/reporting/daily',
    query: { date: todayDate(), taskId: row.id },
  });
}

function openHourly(row: any) {
  router.push({
    path: '/reporting/task-summary',
    query: {
      endDate: todayDate(),
      startDate: todayDate(),
      taskId: row.id,
    },
  });
}

function openConversionDetail(row: any) {
  router.push({
    path: '/reporting/event-detail',
    query: { date: todayDate(), eventType: 'PAY', taskId: row.id },
  });
}

function openClickDetail(row: any) {
  router.push({
    path: '/reporting/event-detail',
    query: { date: todayDate(), eventType: 'CLICK', taskId: row.id },
  });
}

watch(mediaId, async () => {
  current.value = 1;
  await Promise.all([load(), loadOptions()]);
});

watch(
  [keyword, linkFilter, channelFilter, current, size],
  () => {
    load();
  },
  { deep: true },
);

bootstrap().then(() => Promise.all([load(), loadOptions()]));
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
          <el-input
            v-model="keyword"
            clearable
            placeholder="任务名称或编码"
            style="width: 240px"
            @keyup.enter="load"
          />
          <el-select
            v-model="linkFilter"
            clearable
            filterable
            placeholder="全部追踪链接"
            style="width: 180px"
          >
            <el-option
              v-for="item in links"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-model="channelFilter"
            clearable
            filterable
            placeholder="全部媒体渠道"
            style="width: 170px"
          >
            <el-option
              v-for="item in channels"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="任务" min-width="240">
          <template #default="{ row }">
            <strong>{{ row.name }}</strong>
            <div class="mvp-code mt-1">{{ row.code }}</div>
          </template>
        </el-table-column>
        <el-table-column label="追踪链接" min-width="180">
          <template #default="{ row }">
            {{ optionName(links, row.linkId) }}
          </template>
        </el-table-column>
        <el-table-column label="媒体渠道" min-width="150">
          <template #default="{ row }">
            {{ optionName(channels, row.channelId) }}
          </template>
        </el-table-column>
        <el-table-column label="今日概况" min-width="235">
          <template #default="{ row }">
            <div class="flex gap-3 text-xs">
              <span>曝光 <strong>{{ ratePerTask(row, 'impressionCount') }}</strong></span>
              <span>有效点击 <strong>{{ ratePerTask(row, 'eligibleClickCount') }}</strong></span>
              <span>付费 <strong>{{ ratePerTask(row, 'attributedPayCount') }}</strong></span>
            </div>
            <div
              class="mt-1 truncate text-xs text-gray-400"
              :title="trackingAddress(row)"
            >
              {{ trackingAddress(row) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" fixed="right" label="操作" width="360">
          <template #default="{ row }">
            <el-button link type="primary" @click="openHourly(row)">时报</el-button>
            <el-button link type="primary" @click="openDaily(row)">日报</el-button>
            <el-button link type="primary" @click="openClickDetail(row)">点击明细</el-button>
            <el-button link type="primary" @click="openConversionDetail(row)">转化明细</el-button>
            <el-button link type="primary" @click="copyTrackingAddress(row)">监测地址</el-button>
            <el-button link @click="openHistory(row)">版本记录</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">尚未创建监测任务</div>
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

    <el-drawer
      v-model="historyDrawer"
      :title="`任务版本记录 · ${historyTask?.name || ''}`"
      size="620px"
    >
      <h3 class="mb-3 text-base font-semibold">配置快照</h3>
      <el-table :data="revisions" size="small">
        <el-table-column label="版本" prop="configVersion" width="90">
          <template #default="{ row }">v{{ row.configVersion }}</template>
        </el-table-column>
        <el-table-column label="生成时间" min-width="180" prop="createTime" />
        <el-table-column label="快照" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mvp-code">{{ row.snapshotJson }}</span>
          </template>
        </el-table-column>
      </el-table>
      <h3 class="mb-3 mt-6 text-base font-semibold">状态流转</h3>
      <el-table :data="statusHistory" size="small">
        <el-table-column label="变更" min-width="160">
          <template #default="{ row }">
            {{ row.previousStatus }} → {{ row.nextStatus }}
          </template>
        </el-table-column>
        <el-table-column
          label="原因"
          min-width="180"
          prop="reason"
          show-overflow-tooltip
        />
        <el-table-column label="时间" min-width="180" prop="createTime" />
      </el-table>
    </el-drawer>
  </main>
</template>
