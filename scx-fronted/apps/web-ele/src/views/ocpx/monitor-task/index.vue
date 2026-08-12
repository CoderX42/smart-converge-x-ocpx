<script lang="ts" setup>
import type { AdvertiserV2, Media, MonitorTask, PromotionLink } from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  archiveMonitorTask,
  changeMonitorTaskStatus,
  createMonitorTask,
  listAdvertisers,
  listMedia,
  listMonitorTasks,
  listPromotionLinks,
  updateMonitorTask,
} from '#/api/ocpx';

defineOptions({ name: 'OcpxMonitorTaskManagement' });

const rows = ref<MonitorTask[]>([]);
const advertisers = ref<AdvertiserV2[]>([]);
const links = ref<PromotionLink[]>([]);
const media = ref<Media[]>([]);
const loading = ref(false);
const total = ref(0);
const open = ref(false);
const editing = ref<MonitorTask>();
const query = reactive({ current: 1, keyword: '', size: 20, status: '' });
const form = reactive({
  advertiserId: '',
  mediaId: '',
  operatorUserId: '',
  promotionLinkId: '',
  taskCode: '',
  taskName: '',
});

const availableLinks = computed(() =>
  links.value.filter((item) => item.advertiserId === form.advertiserId),
);

async function load() {
  loading.value = true;
  try {
    const result = await listMonitorTasks({
      ...query,
      status: query.status || undefined,
    });
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

async function loadOptions() {
  const [advertiserPage, linkPage, mediaPage] = await Promise.all([
    listAdvertisers({ current: 1, size: 500, status: 'ACTIVE' }),
    listPromotionLinks({ current: 1, size: 500, status: 'ACTIVE' }),
    listMedia({ current: 1, size: 500, status: 'ACTIVE' }),
  ]);
  advertisers.value = advertiserPage.records;
  links.value = linkPage.records;
  media.value = mediaPage.records;
}

function edit(row?: MonitorTask) {
  editing.value = row;
  Object.assign(form, {
    advertiserId: row?.advertiserId || '',
    mediaId: row?.mediaId || '',
    operatorUserId: row?.operatorUserId || '',
    promotionLinkId: row?.promotionLinkId || '',
    taskCode: row?.taskCode || '',
    taskName: row?.taskName || '',
  });
  open.value = true;
}

async function save() {
  const data = {
    advertiserId: form.advertiserId,
    mediaId: form.mediaId,
    operatorUserId: form.operatorUserId || undefined,
    promotionLinkId: form.promotionLinkId,
    taskName: form.taskName,
  };
  if (editing.value) {
    await updateMonitorTask(editing.value.id, {
      ...data,
      expectedVersion: editing.value.configVersion,
    });
  } else {
    await createMonitorTask({ ...data, taskCode: form.taskCode });
  }
  ElMessage.success('保存成功');
  open.value = false;
  await load();
}

async function toggle(row: MonitorTask) {
  await changeMonitorTaskStatus(row.id, {
    expectedVersion: row.configVersion,
    status: row.status === 'PAUSED' ? 'ACTIVE' : 'PAUSED',
  });
  await load();
}

async function copyAddress(row: MonitorTask) {
  await navigator.clipboard.writeText(row.trackingUrl);
  ElMessage.success('监测地址已复制');
}

async function archive(row: MonitorTask) {
  await ElMessageBox.confirm('确认归档该监测任务？', '归档监测任务');
  await archiveMonitorTask(row.id, { expectedVersion: row.configVersion });
  await load();
}

onMounted(async () => {
  await loadOptions();
  await load();
});
</script>

<template>
  <Page auto-content-height title="监测任务">
    <el-card shadow="never">
      <div class="mb-4 flex gap-3">
        <el-input v-model="query.keyword" clearable placeholder="编码 / 名称" />
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="暂停" value="PAUSED" />
        </el-select>
        <el-button @click="load">查询</el-button>
        <el-button
          v-access:code="'ocpx:monitor-task:add'"
          type="primary"
          @click="edit()"
        >
          新增任务
        </el-button>
      </div>
      <el-alert
        class="mb-4"
        :closable="false"
        title="任务直接绑定一个广告主、一个推广链接和一个媒体；启用后 Feedback、Callback 即按此关系转发。"
        type="info"
      />
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="编码" prop="taskCode" />
        <el-table-column label="名称" prop="taskName" />
        <el-table-column label="广告主" prop="advertiserName" />
        <el-table-column label="推广链接" prop="promotionLinkName" />
        <el-table-column label="媒体" prop="mediaName" />
        <el-table-column label="状态" prop="status" />
        <el-table-column fixed="right" label="操作" width="280">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'ARCHIVED'"
              v-access:code="'ocpx:monitor-task:edit'"
              link
              type="primary"
              @click="edit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status !== 'ARCHIVED'"
              v-access:code="'ocpx:monitor-task:status'"
              link
              @click="toggle(row)"
            >
              {{ row.status === 'PAUSED' ? '恢复' : '暂停' }}
            </el-button>
            <el-button link @click="copyAddress(row)">复制地址</el-button>
            <el-button
              v-if="row.status !== 'ARCHIVED'"
              v-access:code="'ocpx:monitor-task:archive'"
              link
              type="danger"
              @click="archive(row)"
            >
              归档
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="mt-4"
        :total="total"
        @change="load"
      />
    </el-card>

    <el-dialog
      v-model="open"
      :title="editing ? '编辑监测任务' : '新增监测任务'"
      width="680px"
    >
      <el-form :model="form" label-width="110px">
        <el-form-item label="任务编码" required>
          <el-input v-model="form.taskCode" :disabled="!!editing" />
        </el-form-item>
        <el-form-item label="任务名称" required>
          <el-input v-model="form.taskName" />
        </el-form-item>
        <el-form-item label="广告主" required>
          <el-select
            v-model="form.advertiserId"
            @change="form.promotionLinkId = ''"
          >
            <el-option
              v-for="item in advertisers"
              :key="item.id"
              :label="item.advertiserName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="推广链接" required>
          <el-select v-model="form.promotionLinkId">
            <el-option
              v-for="item in availableLinks"
              :key="item.id"
              :label="item.linkName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="媒体" required>
          <el-select v-model="form.mediaId">
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.mediaName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="运营人员 ID">
          <el-input v-model="form.operatorUserId" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </Page>
</template>
