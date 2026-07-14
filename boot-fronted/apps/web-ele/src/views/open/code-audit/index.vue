<script lang="ts" setup name="wxApp">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  delObj,
  getAuditStatus,
  getPage,
  submitAudit,
  undoCodeAudit,
} from '#/api/open/code-audit';
import { useDict } from '#/utils/dict';

const { code_audit_status } = useDict('code_audit_status');
const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const QrCodeDialog = defineAsyncComponent(
  () => import('./components/QrCodeDialog.vue'),
);
const state = reactive({
  queryParams: { auditId: '' },
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 10, // 每页显示多少条
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const codeLoading = ref(false);
const queryRef = ref();
const qrCodeDialogVisible = ref(false);
const currentAuditId = ref('');
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response: any) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
  initPage();
};

/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该代码提审记录，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id).then(() => {
      ElMessage.success('删除成功');
      initPage();
    });
  });
};
const getAuditStatusFn = (id: string) => {
  getAuditStatus(id).then(() => {
    ElMessage.success('操作成功');
    initPage();
  });
};
const getQrCodeFn = (id: string) => {
  currentAuditId.value = id;
  qrCodeDialogVisible.value = true;
};
/**
 * 撤回
 * @param id
 */
const handlerUndoCodeAudit = async (id: string) => {
  const confirmed = await ElMessageBox.confirm(
    '此操作将撤回该代码提审，是否继续?',
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  )
    .then(() => true)
    .catch(() => false);
  if (!confirmed) return;

  try {
    codeLoading.value = true;
    await undoCodeAudit(id);
    ElMessage.success('操作成功');
    initPage();
  } finally {
    codeLoading.value = false;
  }
};
/**
 * 提交审核
 */
const handlerSubmitAudit = async (id: string) => {
  const confirmed = await ElMessageBox.confirm(
    '此操作将代码提审，是否继续?',
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  )
    .then(() => true)
    .catch(() => false);
  if (!confirmed) return;

  try {
    codeLoading.value = true;
    await submitAudit(id);
    ElMessage.success('提交成功');
    initPage();
  } finally {
    codeLoading.value = false;
  }
};
initPage();
</script>
<template>
  <div class="app-layout-container">
    <div class="app-layout-container-auto app-layout-container-view">
      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="审核ID" prop="auditId">
          <ElInput
            v-model="state.queryParams.auditId"
            clearable
            placeholder="请输入审核ID"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>
      <!-- 工具栏 -->
      <div class="app-table-toolbar">
        <div></div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="appId" label="应用ID" />
        <ElTableColumn prop="appName" label="应用名称" />
        <ElTableColumn prop="auditId" label="审核ID" />
        <ElTableColumn prop="userVersion" label="版本号" />
        <ElTableColumn prop="status" label="状态">
          <template #default="scope">
            <DictTag :options="code_audit_status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="reason" label="驳回原因" show-overflow-tooltip />
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" width="250" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-if="scope.row.status === '5'"
              @click="handlerSubmitAudit(scope.row.id)"
              :disabled="codeLoading"
              v-access:code="'open:code-audit:submit'"
            >
              提交审核
            </ElButton>
            <ElButton
              link
              type="primary"
              v-if="scope.row.status === '2'"
              @click="handlerUndoCodeAudit(scope.row.id)"
              :disabled="codeLoading"
              v-access:code="'open:code-audit:edit'"
            >
              撤回
            </ElButton>
            <ElButton
              link
              v-if="scope.row.status === '2'"
              @click="getAuditStatusFn(scope.row.id)"
              v-access:code="'open:code-audit:get'"
            >
              查询审核状态
            </ElButton>
            <ElButton
              link
              @click="getQrCodeFn(scope.row.id)"
              v-access:code="'open:code-audit:get'"
            >
              体验二维码
            </ElButton>
            <ElButton
              link
              v-if="
                scope.row.status === '1' ||
                scope.row.status === '3' ||
                scope.row.status === '5'
              "
              type="danger"
              @click="del(scope.row.id)"
              v-access:code="'open:template-info:del'"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
    </div>

    <!-- 二维码弹窗 -->
    <QrCodeDialog
      v-model:visible="qrCodeDialogVisible"
      :audit-id="currentAuditId"
    />
  </div>
</template>
