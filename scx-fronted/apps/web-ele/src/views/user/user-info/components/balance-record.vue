<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage } from '#/api/user/balance-record';
import { useDict } from '#/utils/dict';

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
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

// 字典
const { balance_record_type } = useDict('balance_record_type');
const state = reactive({
  queryParams: {
    type: '',
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
const queryRef = ref();
const showSearch = ref(true);
const loading = ref(false);

const initPage = async () => {
  if (!props.userId) {
    return;
  }
  state.queryParams.userId = props.userId;

  loading.value = true;
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
      <ElFormItem label="交易类型" prop="type">
        <ElSelect
          v-model="state.queryParams.type"
          style="width: 200px"
          placeholder="请选择交易类型"
          clearable
        >
          <ElOption
            v-for="item in balance_record_type"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
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
      <ElTableColumn prop="beforeBalance" label="交易前账户余额" />
      <ElTableColumn prop="amount" label="交易金额">
        <template #default="scope">
          <span
            :style="{ color: scope.row.amount > 0 ? '#67C23A' : '#F56C6C' }"
          >
            {{
              scope.row.amount > 0 ? `+${scope.row.amount}` : scope.row.amount
            }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="afterBalance" label="交易后账户余额" />
      <ElTableColumn prop="type" label="交易类型">
        <template #default="scope">
          <DictTag :options="balance_record_type" :value="scope.row.type" />
        </template>
      </ElTableColumn>
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
