<script lang="ts" setup>
import { reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import { ElButton, ElCard, ElInput, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { getPage } from '#/api/order/order-refund';

const loading = ref(false);
const rows = ref<any[]>([]);
const query = reactive({ refundNo: '', orderNo: '', refundStatus: '' });

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
  <Page title="通用退款" description="业务无关退款申请与支付退款单关联">
    <ElCard>
      <div class="mb-4 flex gap-3">
        <ElInput v-model="query.refundNo" clearable placeholder="退款单号" style="width: 220px" />
        <ElInput v-model="query.orderNo" clearable placeholder="订单号" style="width: 220px" />
        <ElInput v-model="query.refundStatus" clearable placeholder="退款状态" style="width: 160px" />
        <ElButton type="primary" @click="loadData">查询</ElButton>
      </div>
      <ElTable v-loading="loading" :data="rows" row-key="id">
        <ElTableColumn prop="refundNo" label="退款单号" min-width="180" />
        <ElTableColumn prop="orderNo" label="订单号" min-width="180" />
        <ElTableColumn prop="refundAmount" label="退款金额" width="120" />
        <ElTableColumn prop="refundReason" label="退款原因" min-width="180" />
        <ElTableColumn prop="refundStatus" label="退款状态" width="130">
          <template #default="{ row }"><ElTag>{{ row.refundStatus }}</ElTag></template>
        </ElTableColumn>
        <ElTableColumn prop="applyTime" label="申请时间" min-width="170" />
      </ElTable>
    </ElCard>
  </Page>
</template>
