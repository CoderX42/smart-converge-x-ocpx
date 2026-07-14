<script lang="ts" setup>
import { Page } from '@boot/common-ui';

import { ElCard, ElCol, ElRow, ElStatistic } from 'element-plus';

const stats = [
  { title: '用户数', value: 0 },
  { title: '会员数', value: 0 },
  { title: '通用订单', value: 0 },
  { title: '开放应用', value: 0 },
];
</script>

<template>
  <Page title="系统数据看板" description="通用项目基座的数据总览占位，可接入用户、会员、订单、支付、开放平台指标。">
    <ElRow :gutter="16">
      <ElCol v-for="item in stats" :key="item.title" :xs="24" :sm="12" :lg="6">
        <ElCard class="mb-4">
          <ElStatistic :title="item.title" :value="item.value" />
        </ElCard>
      </ElCol>
    </ElRow>
    <ElCard>
      <div class="text-base font-medium">后续看板接入建议</div>
      <div class="mt-3 text-sm text-gray-500">
        可接入用户增长、会员活跃、订单转化、支付成功率、开放 API 调用量等通用指标。
      </div>
    </ElCard>
  </Page>
</template>
