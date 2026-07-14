<script lang="ts" setup>
import { Page } from '@boot/common-ui';
import { ElCard, ElCol, ElRow, ElStatistic } from 'element-plus';

const stats = [
  { title: '新增会员', value: 0 },
  { title: '活跃会员', value: 0 },
  { title: '积分发放', value: 0 },
  { title: '余额流水', value: 0 },
];
</script>

<template>
  <Page title="会员分析" description="通用 基座会员中心指标占位">
    <ElRow :gutter="16">
      <ElCol v-for="item in stats" :key="item.title" :xs="24" :sm="12" :lg="6">
        <ElCard class="mb-4">
          <ElStatistic :title="item.title" :value="item.value" />
        </ElCard>
      </ElCol>
    </ElRow>
  </Page>
</template>
