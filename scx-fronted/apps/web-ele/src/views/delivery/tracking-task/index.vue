<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  changeTrackingTaskStatus,
  getMaster,
  getMedia,
  getReportBreakdown,
  getRtaEndpoints,
  getTrackingLinks,
  getTrackingTaskRevisions,
  getTrackingTasks,
  getTrackingTaskStatusHistory,
  saveTrackingTask,
} from '#/api/ad';

import '../components/mvp-page.css';

defineOptions({ name: 'AdTrackingTask' });

const router = useRouter();
const media = ref<Media[]>([]);
const mediaId = ref('');
const rows = ref<any[]>([]);
const links = ref<any[]>([]);
const channels = ref<any[]>([]);
const rtaEndpoints = ref<any[]>([]);
const loading = ref(false);
const saving = ref(false);
const drawer = ref(false);
const historyDrawer = ref(false);
const current = ref(1);
const size = ref(10);
const total = ref(0);
const keyword = ref('');
const status = ref('');
const linkFilter = ref('');
const channelFilter = ref('');
const form = reactive<any>({});
const revisions = ref<any[]>([]);
const statusHistory = ref<any[]>([]);
const historyTask = ref<any>();
const todayMetrics = ref<Record<string, any>>({});

function todayDate() {
  return new Date().toISOString().slice(0, 10);
}

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function loadOptions() {
  if (!mediaId.value) return;
  const params = {
    current: 1,
    mediaId: mediaId.value,
    size: 200,
    status: 'ACTIVE',
  };
  const [linkResult, channelResult, rtaResult] = await Promise.all([
    getTrackingLinks(params),
    getMaster('media-channels', params),
    getRtaEndpoints(params),
  ]);
  links.value = linkResult.records || [];
  channels.value = channelResult.records || [];
  rtaEndpoints.value = rtaResult.records || [];
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const today = new Date().toISOString().slice(0, 10);
    const [result, metrics] = await Promise.all([
      getTrackingTasks({
        current: current.value,
        keyword: keyword.value,
        mediaId: mediaId.value,
        size: size.value,
        status: status.value,
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

function open(row?: any) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(
    form,
    row || {
      attributionWindowHours: 168,
      callbackTargetRef: 'sandbox',
      code: `TASK-${Date.now()}`,
      dedupWindowSec: 300,
      dailyConversionLimit: undefined,
      impressionEnabled: false,
      rtaEnabled: false,
      bigClickEnabled: false,
      mediaId: mediaId.value,
      status: 'DRAFT',
    },
  );
  drawer.value = true;
}

async function submit() {
  if (!form.name || !form.code || !form.linkId || !form.channelId) {
    ElMessage.warning('请完整填写名称、编码、追踪链接和媒体渠道');
    return;
  }
  saving.value = true;
  try {
    await saveTrackingTask({ ...form, mediaId: mediaId.value });
    ElMessage.success('监测任务已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function changeStatus(row: any, next: string) {
  const { value } = await ElMessageBox.prompt(
    `请输入将任务变更为 ${next} 的原因`,
    '状态变更',
    {
      inputValidator: (text) => Boolean(text?.trim()) || '必须填写原因',
    },
  );
  await changeTrackingTaskStatus(row.id, {
    reason: value,
    status: next,
    version: row.version,
  });
  ElMessage.success('任务状态已更新');
  await Promise.all([load(), loadOptions()]);
}

function optionName(list: any[], id: string) {
  return list.find((item) => item.id === id)?.name || id;
}

function todayMetric(row: any, field: string) {
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

function openHourlyReport(row: any) {
  router.push({
    path: '/reporting/task-summary',
    query: {
      endDate: todayDate(),
      startDate: todayDate(),
      taskId: row.id,
    },
  });
}

function openDailyReport(row: any) {
  router.push({
    path: '/reporting/daily',
    query: {
      date: todayDate(),
      taskId: row.id,
    },
  });
}

function openLinkDailyReport(row: any) {
  router.push({
    path: '/reporting/link-summary',
    query: {
      endDate: todayDate(),
      linkId: row.linkId || '',
      startDate: todayDate(),
      taskId: row.id,
    },
  });
}

function openIntegrationTest(row: any) {
  router.push({
    path: '/operations/jobs',
    query: {
      taskId: row.id,
    },
  });
}

function openClickDetail(row: any) {
  router.push({
    path: '/reporting/event-detail',
    query: {
      date: todayDate(),
      eventType: 'CLICK',
      taskId: row.id,
    },
  });
}

function openConversionDetail(row: any) {
  router.push({
    path: '/reporting/event-detail',
    query: {
      date: todayDate(),
      eventType: 'PAY',
      taskId: row.id,
    },
  });
}

watch(mediaId, async () => {
  current.value = 1;
  await Promise.all([load(), loadOptions()]);
});
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
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 150px"
          >
            <el-option
              v-for="item in ['DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED']"
              :key="item"
              :value="item"
            />
          </el-select>
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
        <el-button
          v-access:code="'ad:task:write'"
          type="primary"
          @click="open()"
        >
          创建监测任务
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="任务" min-width="220">
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
              <span
                >曝光
                <strong>{{ todayMetric(row, 'impressionCount') }}</strong></span
              >
              <span
                >有效点击
                <strong>{{
                  todayMetric(row, 'eligibleClickCount')
                }}</strong></span
              >
              <span
                >付费
                <strong>{{
                  todayMetric(row, 'attributedPayCount')
                }}</strong></span
              >
            </div>
            <div
              class="mt-1 truncate text-xs text-gray-400"
              :title="trackingAddress(row)"
            >
              {{ trackingAddress(row) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="去重窗口" width="110">
          <template #default="{ row }">{{ row.dedupWindowSec }} 秒</template>
        </el-table-column>
        <el-table-column label="归因窗口" width="110">
          <template #default="{ row }">
            {{ row.attributionWindowHours }} 小时
          </template>
        </el-table-column>
        <el-table-column label="配置版本" width="105">
          <template #default="{ row }">v{{ row.configVersion }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag
              effect="plain"
              :type="
                row.status === 'ACTIVE'
                  ? 'success'
                  : row.status === 'ARCHIVED'
                    ? 'info'
                    : 'warning'
              "
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" fixed="right" label="操作" width="430">
          <template #default="{ row }">
            <el-button
              v-access:code="'ad:report:read'"
              link
              type="primary"
              @click="openHourlyReport(row)"
            >
              时报
            </el-button>
            <el-button
              v-access:code="'ad:report:read'"
              link
              type="primary"
              @click="openDailyReport(row)"
            >
              日报
            </el-button>
            <el-button
              v-access:code="'ad:report:read'"
              link
              type="primary"
              @click="openLinkDailyReport(row)"
            >
              链接日报
            </el-button>
            <el-button
              v-access:code="'ad:event-detail:read'"
              link
              type="primary"
              @click="openIntegrationTest(row)"
            >
              联调
            </el-button>
            <el-button
              v-access:code="'ad:event-detail:read'"
              link
              type="primary"
              @click="openClickDetail(row)"
            >
              点击明细
            </el-button>
            <el-button
              v-access:code="'ad:event-detail:read'"
              link
              type="primary"
              @click="openConversionDetail(row)"
            >
              转化明细
            </el-button>
            <el-button link type="primary" @click="copyTrackingAddress(row)">
              监测地址
            </el-button>
            <el-button link @click="openHistory(row)">版本记录</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-access:code="'ad:task:write'"
              link
              type="primary"
              @click="open(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'PAUSED'"
              v-access:code="'ad:task:status'"
              link
              type="success"
              @click="changeStatus(row, 'ACTIVE')"
            >
              启用
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-access:code="'ad:task:status'"
              link
              type="warning"
              @click="changeStatus(row, 'PAUSED')"
            >
              暂停
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE' || row.status === 'PAUSED'"
              v-access:code="'ad:task:status'"
              link
              type="danger"
              @click="changeStatus(row, 'ARCHIVED')"
            >
              归档
            </el-button>
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

    <el-drawer
      v-model="drawer"
      :title="form.id ? '编辑监测任务' : '创建监测任务'"
      size="540px"
    >
      <el-form :model="form" label-position="top">
        <div class="grid grid-cols-2 gap-x-4">
          <el-form-item label="名称" required>
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item label="唯一编码" required>
            <el-input v-model="form.code" :disabled="Boolean(form.id)" />
          </el-form-item>
        </div>
        <el-form-item label="追踪链接" required>
          <el-select v-model="form.linkId" class="w-full" filterable>
            <el-option
              v-for="item in links"
              :key="item.id"
              :label="`${item.name} · ${item.code}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="媒体渠道" required>
          <el-select v-model="form.channelId" class="w-full" filterable>
            <el-option
              v-for="item in channels"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <div class="grid grid-cols-2 gap-x-4">
          <el-form-item label="点击去重窗口（秒）">
            <el-input-number
              v-model="form.dedupWindowSec"
              class="w-full"
              :min="0"
              :step="60"
            />
          </el-form-item>
          <el-form-item label="末次点击归因窗口（小时）">
            <el-input-number
              v-model="form.attributionWindowHours"
              class="w-full"
              :min="1"
              :step="24"
            />
          </el-form-item>
          <el-form-item label="日转化限量">
            <el-input-number
              v-model="form.dailyConversionLimit"
              class="w-full"
              controls-position="right"
              placeholder="不限制"
              :min="1"
            />
          </el-form-item>
        </div>
        <el-form-item label="回传目标">
          <el-input
            v-model="form.callbackTargetRef"
            maxlength="64"
            placeholder="sandbox 或 partner-a"
          />
          <div class="mt-1 text-xs text-gray-500">
            在“广告平台配置”中设置
            <code>callback.endpoint.&lt;目标标识&gt;</code>；默认
            <code>sandbox</code> 指向本地回传模拟器。
          </div>
        </el-form-item>
        <el-form-item label="后置 RTA">
          <el-switch v-model="form.rtaEnabled" />
        </el-form-item>
        <el-form-item v-if="form.rtaEnabled" label="RTA 配置" required>
          <el-select v-model="form.rtaEndpointId" class="w-full" filterable>
            <el-option
              v-for="item in rtaEndpoints"
              :key="item.id"
              :label="`${item.name} · ${item.code}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="曝光事件接入">
          <el-switch v-model="form.impressionEnabled" />
        </el-form-item>
        <template v-if="form.impressionEnabled">
          <el-form-item label="曝光上报地址" required>
            <el-input
              v-model="form.exposureReportUrl"
              placeholder="http://localhost:8080/sandbox/exposure"
            />
          </el-form-item>
          <el-form-item label="开启大点击">
            <el-switch v-model="form.bigClickEnabled" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="submit">
          保存
        </el-button>
      </template>
    </el-drawer>
  </main>
</template>
