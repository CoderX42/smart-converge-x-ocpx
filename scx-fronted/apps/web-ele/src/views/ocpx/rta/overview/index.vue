<script setup lang="ts">
import type { EntityOption } from '#/api/ocpx';
import type {
  RtaCheck,
  RtaOverview,
  RtaRoute,
  RtaRouteStatus,
  RtaSegment,
} from '#/api/ocpx/rta';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { rtaOptions, rtaOverview } from '#/api/ocpx/rta';

import RtaConfigurationDrawer from '../configuration-drawer.vue';
import {
  formatRate,
  protocols,
  routeStatus,
  segmentState,
  tagOf,
} from '../labels';

defineOptions({ name: 'RtaOverview' });

type Segment = 'binding' | 'media' | 'upstream';

const route = useRoute();
const router = useRouter();
const { hasAccessByCodes } = useAccess();
const loading = ref(false);
const error = ref('');
const result = ref<RtaOverview>();
const media = ref<EntityOption[]>([]);
const query = reactive({
  current: 1,
  keyword: '',
  mediaId: '',
  onlyIssues: false,
  size: 30,
  status: '' as '' | RtaRouteStatus,
});
const summaryCards = computed(() => {
  const summary = result.value?.summary;
  return [
    { count: summary?.ready ?? 0, status: 'READY' as const },
    { count: summary?.incomplete ?? 0, status: 'INCOMPLETE' as const },
    { count: summary?.disabled ?? 0, status: 'DISABLED' as const },
    { count: summary?.notConfigured ?? 0, status: 'NOT_CONFIGURED' as const },
  ];
});
const segmentColumns: Array<{ key: Segment; label: string }> = [
  { key: 'media', label: '媒体接入' },
  { key: 'upstream', label: '广告主接入' },
  { key: 'binding', label: '任务绑定' },
];
const permissions: Record<Segment, string> = {
  binding: 'ocpx:rta:bindings:view',
  media: 'ocpx:rta:media:view',
  upstream: 'ocpx:rta:upstreams:view',
};

let sequence = 0;
async function load(reset = false) {
  if (reset) query.current = 1;
  const token = ++sequence;
  loading.value = true;
  error.value = '';
  try {
    const data = await rtaOverview({
      current: query.current,
      keyword: query.keyword.trim(),
      mediaId: query.mediaId,
      onlyIssues: String(query.onlyIssues),
      size: query.size,
      status: query.status,
    });
    if (token === sequence) result.value = data;
  } catch (error_) {
    if (token === sequence) {
      result.value = undefined;
      error.value =
        error_ instanceof Error ? error_.message : '查询失败，请重试';
    }
  } finally {
    if (token === sequence) loading.value = false;
  }
}

function filterStatus(status: RtaRouteStatus) {
  query.status = query.status === status ? '' : status;
  void load(true);
}

function segmentOf(row: RtaRoute, key: Segment): RtaSegment {
  return row[key];
}

function issues(row: RtaRoute): RtaCheck[] {
  return row.checks.filter((check) => check.level !== 'OK');
}

function fix(row: RtaRoute, key: Segment) {
  const next: Record<string, string> = {};
  if (key === 'media') next.rtaMediaId = row.mediaId;
  if (key === 'upstream') {
    next.rtaAdvertiserId = row.advertiserId;
    if (row.upstreamId) next.rtaSelect = row.upstreamId;
  }
  if (key === 'binding') next.rtaTaskId = row.monitorTaskId;
  void router.replace({ query: { ...route.query, ...next } });
}

function openRecords(row: RtaRoute) {
  void router.push({
    path: '/ocpx/rta/records',
    query: { monitorTaskId: row.monitorTaskId },
  });
}

async function searchMedia(keyword: string) {
  try {
    media.value = await rtaOptions('media', { keyword });
  } catch {
    media.value = [];
  }
}

onMounted(() => {
  void load(true);
  void searchMedia('');
});
</script>

