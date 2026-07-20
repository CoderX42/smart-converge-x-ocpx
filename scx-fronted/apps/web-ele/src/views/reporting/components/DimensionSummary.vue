<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { computed, ref, watch } from 'vue';

import {
  getMaster,
  getMedia,
  getReportBreakdown,
  getTrackingLinks,
  getTrackingTasks,
  getUpstreamCustomers,
} from '#/api/ad';

import '../../delivery/components/mvp-page.css';

const props = defineProps<{
  dimension: 'DATE' | 'LINK' | 'PRODUCT' | 'TASK';
}>();

const media = ref<Media[]>([]);
const mediaId = ref('');
const dates = ref<string[]>([]);
const customers = ref<any[]>([]);
const products = ref<any[]>([]);
const links = ref<any[]>([]);
const tasks = ref<any[]>([]);
const customerId = ref('');
const productId = ref('');
const linkId = ref('');
const taskId = ref('');
const loading = ref(false);
const rows = ref<any[]>([]);

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
    rows.value = await getReportBreakdown({
      dimension: props.dimension,
      endDate,
      customerId: customerId.value || undefined,
      linkId: linkId.value || undefined,
      mediaId: mediaId.value,
      productId: productId.value || undefined,
      startDate,
      taskId: taskId.value || undefined,
    });
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
  const [customerResult, productResult, linkResult, taskResult] =
    await Promise.all([
      getUpstreamCustomers(params),
      getMaster('products', params),
      getTrackingLinks(params),
      getTrackingTasks(params),
    ]);
  customers.value = customerResult.records || [];
  products.value = productResult.records || [];
  links.value = linkResult.records || [];
  tasks.value = taskResult.records || [];
}

function resetFilters() {
  customerId.value = '';
  productId.value = '';
  linkId.value = '';
  taskId.value = '';
}

watch(mediaId, async () => {
  resetFilters();
  await loadOptions();
  await load();
});
watch([dates, customerId, productId, linkId, taskId], load, { deep: true });
bootstrap();
</script>

<template>
  <main class="mvp-page">
    <div class="mvp-scope-toolbar">
      <div class="mvp-control space-y-2">
        <div class="mvp-scope-label text-xs">媒体安全范围</div>
        <el-select v-model="mediaId" class="w-full">
          <el-option
            v-for="item in media"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-date-picker
          v-model="dates"
          class="!w-full"
          end-placeholder="结束日期"
          start-placeholder="开始日期"
          type="daterange"
          value-format="YYYY-MM-DD"
        />
      </div>
    </div>

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
            <div class="font-semibold">范围内效果明细</div>
            <div class="mt-1 text-xs text-gray-500">
              可按客户、产品、链接或任务交叉筛选；仅统计授权范围内最新成功日报快照。
            </div>
          </div>
          <div class="flex gap-2">
            <el-button plain @click="resetFilters">清空筛选</el-button>
            <el-button @click="load">刷新</el-button>
          </div>
        </div>
        <div
          class="mvp-filters border-t border-gray-100 pt-3 dark:border-white/10"
        >
          <el-select
            v-model="customerId"
            class="!w-40"
            clearable
            filterable
            placeholder="上游客户"
          >
            <el-option
              v-for="item in customers"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
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
            v-model="linkId"
            class="!w-48"
            clearable
            filterable
            placeholder="追踪链接"
          >
            <el-option
              v-for="item in links"
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
            <div class="mvp-code text-xs">{{ row.dimensionId }}</div>
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
          label="重复点击"
          prop="deduplicatedClickCount"
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
        <el-table-column label="次留 / 3留 / 7留" min-width="145">
          <template #default="{ row }">
            {{ row.nextDayReturnCount }} / {{ row.retention3dCount }} /
            {{ row.retention7dCount }}
          </template>
        </el-table-column>
        <el-table-column label="首活 / 首单" min-width="120">
          <template #default="{ row }">
            {{ row.firstActivateCount }} / {{ row.firstOrderCount }}
          </template>
        </el-table-column>
        <el-table-column label="拉活 / 自定义 / 授信" min-width="155">
          <template #default="{ row }">
            {{ row.reactivateCount }} / {{ row.customCount }} /
            {{ row.creditCount }}
          </template>
        </el-table-column>
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
            该范围内没有成功日报快照，请先在效果日报生成快照。
          </div>
        </template>
      </el-table>
    </el-card>
  </main>
</template>
