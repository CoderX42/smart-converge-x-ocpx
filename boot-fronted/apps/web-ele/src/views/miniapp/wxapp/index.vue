<script lang="ts" setup name="wxApp">
import { defineAsyncComponent, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Plus, Refresh, Search } from '@element-plus/icons-vue';
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus';

import { openAuth } from '#/api/open/open-auth';
import { getPage } from '#/api/open/open-platform-app';

import ViewModeToggle from './components/ViewModeToggle.vue';
// 导入子组件
import WxappCardList from './components/WxappCardList.vue';
import WxappTable from './components/WxappTable.vue';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const Form = defineAsyncComponent(() => import('./form.vue'));
const router = useRouter();
const state = reactive({
  queryParams: { appId: '', platformType: 'WX_MA' },
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
const openRegister = () => {
  router.push('/miniapp/register');
};
initPage();
</script>
<template>
  <div class="app-layout-container">
    <div
      class="app-layout-container-auto app-layout-container-view"
      style="height: 100vh; overflow-y: auto"
    >
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
        </ElFormItem>
      </ElForm>
      <Form ref="formRef" @init-page="initPage" />
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
          <ElButton type="primary" @click="openAuthorize">
            已有小程序授权
          </ElButton>
          <ElButton type="primary" @click="openRegister">
            快速注册小程序
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

      <!-- 列表模式 -->
      <WxappTable
        v-if="viewMode === 'list'"
        :data="state.tableData"
        :loading="loading"
        @edit="edit"
        @init-page="initPage"
      />

      <!-- 卡片模式 -->
      <WxappCardList
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
