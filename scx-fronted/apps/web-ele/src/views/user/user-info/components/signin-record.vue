<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage } from '#/api/user/signin-record';

const props = defineProps({
  userId: {
    type: String,
    default: '',
  },
});
const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  queryParams: {
    day: null,
    userId: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const queryRef = ref();
const initPage = async () => {
  loading.value = true;
  if (!props.userId) {
    return;
  }
  state.queryParams.userId = props.userId;

  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response) => {
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
initPage();
</script>
<template>
  <div>
    <!-- 搜索 -->
    <ElForm
      :model="state.queryParams"
      ref="queryRef"
      :inline="true"
      v-show="showSearch"
    >
      <ElFormItem>
        <ElButton type="primary" @click="initPage"> 搜索 </ElButton>
        <ElButton @click="resetQuery"> 重置 </ElButton>
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
      <ElTableColumn prop="user" label="用户信息" width="140">
        <template #default="scope">
          {{ scope.row.nickname }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="pointNum" label="奖励积分" width="120" />
      <ElTableColumn prop="growthNum" label="奖励成长值" width="120" />
      <ElTableColumn prop="streakDays" label="连续签到天数" width="120">
        <template #default="scope">
          <span>{{ scope.row.streakDays }}天</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="signinDate" label="签到时间" />
      <ElTableColumn prop="createTime" label="创建时间" />
    </ElTable>
    <!-- 分页 -->
    <Pagination
      :total="state.page.total"
      v-model:current="state.page.currentPage"
      v-model:size="state.page.pageSize"
      @change="initPage"
    />
  </div>
</template>
