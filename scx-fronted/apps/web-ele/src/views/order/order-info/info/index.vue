<script lang="ts" setup>
import { Page } from '@boot/common-ui';

import { ElEmpty } from 'element-plus';
</script>

<template>
  <Page
    title="通用订单详情"
    description="展示订单主表、明细快照、支付和退款状态"
  >
    <ElEmpty description="暂无订单详情" />
  </Page>
</template>
