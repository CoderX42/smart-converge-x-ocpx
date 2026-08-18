<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { DeductionRule, EntityOption } from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createDeductionRule,
  deductionPercentageToRatio,
  deductionRatioToPercentage,
  deleteDeductionRule,
  listDeductionRules,
  mediaOptions,
  promotionLinkOptions,
  updateDeductionRule,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import {
  allColumnKeys,
  createLatestRequestGuard,
  PAGE_SIZES,
  resetPage,
} from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxDeductionRuleManagement' });

const rows = ref<DeductionRule[]>([]);
const links = ref<EntityOption[]>([]);
const media = ref<EntityOption[]>([]);
const loading = ref(false);
const deletingId = ref('');
const total = ref(0);
const showSearch = ref(true);
const columns = [
  { key: 'id', label: '规则编号' },
  { key: 'link', label: '推广链接' },
  { key: 'media', label: '媒体' },
  { key: 'ratio', label: '媒体回传比例' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '修改时间' },
];
const visibleColumns = ref(allColumnKeys(columns));
const open = ref(false);
const editing = ref<DeductionRule>();
const submitLoading = ref(false);
const formRef = ref<FormInstance>();
const query = reactive({ current: 1, promotionLinkId: '', size: 30 });
const form = reactive<{
  mediaId: string;
  promotionLinkId: string;
  ratio: number | string;
}>({
  mediaId: '',
  promotionLinkId: '',
  ratio: 100,
});

function validateRatio(
  _rule: unknown,
  value: number | string,
  callback: (error?: Error) => void,
) {
  if (value === '' || value === undefined || value === null) {
    callback(new Error('请输入媒体回传比例'));
    return;
  }
  const number = Number(value);
  if (Number.isFinite(number) && number >= 0 && number <= 100) {
    callback();
  } else {
    callback(new Error('媒体回传比例必须在 0%～100% 之间'));
  }
}

const rules: FormRules = {
  mediaId: [{ message: '请选择渠道', required: true, trigger: 'blur' }],
  promotionLinkId: [
    { message: '请选择应用链接', required: true, trigger: 'blur' },
  ],
  ratio: [
    { message: '请输入媒体回传比例', required: true, trigger: 'blur' },
    { trigger: 'blur', validator: validateRatio },
  ],
};
const { hasAccessByCodes } = useAccess();
const canOperate = computed(() =>
  hasAccessByCodes([
    'ocpx:deduction-rule:update',
    'ocpx:deduction-rule:delete',
  ]),
);
const loadGuard = createLatestRequestGuard();

async function load() {
  const requestSequence = loadGuard.begin();
  loading.value = true;
  try {
    const result = await listDeductionRules({
      ...query,
      promotionLinkId: query.promotionLinkId || undefined,
    });
    if (loadGuard.isLatest(requestSequence)) {
      rows.value = result.records;
      total.value = result.total;
    }
  } finally {
    if (loadGuard.isLatest(requestSequence)) loading.value = false;
  }
}

async function loadOptions() {
  const [linkPage, mediaPage] = await Promise.all([
    promotionLinkOptions(),
    mediaOptions(),
  ]);
  links.value = linkPage;
  media.value = mediaPage;
}

function toQuery() {
  resetPage(query);
  void load();
}

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

function edit(row?: DeductionRule) {
  editing.value = row;
  Object.assign(form, {
    mediaId: row?.mediaId || '',
    promotionLinkId: row?.promotionLinkId || '',
    ratio: deductionRatioToPercentage(row?.ratio ?? 1),
  });
  open.value = true;
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  submitLoading.value = true;
  try {
    const data = {
      mediaId: form.mediaId,
      promotionLinkId: form.promotionLinkId,
      ratio: deductionPercentageToRatio(Number(form.ratio)),
    };
    if (editing.value) {
      await updateDeductionRule(editing.value.id, data);
      ElMessage.success('编辑成功');
    } else {
      await createDeductionRule(data);
      ElMessage.success('新增成功');
    }
    open.value = false;
    await load();
  } finally {
    submitLoading.value = false;
  }
}

async function remove(row: DeductionRule) {
  await ElMessageBox.confirm('此操作将永久删除, 是否继续?', '提示', {
    cancelButtonText: '取消',
    confirmButtonText: '确定',
    type: 'warning',
  });
  deletingId.value = row.id;
  try {
    await deleteDeductionRule(row.id);
    ElMessage.success('删除成功!');
    await load();
  } catch {
    ElMessage.error('删除异常!');
  } finally {
    deletingId.value = '';
  }
}

function handleDialogClose() {
  editing.value = undefined;
  formRef.value?.resetFields();
  Object.assign(form, { mediaId: '', promotionLinkId: '', ratio: 100 });
}

function ratioText(ratio: number) {
  const passed = deductionRatioToPercentage(ratio ?? 1);
  return `${passed}%（预计扣除 ${Math.round((100 - passed) * 100) / 100}%）`;
}

onMounted(async () => {
  await loadOptions();
  await load();
});
</script>

<template>
  <Page auto-content-height title="媒体回传扣量规则">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-select
          v-model="query.promotionLinkId"
          clearable
          filterable
          aria-label="按推广链接查询扣量规则"
          placeholder="推广链接"
          @change="toQuery"
        >
          <el-option
            v-for="item in links"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-button :disabled="loading" type="primary" @click="toQuery"
          >搜索</el-button
        >
      </div>
      <div class="ocpx-actions">
        <el-button
          v-access:code="'ocpx:deduction-rule:create'"
          :disabled="loading || submitLoading || Boolean(deletingId)"
          type="primary"
          @click="edit()"
        >
          新增
        </el-button>
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
        <el-table-column v-if="hasColumn('id')" label="规则编号" min-width="170"
          ><template #default="{ row }"><LongText :value="row.id" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('link')"
          label="推广链接"
          min-width="160"
          prop="promotionLinkName"
        />
        <el-table-column
          v-if="hasColumn('media')"
          label="媒体"
          min-width="150"
          prop="mediaName"
        />
        <el-table-column
          v-if="hasColumn('ratio')"
          label="媒体回传比例"
          min-width="210"
          prop="ratio"
        >
          <template #default="{ row }">{{ ratioText(row.ratio) }}</template>
        </el-table-column>
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
          width="160"
        >
          <template #default="{ row }">
            <el-button
              v-access:code="'ocpx:deduction-rule:update'"
              :disabled="submitLoading || Boolean(deletingId)"
              link
              type="primary"
              @click="edit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-access:code="'ocpx:deduction-rule:delete'"
              :disabled="submitLoading || Boolean(deletingId)"
              :loading="deletingId === row.id"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
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
      v-model="open"
      :close-on-click-modal="false"
      :title="editing ? '扣量规则 编辑' : '扣量规则 新增'"
      width="800px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="媒体" prop="mediaId">
          <el-select v-model="form.mediaId" filterable>
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="推广链接" prop="promotionLinkId">
          <el-select v-model="form.promotionLinkId" filterable>
            <el-option
              v-for="item in links"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="媒体回传比例" prop="ratio">
          <el-input-number
            v-model="form.ratio"
            :max="100"
            :min="0"
            :precision="2"
            :step="1"
          />
          <span class="ml-2">%</span>
          <div class="w-full text-xs text-gray-500">
            100% 全部回传，0% 全部拦截；预计扣除比例 = 100% - 回传比例。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitLoading" @click="open = false"
          >取消</el-button
        >
        <el-button :loading="submitLoading" type="primary" @click="save">
          确定
        </el-button>
      </template>
    </el-dialog>
  </Page>
</template>
