<script lang="ts" setup name="wxApp">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Plus, Refresh, Search } from '@element-plus/icons-vue';
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus';

import { openAuth } from '#/api/open/open-auth';
import { getPage } from '#/api/open/open-platform-app';

import MpAppCardList from './components/MpAppCardList.vue';
import MpAppTable from './components/MpAppTable.vue';
import ViewModeToggle from './components/ViewModeToggle.vue';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const Form = defineAsyncComponent(() => import('./form.vue'));
const state = reactive({
  queryParams: { appId: '', platformType: 'WX_H5' },
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
// 新增：视图模式切换
const viewMode = ref<'card' | 'list'>('card');

const initPage = async () => {
  loading.value = true;

  await getPage(Object.assign(state.page, state.queryParams))
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

// 切换视图模式
const toggleViewMode = () => {
  viewMode.value = viewMode.value === 'list' ? 'card' : 'list';
};

const openAuthorize = async () => {
  const res = await openAuth();
  window.open(res, '_blank');
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
        <ElFormItem label="应用ID" prop="appId">
          <ElInput
            v-model="state.queryParams.appId"
            clearable
            placeholder="请输入应用ID"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
          <ElButton type="primary" @click="openAuthorize">
            已有公众号/服务号授权
          </ElButton>
        </ElFormItem>
      </ElForm>
      <!-- 工具栏 -->
      <div class="app-table-toolbar">
        <div>
          <ElButton
            type="primary"
            v-access:code="'open:openPlatformApp:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
          <ViewModeToggle :mode="viewMode" @toggle="toggleViewMode" />
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <Form ref="formRef" @init-page="initPage" />

      <!-- 列表模式 -->
      <MpAppTable
        v-if="viewMode === 'list'"
        :data="state.tableData"
        :loading="loading"
        @edit="edit"
        @init-page="initPage"
      />

      <!-- 卡片模式 -->
      <MpAppCardList
        v-if="viewMode === 'card'"
        :data="state.tableData"
        :loading="loading"
        @edit="edit"
        @add="add"
        @init-page="initPage"
      />

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
