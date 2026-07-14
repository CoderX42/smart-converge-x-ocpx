<script lang="ts" setup name="miniAuth">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search, View } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage } from '#/api/open/mini-auth';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const {
  mini_auth_customer_type,
  mini_auth_apply_status,
  mini_auth_task_status,
} = useDict(
  'mini_auth_customer_type',
  'mini_auth_apply_status',
  'mini_auth_task_status',
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const Detail = defineAsyncComponent(() => import('./detail.vue'));

const state = reactive({
  queryParams: {
    accountName: '',
    customerType: '',
    status: '',
  },
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
const detailRef = ref();
const queryRef = ref();

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
 * 搜索
 */
const handleQuery = () => {
  state.page.currentPage = 1;
  initPage();
};

/**
 * 查看详情
 */
const viewDetail = (row: any) => {
  detailRef.value.initForm(row);
};

// 页面初始化
initPage();
</script>

<template>
  <div class="app-layout-container">
    <div class="app-layout-container-auto app-layout-container-view">
      <!-- 搜索表单 -->
      <ElForm
        ref="queryRef"
        :model="state.queryParams"
        :inline="true"
        v-show="showSearch"
        label-width="100px"
      >
        <ElFormItem label="小程序名称" prop="accountName">
          <ElInput
            v-model="state.queryParams.accountName"
            placeholder="请输入小程序名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </ElFormItem>
        <ElFormItem label="主体类型" prop="customerType">
          <ElInput
            v-model="state.queryParams.customerType"
            placeholder="请选择主体类型"
            clearable
            style="width: 200px"
          />
        </ElFormItem>
        <ElFormItem label="认证状态" prop="status">
          <ElInput
            v-model="state.queryParams.status"
            placeholder="请选择认证状态"
            clearable
            style="width: 200px"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" :icon="Search" @click="handleQuery">
            搜索
          </ElButton>
          <ElButton :icon="Refresh" @click="resetQuery">重置</ElButton>
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

      <Detail ref="detailRef" />

      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="accountName" label="小程序名称" min-width="150" />
        <ElTableColumn prop="customerType" label="主体类型" width="120">
          <template #default="scope">
            <DictTag
              :options="mini_auth_customer_type"
              :value="scope.row.customerType"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="contactName" label="联系人姓名" width="120" />
        <ElTableColumn prop="contactEmail" label="联系人邮箱" min-width="140" />
        <ElTableColumn prop="contactMobile" label="联系电话" width="130" />
        <ElTableColumn prop="taskStatus" label="任务状态" width="200">
          <template #default="scope">
            <DictTag
              v-if="scope.row.taskStatus === 12"
              :options="mini_auth_apply_status"
              :value="scope.row.applyStatus"
            />
            <DictTag
              v-else
              :options="mini_auth_task_status"
              :value="scope.row.taskStatus"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="authMsg" label="提示信息" width="180" />
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn prop="updateTime" label="更新时间" width="180" />
        <ElTableColumn label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              @click="viewDetail(scope.row)"
              v-access:code="'open:mini-auth:get'"
              :icon="View"
            >
              查看
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
  </div>
</template>

<style scoped>
.app-table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
</style>
