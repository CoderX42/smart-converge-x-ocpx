<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { EntityOption, MonitorTask } from '#/api/ocpx';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createMonitorTask,
  deleteMonitorTask,
  listMonitorTasks,
  mediaOptions,
  monitorTaskOptions,
  promotionLinkAdvertisers,
  promotionLinkOptions,
  updateMonitorTask,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import {
  allColumnKeys,
  createLatestRequestGuard,
  PAGE_SIZES,
  platformLabel,
  resetPage,
} from '../table';

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
const mediaSelects = ref<EntityOption[]>([]);
const advertiserSelects = ref<EntityOption[]>([]);
const promotionOptions = ref<EntityOption[]>([]);
const monitorSelects = ref<EntityOption[]>([]);
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
  unitPrice: '' as number | string,
});

function validateNumber(
  _rule: unknown,
  value: number | string,
  callback: (error?: Error) => void,
) {
  if (value === '' || value === undefined || value === null) {
    callback(new Error('输入不能为空'));
    return;
  }
  if (/^\d+(?:\.\d+)?$/.test(String(value))) {
    callback();
  } else {
    callback(new Error('请输入正确的数字格式'));
  }
}

const rules: FormRules = {
  advertiserId: [{ message: '请选择广告主', required: true, trigger: 'blur' }],
  mediaId: [{ message: '请选择渠道', required: true, trigger: 'blur' }],
  monitorName: [{ message: '请输入监测名称', required: true, trigger: 'blur' }],
  promotionLinkId: [
    { message: '请选择投放链接', required: true, trigger: 'blur' },
  ],
  unitPrice: [
    { message: '请输入投放单价', required: true, trigger: 'blur' },
    { trigger: 'blur', validator: validateNumber },
  ],
};
const { hasAccessByCodes } = useAccess();
const canOperate = computed(() =>
  hasAccessByCodes(['ocpx:monitor-task:edit', 'ocpx:monitor-task:delete']),
);
const loadGuard = createLatestRequestGuard();
const monitorOptionGuard = createLatestRequestGuard();
const promotionOptionGuard = createLatestRequestGuard();

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

async function load() {
  const requestSequence = loadGuard.begin();
  loading.value = true;
  try {
    const result = await listMonitorTasks({
      current: query.current,
      mediaId: query.mediaId || undefined,
      monitorTaskId: query.monitorTaskId || undefined,
      size: query.size,
    });
    if (loadGuard.isLatest(requestSequence)) {
      rows.value = result.records;
      total.value = result.total;
    }
  } finally {
    if (loadGuard.isLatest(requestSequence)) loading.value = false;
  }
}

async function loadMonitorSelects(reset = true) {
  if (reset) query.monitorTaskId = '';
  const requestSequence = monitorOptionGuard.begin();
  const result = await monitorTaskOptions(query.mediaId || undefined);
  if (monitorOptionGuard.isLatest(requestSequence)) {
    monitorSelects.value = result;
  }
}

async function loadPromotionOptions(advertiserId: string, keepId = false) {
  if (!keepId) form.promotionLinkId = '';
  const requestSequence = promotionOptionGuard.begin();
  if (!advertiserId) {
    promotionOptions.value = [];
    return;
  }
  const result = await promotionLinkOptions(advertiserId);
  if (!promotionOptionGuard.isLatest(requestSequence)) return;
  promotionOptions.value = result;
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
    unitPrice: row?.unitPrice ?? '',
  });
  await loadPromotionOptions(form.advertiserId, true);
  dialogOpen.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    const editingId = editing.value?.id;
    const payload = {
      advertiserId: form.advertiserId,
      bizId: form.bizId || undefined,
      mediaId: form.mediaId,
      monitorName: form.monitorName,
      promotionLinkId: form.promotionLinkId,
      remark: form.remark || undefined,
      unitPrice: Number(form.unitPrice),
    };
    await (editingId
      ? updateMonitorTask(editingId, payload)
      : createMonitorTask(payload));
    ElMessage.success(editingId ? '编辑成功' : '新增成功');
    dialogOpen.value = false;
    await loadMonitorSelects(false);
    await load();
  } finally {
    saving.value = false;
  }
}

function handleDialogClose() {
  editing.value = undefined;
  formRef.value?.resetFields();
  promotionOptions.value = [];
  Object.assign(form, {
    advertiserId: '',
    bizId: '',
    mediaId: '',
    monitorName: '',
    promotionLinkId: '',
    remark: '',
    unitPrice: '',
  });
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
  const [media, advertisers, monitors] = await Promise.all([
    mediaOptions(),
    promotionLinkAdvertisers(),
    monitorTaskOptions(),
  ]);
  mediaSelects.value = media;
  advertiserSelects.value = advertisers;
  monitorSelects.value = monitors;
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
          placeholder="请选择渠道"
          @change="loadMonitorSelects()"
        >
          <el-option
            v-for="item in mediaSelects"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="query.monitorTaskId"
          aria-label="按监测任务查询"
          clearable
          filterable
          placeholder="请选择监测任务"
        >
          <el-option
            v-for="item in monitorSelects"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-button :disabled="loading" type="primary" @click="search"
          >搜索</el-button
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
        <el-table-column
          v-if="canOperate"
          fixed="right"
          label="操作"
          width="130"
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
      :close-on-click-modal="false"
      :title="editing ? '编辑监测任务' : '新增监测任务'"
      width="680px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="所属渠道" prop="mediaId"
          ><el-select v-model="form.mediaId" filterable placeholder="请选择渠道"
            ><el-option
              v-for="item in mediaSelects"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="所属广告主" prop="advertiserId"
          ><el-select
            v-model="form.advertiserId"
            filterable
            placeholder="请选择广告主"
            @change="loadPromotionOptions(form.advertiserId)"
            ><el-option
              v-for="item in advertiserSelects"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="投放链接" prop="promotionLinkId"
          ><el-select
            v-model="form.promotionLinkId"
            filterable
            placeholder="请选择投放链接"
            ><el-option
              v-for="item in promotionOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="监测名称" prop="monitorName"
          ><el-input
            v-model="form.monitorName"
            autosize
            placeholder="请输入监测名称"
            type="textarea"
        /></el-form-item>
        <el-form-item label="业务编号" prop="bizId"
          ><el-input
            v-model="form.bizId"
            autosize
            placeholder="请输入业务编号"
            type="textarea"
        /></el-form-item>
        <el-form-item label="投放单价" prop="unitPrice"
          ><el-input v-model="form.unitPrice" placeholder="请输入投放单价"
        /></el-form-item>
        <el-form-item label="备注" prop="remark"
          ><el-input
            v-model="form.remark"
            autosize
            placeholder="请输入备注"
            type="textarea"
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
          >确定</el-button
        ></template
      >
    </el-dialog>
  </Page>
</template>