<template>
  <Page
    title="接入总览"
    description="每行一个监测任务，展示媒体接入、广告主接入、任务绑定三段配置是否就绪；点击“修复”可在当前页直接打开对应配置。"
  >
    <div class="rta-overview">
      <el-alert
        v-for="notice in result?.notices || []"
        :key="notice.code"
        :title="notice.label"
        :description="notice.hint"
        :type="notice.level === 'BLOCK' ? 'error' : 'warning'"
        :closable="false"
        show-icon
      />
      <div class="rta-overview__summary">
        <button
          v-for="card in summaryCards"
          :key="card.status"
          type="button"
          class="rta-overview__card"
          :class="[
            `is-${tagOf(routeStatus, card.status).type}`,
            { 'is-active': query.status === card.status },
          ]"
          @click="filterStatus(card.status)"
        >
          <span>{{ tagOf(routeStatus, card.status).text }}</span>
          <strong>{{ card.count }}</strong>
        </button>
      </div>
      <el-form inline @submit.prevent="load(true)">
        <el-form-item label="媒体">
          <el-select
            v-model="query.mediaId"
            remote
            :remote-method="searchMedia"
            clearable
            filterable
            placeholder="全部媒体"
          >
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="任务名称或 ID"
          />
        </el-form-item>
        <el-form-item label="只看问题项">
          <el-switch v-model="query.onlyIssues" @change="load(true)" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit">查询</el-button>
          <el-button :loading="loading" @click="load()">刷新</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
      />
      <el-table
        v-loading="loading"
        :data="result?.records || []"
        border
        empty-text="没有已开通 RTA 的任务"
      >
        <el-table-column label="监测任务" min-width="180">
          <template #default="{ row }">
            <div class="rta-overview__name">
              <strong>{{ row.monitorName }}</strong>
              <span>{{ row.monitorTaskId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="广告主"
          prop="advertiserName"
          min-width="120"
          show-overflow-tooltip
        />
        <el-table-column label="媒体" min-width="150">
          <template #default="{ row }">
            {{ row.mediaName }}
            <el-tag
              v-if="row.mediaProtocol"
              size="small"
              :type="tagOf(protocols, row.mediaProtocol).type"
              >{{ tagOf(protocols, row.mediaProtocol).text }}</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column label="上游接入" min-width="150">
          <template #default="{ row }">
            {{ row.upstreamName || '—' }}
            <el-tag
              v-if="row.upstreamProtocol"
              size="small"
              :type="tagOf(protocols, row.upstreamProtocol).type"
              >{{ tagOf(protocols, row.upstreamProtocol).text }}</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column
          v-for="column in segmentColumns"
          :key="column.key"
          :label="column.label"
          width="130"
        >
          <template #default="{ row }">
            <div class="rta-overview__segment">
              <el-tag
                size="small"
                :type="
                  tagOf(segmentState, segmentOf(row, column.key).state).type
                "
                >{{
                  tagOf(segmentState, segmentOf(row, column.key).state).text
                }}</el-tag
              >
              <el-button
                v-if="hasAccessByCodes([permissions[column.key]])"
                link
                type="primary"
                size="small"
                @click="fix(row, column.key)"
                >{{
                  segmentOf(row, column.key).state === 'ENABLED'
                    ? '查看'
                    : '修复'
                }}</el-button
              >
            </div>
          </template>
        </el-table-column>
        <el-table-column label="整体状态" width="110">
          <template #default="{ row }">
            <el-tooltip
              :disabled="issues(row).length === 0"
              placement="top"
              :content="
                issues(row)
                  .map((check) => `${check.label}：${check.hint || ''}`)
                  .join('\n')
              "
              popper-class="rta-overview__tip"
            >
              <el-tag :type="tagOf(routeStatus, row.status).type">
                {{ tagOf(routeStatus, row.status).text
                }}{{ issues(row).length > 0 ? ` · ${issues(row).length}` : '' }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="24 小时受理" width="110" align="right">
          <template #default="{ row }">{{ row.requestCount24h }}</template>
        </el-table-column>
        <el-table-column label="命中率" width="90" align="right">
          <template #default="{ row }">{{
            formatRate(row.allowCount24h, row.requestCount24h)
          }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="100">
          <template #default="{ row }">
            <el-button
              v-if="hasAccessByCodes(['ocpx:rta:records:view'])"
              link
              type="primary"
              @click="openRecords(row)"
              >查看记录</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="result?.total || 0"
        :page-sizes="[10, 30, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="load()"
        @size-change="load(true)"
      />
    </div>
    <RtaConfigurationDrawer @saved="load()" />
  </Page>
</template>

<style scoped>
.rta-overview {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  background: var(--el-bg-color);
  border-radius: 8px;
}

.rta-overview__summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 12px;
}

.rta-overview__card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
  padding: 14px 16px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-left: 4px solid var(--el-color-info);
  border-radius: 8px;
}

.rta-overview__card strong {
  font-size: 24px;
  color: var(--el-text-color-primary);
}

.rta-overview__card.is-success {
  border-left-color: var(--el-color-success);
}

.rta-overview__card.is-danger {
  border-left-color: var(--el-color-danger);
}

.rta-overview__card.is-active {
  border-color: var(--el-color-primary);
}

.rta-overview__name {
  display: flex;
  flex-direction: column;
}

.rta-overview__name span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rta-overview__segment {
  display: flex;
  gap: 4px;
  align-items: center;
}

.el-pagination {
  justify-content: flex-end;
}

@media (width <= 768px) {
  .rta-overview__summary {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

<style>
.rta-overview__tip {
  max-width: 360px;
  white-space: pre-line;
}
</style>
