<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type {
  EntityOption,
  Media,
  MonitorTask,
  PromotionLink,
} from '#/api/ocpx';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createMonitorTask,
  deleteMonitorTask,
  listMedia,
  listMonitorTasks,
  listPromotionLinks,
  promotionLinkAdvertisers,
  updateMonitorTask,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import { allColumnKeys, PAGE_SIZES, platformLabel, resetPage } from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxMonitorTaskManagement' });

const columns = [
  { key: 'id', label: '监测编号' },
  { key: 'media', label: '媒体' },
  { key: 'link', label: '推广链接' },
  { key: 'advertiser', label: '广告主' },
  { key: 'bizId', label: '业务编号' },
  { key: 'platform', label: '平台' },
  { key: 'unitPrice', label: '单价' },
  { key: 'showUrl', label: '展示监测链接' },
  { key: 'clkUrl', label: '点击监测链接' },
  { key: 'remark', label: '备注' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '修改时间' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<MonitorTask[]>([]);
const mediaOptions = ref<Media[]>([]);
const advertiserOptions = ref<EntityOption[]>([]);
const promotionOptions = ref<PromotionLink[]>([]);
const monitorOptions = ref<MonitorTask[]>([]);
const loading = ref(false);
const saving = ref(false);
const deletingId = ref('');
const total = ref(0);
const showSearch = ref(true);
const query = reactive({
  current: 1,
  mediaId: '',
  monitorTaskId: '',
  size: 30,
});

const dialogOpen = ref(false);
const editing = ref<MonitorTask>();
const formRef = ref<FormInstance>();
const form = reactive({
  advertiserId: '',
  bizId: '',
  mediaId: '',
  monitorName: '',
  promotionLinkId: '',
  remark: '',
  unitPrice: 0,
});
const rules: FormRules = {
  advertiserId: [{ message: '请选择广告主', required: true }],
  mediaId: [{ message: '请选择媒体', required: true }],
  monitorName: [{ message: '请输入监测名称', required: true }],
  promotionLinkId: [{ message: '请选择所属链接', required: true }],
  unitPrice: [{ message: '请输入单价', required: true }],
};

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

async function load() {
  loading.value = true;
  try {
    const result = await listMonitorTasks({
      current: query.current,
      mediaId: query.mediaId || undefined,
      monitorTaskId: query.monitorTaskId || undefined,
      size: query.size,
    });
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

async function loadMonitorOptions() {
  query.monitorTaskId = '';
  const result = await listMonitorTasks({
    current: 1,
    mediaId: query.mediaId || undefined,
    size: 100,
  });
  monitorOptions.value = result.records;
  resetPage(query);
  await load();
}

async function loadPromotionOptions(advertiserId: string, keepId = false) {
  if (!keepId) form.promotionLinkId = '';
  if (!advertiserId) {
    promotionOptions.value = [];
    return;
  }
  const result = await listPromotionLinks({
    advertiserId,
    current: 1,
    size: 100,
  });
  promotionOptions.value = result.records;
  if (
    form.promotionLinkId &&
    !promotionOptions.value.some(({ id }) => id === form.promotionLinkId)
  ) {
    form.promotionLinkId = '';
  }
}

function search() {
  resetPage(query);
  void load();
}

async function edit(row?: MonitorTask) {
  editing.value = row;
  Object.assign(form, {
    advertiserId: row?.advertiserId || '',
    bizId: row?.bizId || '',
    mediaId: row?.mediaId || '',
    monitorName: row?.monitorName || '',
    promotionLinkId: row?.promotionLinkId || '',
    remark: row?.remark || '',
    unitPrice: row?.unitPrice ?? 0,
  });
  await loadPromotionOptions(form.advertiserId, true);
  dialogOpen.value = true;
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const payload = {
      ...form,
      bizId: form.bizId || undefined,
      remark: form.remark || undefined,
    };
    await (editing.value
      ? updateMonitorTask(editing.value.id, payload)
      : createMonitorTask(payload));
    ElMessage.success('保存成功');
    dialogOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function remove(row: MonitorTask) {
  await ElMessageBox.confirm(
    '删除后该监测任务不再可用，确认继续？',
    '删除监测任务',
    {
      confirmButtonText: '确认删除',
      type: 'warning',
    },
  );
  deletingId.value = row.id;
  try {
    await deleteMonitorTask(row.id);
    ElMessage.success('删除成功');
    await load();
  } finally {
    deletingId.value = '';
  }
}

onMounted(async () => {
  const [mediaResult, advertisers, monitors] = await Promise.all([
    listMedia({ current: 1, size: 100 }),
    promotionLinkAdvertisers(),
    listMonitorTasks({ current: 1, size: 100 }),
  ]);
  mediaOptions.value = mediaResult.records;
  advertiserOptions.value = advertisers;
  monitorOptions.value = monitors.records;
  await load();
});
</script>

<template>
  <Page auto-content-height title="监测任务">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-select
          v-model="query.mediaId"
          aria-label="按媒体查询监测任务"
          clearable
          filterable
          placeholder="媒体"
          @change="loadMonitorOptions"
        >
          <el-option
            v-for="item in mediaOptions"
            :key="item.id"
            :label="item.mediaName"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="query.monitorTaskId"
          aria-label="按监测任务查询"
          clearable
          filterable
          placeholder="监测任务"
          @change="search"
        >
          <el-option
            v-for="item in monitorOptions"
            :key="item.id"
            :label="item.monitorName"
            :value="item.id"
          />
        </el-select>
        <el-button :disabled="loading" type="primary" @click="search"
          >查询</el-button
        >
        <el-button
          :disabled="loading"
          @click="
            Object.assign(query, { mediaId: '', monitorTaskId: '' });
            loadMonitorOptions();
          "
          >重置</el-button
        >
      </div>
      <div class="ocpx-actions">
        <el-button
          v-access:code="'ocpx:monitor-task:add'"
          :disabled="loading || saving || Boolean(deletingId)"
          type="primary"
          @click="edit()"
          >新增任务</el-button
        >
        <RightToolbar
          v-model:visible-columns="visibleColumns"
          :columns="columns"
          :loading="loading"
          refresh-btn
          search-btn
          @refresh="load"
          @search="showSearch = !showSearch"
        />
      </div>
      <el-table
        v-loading="loading"
        class="ocpx-table"
        :data="rows"
        highlight-current-row
        max-height="560"
        size="small"
      >
        <el-table-column v-if="hasColumn('id')" label="监测编号" min-width="170"
          ><template #default="{ row }"><LongText :value="row.id" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('media')"
          label="媒体"
          min-width="140"
          prop="mediaName"
        />
        <el-table-column
          v-if="hasColumn('link')"
          label="推广链接"
          min-width="150"
          prop="promotionLinkName"
        />
        <el-table-column
          v-if="hasColumn('advertiser')"
          label="广告主"
          min-width="150"
          prop="advertiserName"
        />
        <el-table-column
          v-if="hasColumn('bizId')"
          label="业务编号"
          min-width="150"
          ><template #default="{ row }"
            ><LongText :value="row.bizId" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('platform')"
          label="平台"
          min-width="120"
          ><template #default="{ row }">{{
            platformLabel[row.platform]
          }}</template></el-table-column
        >
        <el-table-column
          v-if="hasColumn('unitPrice')"
          label="单价"
          min-width="100"
          prop="unitPrice"
        />
        <el-table-column
          v-if="hasColumn('showUrl')"
          label="展示监测链接"
          min-width="240"
          ><template #default="{ row }"
            ><LongText :value="row.showUrl" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('clkUrl')"
          label="点击监测链接"
          min-width="240"
          ><template #default="{ row }"
            ><LongText :value="row.clkUrl" /></template
        ></el-table-column>
        <el-table-column v-if="hasColumn('remark')" label="备注" min-width="160"
          ><template #default="{ row }"
            ><LongText :value="row.remark" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('createTime')"
          label="创建时间"
          min-width="170"
          prop="createTime"
        />
        <el-table-column
          v-if="hasColumn('updateTime')"
          label="修改时间"
          min-width="170"
          prop="updateTime"
        />
        <el-table-column fixed="right" label="操作" width="130"
          ><template #default="{ row }"
            ><el-button
              v-access:code="'ocpx:monitor-task:edit'"
              :disabled="saving || Boolean(deletingId)"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            ><el-button
              v-access:code="'ocpx:monitor-task:delete'"
              :disabled="saving || Boolean(deletingId)"
              :loading="deletingId === row.id"
              link
              type="danger"
              @click="remove(row)"
              >删除</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="ocpx-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="PAGE_SIZES"
        :total="total"
        @change="load"
      />
    </el-card>

    <el-dialog
      v-model="dialogOpen"
      :title="editing ? '编辑监测任务' : '新增监测任务'"
      width="680px"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="媒体" prop="mediaId"
          ><el-select v-model="form.mediaId" filterable
            ><el-option
              v-for="item in mediaOptions"
              :key="item.id"
              :label="item.mediaName"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="广告主" prop="advertiserId"
          ><el-select
            v-model="form.advertiserId"
            filterable
            @change="loadPromotionOptions(form.advertiserId)"
            ><el-option
              v-for="item in advertiserOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="所属链接" prop="promotionLinkId"
          ><el-select v-model="form.promotionLinkId" filterable
            ><el-option
              v-for="item in promotionOptions"
              :key="item.id"
              :label="item.linkName"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="监测名称" prop="monitorName"
          ><el-input v-model="form.monitorName"
        /></el-form-item>
        <el-form-item label="业务编号" prop="bizId"
          ><el-input v-model="form.bizId"
        /></el-form-item>
        <el-form-item label="单价" prop="unitPrice"
          ><el-input-number v-model="form.unitPrice" :min="0" :precision="6"
        /></el-form-item>
        <el-form-item label="备注" prop="remark"
          ><el-input v-model="form.remark" :rows="3" type="textarea"
        /></el-form-item>
        <el-alert
          :closable="false"
          title="展示和点击监测 URL 由后端生成，无需手工填写。"
          type="info"
        />
      </el-form>
      <template #footer
        ><el-button :disabled="saving" @click="dialogOpen = false"
          >取消</el-button
        ><el-button :loading="saving" type="primary" @click="save"
          >保存</el-button
        ></template
      >
    </el-dialog>
  </Page>
</template>
