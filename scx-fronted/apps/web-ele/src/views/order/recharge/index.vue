<script lang="ts" setup name="order">
import type { FormInstance } from 'element-plus';

import { computed, defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElAvatar,
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSegmented,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, getPage, refundObj } from '#/api/order/balance-order';
import { useDict } from '#/utils/dict';

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
    paymentStatus: '',
    paymentType: '',
    orderNo: '',
    paymentQueryTimes: [],
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [],
});
const activeTypeOptions = ref([
  {
    value: '',
    label: '全部',
  },
  {
    value: '1',
    label: '待付款',
  },
  {
    value: '2',
    label: '已完成',
  },
  {
    value: '3',
    label: '退款中',
  },
  {
    value: '4',
    label: '已退款',
  },
  {
    value: '11',
    label: '交易已关闭',
  },
]);
const shortcuts = [
  {
    text: '最近一周',
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - 7);
      return [start, end];
    },
  },
  {
    text: '最近一月',
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setMonth(start.getMonth() - 1);
      return [start, end];
    },
  },
  {
    text: '最近三个月',
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setMonth(start.getMonth() - 3);
      return [start, end];
    },
  },
];
const showSearch = ref(true);
const loading = ref(false);
const filteredPayTypes = computed(() =>
  pay_type?.value.filter((item: any) => item.value !== '3'),
);

const initPage = async () => {
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
  initPage();
};

/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该订单，是否继续?', '提示', {
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

/**
 * 订单状态切换
 */
const tabHandle = () => {
  initPage();
};
/**
 * 退款
 */
const refund = (id: string) => {
  ElMessageBox.confirm('此操作将退款该订单，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    refundObj({ id })
      .then(() => {
        ElMessage.success('退款成功');
        initPage();
      })
      .catch(() => {});
  });
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
        <ElFormItem label="支付时间" prop="paymentQueryTimes">
          <ElDatePicker
            style="width: 360px"
            v-model="state.queryParams.paymentQueryTimes"
            type="datetimerange"
            :shortcuts="shortcuts"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="-"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
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
        <ElSegmented
          v-model="state.queryParams.status"
          :options="activeTypeOptions"
          @change="tabHandle"
        />
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
        <ElTableColumn prop="userInfo" label="下单用户" width="230">
          <template #default="scope">
            <div class="user-info" v-if="scope.row.userInfo">
              <ElAvatar
                v-if="scope.row.userInfo && scope.row.userInfo.avatarUrl"
                :src="scope.row.userInfo.avatarUrl"
              />
              <div class="name">
                {{ scope.row.userInfo?.nickname }}
              </div>
            </div>
            <div v-else>用户不存在或已删除</div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="orderNo" label="订单单号" width="230" />
        <ElTableColumn
          prop="paymentType"
          label="支付类型"
          width="160"
          align="center"
        >
          <template #default="scope">
            <DictTag :options="pay_type" :value="scope.row.paymentType" />
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="status"
          label="订单状态"
          align="center"
          width="100"
        >
          <template #default="scope">
            <DictTag
              :options="order_balance_status"
              :value="scope.row.status"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="paymentPrice"
          label="支付金额（元）"
          width="160"
          align="center"
        />
        <ElTableColumn
          prop="bonusPrice"
          label="赠送金额（元）"
          width="160"
          align="center"
        />
        <ElTableColumn prop="createTime" label="创建时间" min-width="180" />
        <ElTableColumn prop="paymentTime" label="支付时间" min-width="180" />
        <ElTableColumn
          label="操作"
          min-width="120"
          align="center"
          fixed="right"
        >
          <template #default="scope">
            <ElButton
              link
              type="danger"
              v-access:code="'order:balance-order:del'"
              v-if="
                scope.row.status === '11' && scope.row.paymentStatus === '0'
              "
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
            <ElButton
              link
              type="warning"
              v-access:code="'order:balance-order:refunds'"
              v-if="scope.row.status === '2'"
              @click="refund(scope.row.id)"
            >
              退款
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
<style lang="scss" scoped>
.user-info {
  display: flex;
  align-items: center;

  .name {
    padding-left: 10px;
  }
}
</style>
