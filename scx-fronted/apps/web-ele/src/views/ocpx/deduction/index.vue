<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { DeductionRule, Media, PromotionLink } from '#/api/ocpx';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createDeductionRule,
  deleteDeductionRule,
  listDeductionRules,
  listMedia,
  listPromotionLinks,
  updateDeductionRule,
} from '#/api/ocpx';

defineOptions({ name: 'OcpxDeductionRuleManagement' });

const rows = ref<DeductionRule[]>([]);
const links = ref<PromotionLink[]>([]);
const media = ref<Media[]>([]);
const loading = ref(false);
const total = ref(0);
const open = ref(false);
const editing = ref<DeductionRule>();
const submitLoading = ref(false);
const formRef = ref<FormInstance>();
const query = reactive({ current: 1, promotionLinkId: '', size: 20 });
const form = reactive<{ mediaId: string; promotionLinkId: string; ratio: number | string }>({
  mediaId: '',
  promotionLinkId: '',
  ratio: 1,
});

function validateRatio(
  _rule: unknown,
  value: number | string,
  callback: (error?: Error) => void,
) {
  if (value === '' || value === undefined || value === null) {
    callback(new Error('请输入扣量比例'));
    return;
  }
  if (/^\d+(?:\.\d+)?$/.test(String(value))) {
    callback();
  } else {
    callback(new Error('请输入正确的数字格式'));
  }
}

const rules: FormRules = {
  mediaId: [{ message: '请选择渠道', required: true, trigger: 'blur' }],
  promotionLinkId: [
    { message: '请选择应用链接', required: true, trigger: 'blur' },
  ],
  ratio: [
    { message: '请输入扣量比例', required: true, trigger: 'blur' },
    { trigger: 'blur', validator: validateRatio },
  ],
};

async function load() {
  loading.value = true;
  try {
    const result = await listDeductionRules({
      ...query,
      promotionLinkId: query.promotionLinkId || undefined,
    });
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

async function loadOptions() {
  const [linkPage, mediaPage] = await Promise.all([
    listPromotionLinks({ current: 1, size: 500, status: 'ACTIVE' }),
    listMedia({ current: 1, size: 500, status: 'ACTIVE' }),
  ]);
  links.value = linkPage.records;
  media.value = mediaPage.records;
}

function toQuery() {
  query.current = 1;
  load();
}

function edit(row?: DeductionRule) {
  editing.value = row;
  Object.assign(form, {
    mediaId: row?.mediaId || '',
    promotionLinkId: row?.promotionLinkId || '',
    ratio: row?.ratio ?? 1,
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
      ratio: Number(form.ratio),
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
  try {
    await deleteDeductionRule(row.id);
    ElMessage.success('删除成功!');
    await load();
  } catch {
    ElMessage.error('删除异常!');
  }
}

function handleDialogClose() {
  editing.value = undefined;
  formRef.value?.resetFields();
  Object.assign(form, { mediaId: '', promotionLinkId: '', ratio: 1 });
}

onMounted(async () => {
  await loadOptions();
  await load();
});
</script>

<template>
  <Page auto-content-height title="扣量规则">
    <el-card shadow="never">
      <div class="mb-4 flex gap-3">
        <el-select
          v-model="query.promotionLinkId"
          clearable
          filterable
          placeholder="应用链接"
        >
          <el-option
            v-for="item in links"
            :key="item.id"
            :label="item.linkName"
            :value="item.id"
          />
        </el-select>
        <el-button type="primary" @click="toQuery">搜索</el-button>
        <el-button
          v-access:code="'ocpx:deduction-rule:create'"
          type="primary"
          @click="edit()"
        >
          新增
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="规则编号" prop="id" />
        <el-table-column label="应用链接" prop="promotionLinkName" />
        <el-table-column label="渠道编号" prop="mediaName" />
        <el-table-column label="扣量比例" prop="ratio" />
        <el-table-column label="创建时间" prop="createTime" />
        <el-table-column label="修改时间" prop="updateTime" />
        <el-table-column fixed="right" label="操作" width="160">
          <template #default="{ row }">
            <el-button
              v-access:code="'ocpx:deduction-rule:update'"
              link
              type="primary"
              @click="edit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-access:code="'ocpx:deduction-rule:delete'"
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
        class="mt-4"
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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属渠道" prop="mediaId">
          <el-select v-model="form.mediaId" filterable>
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.mediaName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属应用链接" prop="promotionLinkId">
          <el-select v-model="form.promotionLinkId" filterable>
            <el-option
              v-for="item in links"
              :key="item.id"
              :label="item.linkName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="扣量比例" prop="ratio">
          <el-input v-model="form.ratio" placeholder="请输入扣量比例" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button :loading="submitLoading" type="primary" @click="save">
          确定
        </el-button>
      </template>
    </el-dialog>
  </Page>
</template>
