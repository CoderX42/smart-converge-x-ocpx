<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElMessage } from 'element-plus';

import {
  getDailyReport,
  getMedia,
  getReportBreakdown,
  refreshDailyReport,
} from '#/api/ad';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdDailyReport' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const reportDate = ref(new Date().toISOString().slice(0, 10));
const rows = ref<any[]>([]);
const taskId = ref('');
const loading = ref(false);
const refreshing = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const breakdownRows = ref<any[]>([]);
const breakdownLoading = ref(false);
const breakdownDimension = ref('TASK');
const breakdownDates = ref<string[]>([]);

const totals = computed(() => {
  const sum = {
    activate: 0,
    click: 0,
    credit: 0,
    custom: 0,
    eligible: 0,
    firstActivate: 0,
    firstOrder: 0,
    impression: 0,
    nextDay: 0,
    order: 0,
    pay: 0,
    reactivate: 0,
    register: 0,
    retention3d: 0,
    retention7d: 0,
  };
  for (const row of rows.value) {
    sum.activate += Number(row.attributedActivateCount || 0);
    sum.click += Number(row.uniqueClickCount || 0);
    sum.impression += Number(row.impressionCount || 0);
    sum.eligible += Number(row.eligibleClickCount || 0);
    sum.pay += Number(row.attributedPayCount || 0);
    sum.register += Number(row.attributedRegisterCount || 0);
    sum.order += Number(row.attributedOrderCount || 0);
    sum.nextDay += Number(row.nextDayReturnCount || 0);
    sum.retention3d += Number(row.retention3dCount || 0);
    sum.retention7d += Number(row.retention7dCount || 0);
    sum.firstActivate += Number(row.firstActivateCount || 0);
    sum.firstOrder += Number(row.firstOrderCount || 0);
    sum.reactivate += Number(row.reactivateCount || 0);
    sum.custom += Number(row.customCount || 0);
    sum.credit += Number(row.creditCount || 0);
  }
  return sum;
});

const route = useRoute();

function rate(numerator: number, denominator: number) {
  return denominator > 0
    ? `${((numerator / denominator) * 100).toFixed(2)}%`
    : '—';
}

function queryString(raw?: unknown): string {
  if (!raw) return '';
  if (Array.isArray(raw)) {
    const [first] = raw;
    return queryString(first);
  }
  if (typeof raw === 'string') return raw.trim();
  return '';
}

function queryDate(raw?: unknown): string {
  const value = queryString(raw);
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : '';
}

function applyQuery() {
  const qTaskId = queryString(route.query.taskId);
  const qDate = queryDate(route.query.date);
  if (qTaskId) taskId.value = qTaskId;
  if (qDate) reportDate.value = qDate;
}

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function load() {
  if (!mediaId.value || !reportDate.value) return;
  loading.value = true;
  try {
    const result = await getDailyReport({
      current: current.value,
      date: reportDate.value,
      mediaId: mediaId.value,
      taskId: taskId.value || undefined,
      size: size.value,
    });
    rows.value = result.records || [];
    total.value = result.total || 0;
  } finally {
    loading.value = false;
  }
}

async function refresh() {
  if (!mediaId.value || !reportDate.value) return;
  refreshing.value = true;
  try {
    await refreshDailyReport(reportDate.value, mediaId.value);
    ElMessage.success('日报快照已重新计算');
    await load();
  } finally {
    refreshing.value = false;
  }
}

async function loadBreakdown() {
  const [startDate, endDate] = breakdownDates.value || [];
  if (!mediaId.value || !startDate || !endDate) return;
  breakdownLoading.value = true;
  try {
    breakdownRows.value = await getReportBreakdown({
      dimension: breakdownDimension.value,
      endDate,
      mediaId: mediaId.value,
      startDate,
    });
  } finally {
    breakdownLoading.value = false;
  }
}

watch([mediaId, reportDate], () => {
  current.value = 1;
  load();
});
watch([mediaId, breakdownDimension, breakdownDates], loadBreakdown, {
  deep: true,
});
bootstrap().then(async () => {
  applyQuery();
  const end = reportDate.value;
  const start = new Date(Date.now() - 6 * 86_400_000)
    .toISOString()
    .slice(0, 10);
  breakdownDates.value = [start, end];
  await Promise.all([load(), loadBreakdown()]);
});
</script>

