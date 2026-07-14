<script lang="ts" setup>
import { reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import { ElButton, ElCard, ElInput, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { getPage } from '#/api/order/order-info';

const loading = ref(false);
const rows = ref<any[]>([]);
const query = reactive({ orderNo: '', businessType: '', userId: '' });

async function loadData() {
  loading.value = true;
  try {
    const result: any = await getPage(query);
    rows.value = result?.records || [];
  } finally {
    loading.value = false;
  }
}

loadData();
</script>

<template>
  <Page title="通用订单" description="业务无关订单中心，按业务类型和业务单号关联外部模块">
    <ElCard>
      <div class="mb-4 flex gap-3">
        <ElInput v-model="query.orderNo" clearable placeholder="订单号" style="width: 220px" />
        <ElInput v-model="query.businessType" clearable placeholder="业务类型" style="width: 180px" />
        <ElInput v-model="query.userId" clearable placeholder="用户ID" style="width: 180px" />
        <ElButton type="primary" @click="loadData">查询</ElButton>
      </div>
      <ElTable v-loading="loading" :data="rows" row-key="id">
        <ElTableColumn prop="orderNo" label="订单号" min-width="180" />
        <ElTableColumn prop="businessType" label="业务类型" min-width="140" />
        <ElTableColumn prop="subject" label="订单标题" min-width="180" />
        <ElTableColumn prop="userId" label="用户ID" min-width="140" />
        <ElTableColumn prop="amountPayable" label="应付金额" width="120" />
        <ElTableColumn prop="orderStatus" label="订单状态" width="130">
          <template #default="{ row }"><ElTag>{{ row.orderStatus }}</ElTag></template>
        </ElTableColumn>
        <ElTableColumn prop="payStatus" label="支付状态" width="130" />
        <ElTableColumn prop="refundStatus" label="退款状态" width="130" />
        <ElTableColumn prop="createTime" label="创建时间" min-width="170" />
      </ElTable>
    </ElCard>
  </Page>
</template>
