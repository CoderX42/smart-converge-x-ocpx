<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import {
  createExportTask,
  getExportTasks,
  getMedia,
  retryExportTask,
} from '#/api/ad';
import { downloadBlobFile } from '#/utils/util';

import '../../delivery/components/mvp-page.css';

defineOptions({ name: 'AdExportTasks' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const status = ref('');
const startDate = ref('');
const endDate = ref('');
const exportType = ref<'DAILY_CSV' | 'REDUCTION_CSV'>('DAILY_CSV');
const rows = ref<any[]>([]);
const loading = ref(false);
const creating = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const result = await getExportTasks({
      current: current.value,
      mediaId: mediaId.value,
      size: size.value,
      status: status.value,
    });
    rows.value = result.records || [];
    total.value = result.total || 0;
  } finally {
    loading.value = false;
  }
}

async function create() {
  if (!startDate.value || !endDate.value) {
    ElMessage.warning('请选择导出开始与结束日期');
    return;
  }
  creating.value = true;
  try {
    const task = await createExportTask({
      endDate: endDate.value,
      exportType: exportType.value,
      mediaId: mediaId.value,
      startDate: startDate.value,
    });
    ElMessage.success(`导出任务已创建：${task.id}`);
    await load();
  } finally {
    creating.value = false;
  }
}

function download(row: any) {
  downloadBlobFile(
    `/ad/export-tasks/${row.id}/download`,
    {},
    row.artifactName || `scx-export-${row.id}.csv`,
  );
}

async function retry(row: any) {
  await retryExportTask(row.id);
  ElMessage.success('导出任务已重新进入队列');
  await load();
}

function tagType(value: string) {
  if (value === 'SUCCEEDED') return 'success';
  if (value === 'FAILED') return 'danger';
  if (value === 'EXPIRED') return 'info';
  return 'warning';
}

function exportTypeLabel(value: string) {
  return value === 'REDUCTION_CSV' ? '审减与超量明细' : '效果日报';
}

watch([mediaId, status], () => {
  current.value = 1;
  void load();
});
bootstrap().then(load);
</script>

<template>
  <main class="mvp-page">
    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-select
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 150px"
          >
            <el-option
              v-for="item in [
                'PENDING',
                'RUNNING',
                'SUCCEEDED',
                'FAILED',
                'EXPIRED',
              ]"
              :key="item"
              :value="item"
            />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
        <div class="flex gap-2">
          <el-select v-model="exportType" style="width: 170px">
            <el-option label="效果日报 CSV" value="DAILY_CSV" />
            <el-option label="审减与超量 CSV" value="REDUCTION_CSV" />
          </el-select>
          <el-date-picker
            v-model="startDate"
            placeholder="开始日期"
            type="date"
            value-format="YYYY-MM-DD"
          />
          <el-date-picker
            v-model="endDate"
            placeholder="结束日期"
            type="date"
            value-format="YYYY-MM-DD"
          />
          <el-button
            v-access:code="'ad:export:create'"
            :loading="creating"
            type="primary"
            @click="create"
          >
            创建导出任务
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="任务 ID" min-width="210">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="日期范围" min-width="190">
          <template #default="{ row }">
            {{ row.reportStartDate }} ～ {{ row.reportEndDate }}
          </template>
        </el-table-column>
        <el-table-column label="导出内容" min-width="150">
          <template #default="{ row }">
            {{ exportTypeLabel(row.exportType) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag effect="plain" :type="tagType(row.status)">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="行数" prop="rowCount" width="90" />
        <el-table-column label="过期时间" prop="expiresAt" min-width="180" />
        <el-table-column label="失败原因" min-width="220">
          <template #default="{ row }">
            <span v-if="row.errorCode" class="mvp-code">
              {{ row.errorCode }} · {{ row.errorMessage }}
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column align="right" fixed="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'SUCCEEDED'"
              v-access:code="'ad:export:read'"
              link
              type="primary"
              @click="download(row)"
            >
              下载
            </el-button>
            <el-button
              v-else-if="row.status === 'FAILED'"
              v-access:code="'ad:export:create'"
              link
              type="warning"
              @click="retry(row)"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">暂无导出任务</div>
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