<template>
  <main class="mvp-page">
    <div class="mvp-scope-toolbar">
      <div class="mvp-control grid grid-cols-2 gap-2">
        <div>
          <div class="mvp-scope-label mb-2 text-xs">媒体范围</div>
          <el-select v-model="mediaId" class="w-full">
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </div>
        <div>
          <div class="mvp-scope-label mb-2 text-xs">报告日期</div>
          <el-date-picker
            v-model="reportDate"
            class="!w-full"
            type="date"
            value-format="YYYY-MM-DD"
          />
        </div>
      </div>
    </div>

    <div class="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-6">
      <div class="mvp-metric">
        <strong>{{ totals.impression }}</strong>
        <span>曝光</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.click }}</strong>
        <span>原始点击</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.eligible }}</strong>
        <span>有效点击</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.activate }}</strong>
        <span>归因激活</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.register }}</strong>
        <span>归因注册</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.pay }}</strong>
        <span>归因付费</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.order }}</strong
        ><span>归因下单</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.nextDay }}</strong
        ><span>次留</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.retention3d }}</strong
        ><span>3 留</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.retention7d }}</strong
        ><span>7 留</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.firstActivate }}</strong
        ><span>首活</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.firstOrder }}</strong
        ><span>首单</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.reactivate }}</strong
        ><span>拉活</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.custom }}</strong
        ><span>自定义</span>
      </div>
      <div class="mvp-metric">
        <strong>{{ totals.credit }}</strong
        ><span>授信</span>
      </div>
    </div>

    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="text-sm text-gray-500">
          点击率 {{ rate(totals.click, totals.impression) }} · 有效点击率
          {{ rate(totals.eligible, totals.click) }} · 激活率
          {{ rate(totals.activate, totals.eligible) }}
        </div>
        <div>
          <el-button @click="load">查询</el-button>
          <el-button
            v-access:code="'ad:report:read'"
            :loading="refreshing"
            type="primary"
            @click="refresh"
          >
            刷新快照
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="任务 ID" min-width="180">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.taskId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="曝光" prop="impressionCount" width="90" />
        <el-table-column label="点击" prop="uniqueClickCount" width="90" />
        <el-table-column label="有效" prop="eligibleClickCount" width="90" />
        <el-table-column
          label="去重"
          prop="deduplicatedClickCount"
          width="90"
        />
        <el-table-column
          label="激活"
          prop="attributedActivateCount"
          width="90"
        />
        <el-table-column
          label="注册"
          prop="attributedRegisterCount"
          width="90"
        />
        <el-table-column label="付费" prop="attributedPayCount" width="90" />
        <el-table-column label="下单" prop="attributedOrderCount" width="90" />
        <el-table-column label="次留" prop="nextDayReturnCount" width="90" />
        <el-table-column label="3 留" prop="retention3dCount" width="90" />
        <el-table-column label="7 留" prop="retention7dCount" width="90" />
        <el-table-column label="首活" prop="firstActivateCount" width="90" />
        <el-table-column label="首单" prop="firstOrderCount" width="90" />
        <el-table-column label="拉活" prop="reactivateCount" width="90" />
        <el-table-column label="自定义" prop="customCount" width="90" />
        <el-table-column label="授信" prop="creditCount" width="90" />
        <el-table-column label="激活率" width="100">
          <template #default="{ row }">
            {{ rate(row.attributedActivateCount, row.eligibleClickCount) }}
          </template>
        </el-table-column>
        <el-table-column label="快照版本" width="110">
          <template #default="{ row }">R{{ row.revision }}</template>
        </el-table-column>
        <el-table-column label="计算时间" prop="calculatedAt" min-width="180" />
        <template #empty>
          <div class="mvp-empty">
            当天尚无成功日报快照，可点击“刷新快照”生成
          </div>
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

    <el-card class="mvp-card mt-4" shadow="never">
      <template #header>
        <div class="mvp-toolbar">
          <div>
            <div class="font-semibold">多维效果分析</div>
            <div class="mt-1 text-xs text-gray-500">
              按最新成功日报快照聚合，最多查询 31 天。
            </div>
          </div>
          <div class="flex gap-2">
            <el-date-picker
              v-model="breakdownDates"
              end-placeholder="结束日期"
              start-placeholder="开始日期"
              type="daterange"
              value-format="YYYY-MM-DD"
            />
            <el-select v-model="breakdownDimension" class="!w-28">
              <el-option label="按任务" value="TASK" />
              <el-option label="按媒体" value="MEDIA" />
              <el-option label="按链接" value="LINK" />
              <el-option label="按产品" value="PRODUCT" />
              <el-option label="按合作方" value="PARTNER" />
              <el-option label="按上游客户" value="CUSTOMER" />
              <el-option label="按接入文档" value="DOCUMENT" />
              <el-option label="按媒体渠道" value="CHANNEL" />
            </el-select>
            <el-button @click="loadBreakdown">查询</el-button>
          </div>
        </div>
      </template>
      <el-table
        v-loading="breakdownLoading"
        :data="breakdownRows"
        row-key="dimensionId"
      >
        <el-table-column label="分析对象" min-width="220">
          <template #default="{ row }">
            <div>{{ row.dimensionName }}</div>
            <div class="mvp-code text-xs">{{ row.dimensionId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="曝光" prop="impressionCount" width="90" />
        <el-table-column label="点击" prop="uniqueClickCount" width="100" />
        <el-table-column
          label="有效点击"
          prop="eligibleClickCount"
          width="110"
        />
        <el-table-column
          label="去重"
          prop="deduplicatedClickCount"
          width="90"
        />
        <el-table-column
          label="激活"
          prop="attributedActivateCount"
          width="90"
        />
        <el-table-column
          label="注册"
          prop="attributedRegisterCount"
          width="90"
        />
        <el-table-column label="付费" prop="attributedPayCount" width="90" />
        <el-table-column label="下单" prop="attributedOrderCount" width="90" />
        <el-table-column label="次留" prop="nextDayReturnCount" width="90" />
        <el-table-column label="3 留" prop="retention3dCount" width="90" />
        <el-table-column label="7 留" prop="retention7dCount" width="90" />
        <el-table-column label="首活" prop="firstActivateCount" width="90" />
        <el-table-column label="首单" prop="firstOrderCount" width="90" />
        <el-table-column label="拉活" prop="reactivateCount" width="90" />
        <el-table-column label="自定义" prop="customCount" width="90" />
        <el-table-column label="授信" prop="creditCount" width="90" />
        <el-table-column label="激活率" width="100">
          <template #default="{ row }">
            {{ rate(row.attributedActivateCount, row.eligibleClickCount) }}
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">该范围内没有已成功生成的日报快照</div>
        </template>
      </el-table>
    </el-card>
  </main>
</template>
