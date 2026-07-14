<script lang="ts" setup name="rechargeOrderRecord">
import type { FormInstance } from 'element-plus';

import { computed, defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage } from '#/api/order/balance-order';
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
const queryRef = ref<FormInstance>();
const { pay_type, order_balance_status } = useDict(
  'pay_type',
  'order_balance_status',
);
const state = reactive({
  queryParams: {
    status: '',
    paymentStatus: '1',
    paymentType: '',
    orderNo: '',
    userId: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [],
});

const showSearch = ref(true);
const loading = ref(false);
const filteredPayTypes = computed(() =>
  pay_type?.value.filter((item: any) => item.value !== '3'),
);

const initPage = async () => {
  if (!props.userId) {
    return;
  }
  state.queryParams.userId = props.userId;

  loading.value = true;

  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
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
const resetQuery = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
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
      <ElFormItem label="订单号" prop="orderNo">
        <ElInput
          v-model="state.queryParams.orderNo"
          clearable
          placeholder="请输入订单号"
        />
      </ElFormItem>
      <ElFormItem label="支付类型" prop="paymentType">
        <ElSelect
          v-model="state.queryParams.paymentType"
          clearable
          placeholder="请选择支付类型"
          style="width: 200px"
        >
          <ElOption
            v-for="item in filteredPayTypes"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>

      <ElFormItem>
        <ElButton type="primary" @click="initPage" :icon="Search">
          搜索
        </ElButton>
        <ElButton @click="resetQuery(queryRef)" :icon="Refresh">
          重置
        </ElButton>
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
    <ElTable
      v-loading="loading"
      :data="state.tableData"
      border
      style="width: 100%"
    >
      <ElTableColumn prop="orderNo" label="订单单号" width="200" />
      <ElTableColumn
        prop="paymentType"
        label="支付类型"
        width="130"
        align="center"
      >
        <template #default="scope">
          <DictTag :options="pay_type" :value="scope.row.paymentType" />
        </template>
      </ElTableColumn>
      <ElTableColumn prop="status" label="订单状态" align="center" width="100">
        <template #default="scope">
          <DictTag :options="order_balance_status" :value="scope.row.status" />
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="paymentPrice"
        label="支付金额（元）"
        width="140"
        align="center"
      />
      <ElTableColumn
        prop="bonusPrice"
        label="赠送金额（元）"
        width="140"
        align="center"
      />
      <ElTableColumn prop="createTime" label="创建时间" min-width="180" />
      <ElTableColumn prop="paymentTime" label="支付时间" min-width="180" />
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
<style lang="scss" scoped>
.user-info {
  display: flex;
  align-items: center;

  .name {
    padding-left: 10px;
  }
}
</style>
