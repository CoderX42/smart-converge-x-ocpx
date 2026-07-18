<script lang="ts" setup>
import { Page } from '@boot/common-ui';

import { ElAlert, ElCard } from 'element-plus';
</script>

<template>
  <Page
    title="通用订单详情"
    description="展示订单主表、明细快照、支付和退款状态"
  >
    <ElCard>
      <ElAlert
        show-icon
        title="物流和垂直业务明细已移除。这里保留为通用订单详情入口，后续可接入 order_info / order_item 数据展示。"
        type="info"
      />
    </ElCard>
  </Page>
</template>
