<script lang="ts" setup name="wxApp">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { useQRCode } from '@vueuse/integrations/useQRCode';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getPage, submit } from '#/api/open/miniapp-register';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const { miniapp_register_code_type, miniapp_register_status } = useDict(
  'miniapp_register_code_type',
  'miniapp_register_status',
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));
const state = reactive({
  queryParams: { name: '' },
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
const formRef = ref();
const queryRef = ref();
const qrcodeDialogVisible = ref(false);
const currentQrCodeUrl = ref('');

// 生成二维码
const qrcode = useQRCode(currentQrCodeUrl, {
  errorCorrectionLevel: 'H',
  margin: 2,
});
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
 * 新增按钮
 */
const add = () => {
  formRef.value.initForm();
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  formRef.value.initForm(row);
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该小程序注册，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};
const submitFn = async (row: any) => {
  try {
    loading.value = true;
    await submit(row.id);
    ElMessage.success('提交成功');
    initPage();
  } finally {
    loading.value = false;
  }
};
const authBetaFn = async (row: any) => {
  currentQrCodeUrl.value = row.authorizeUrl;
  qrcodeDialogVisible.value = true;
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
        <ElFormItem label="企业名/小程序名称" prop="name">
          <ElInput
            v-model="state.queryParams.name"
            clearable
            placeholder="请输入企业名"
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
        <div>
          <ElButton
            type="primary"
            v-access:code="'open:miniapp-register:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <Form ref="formRef" @init-page="initPage" />
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="name" label="企业名/小程序名称" />
        <ElTableColumn prop="codeType" label="企业代码类型">
          <template #default="scope">
            <DictTag
              :options="miniapp_register_code_type"
              :value="scope.row.codeType"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="code" label="企业代码" />
        <ElTableColumn prop="legalPersonaWechat" label="法人微信号" />
        <ElTableColumn prop="legalPersonaName" label="法人姓名" />
        <ElTableColumn prop="componentPhone" label="第三方联系电话" />
        <ElTableColumn prop="status" label="状态">
          <template #default="scope">
            <DictTag
              :options="miniapp_register_status"
              :value="scope.row.status"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              @click="authBetaFn(scope.row)"
              v-access:code="'open:miniapp-register:add'"
              v-if="scope.row.status === '2' && scope.row.type === '2'"
              :icon="Edit"
            >
              授权确认
            </ElButton>
            <ElButton
              link
              type="primary"
              @click="submitFn(scope.row)"
              v-access:code="'open:miniapp-register:add'"
              v-if="scope.row.status === '1'"
              :icon="Edit"
            >
              提交申请
            </ElButton>
            <ElButton
              link
              type="primary"
              @click="edit(scope.row)"
              v-access:code="'open:miniapp-register:edit'"
              v-if="scope.row.status === '1'"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              @click="del(scope.row.id)"
              v-access:code="'open:miniapp-register:del'"
              v-if="scope.row.status === '1'"
              :icon="Delete"
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

    <!-- 二维码预览弹窗 -->
    <ElDialog
      v-model="qrcodeDialogVisible"
      title="授权二维码"
      width="400px"
      :close-on-click-modal="false"
      :close-on-press-escape="true"
    >
      <div
        style="
          display: flex;
          align-items: center;
          justify-content: center;
          min-height: 300px;
        "
      >
        <img
          v-if="qrcode"
          :src="qrcode"
          alt="授权二维码"
          style="max-width: 100%; max-height: 300px; object-fit: contain"
        />
        <div v-else style="padding: 20px; color: #999">生成中...</div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <ElButton @click="qrcodeDialogVisible = false">关闭</ElButton>
        </span>
      </template>
    </ElDialog>
  </div>
</template>
