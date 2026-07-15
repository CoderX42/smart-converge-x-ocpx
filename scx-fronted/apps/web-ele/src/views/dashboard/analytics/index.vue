<script lang="ts" setup>
import { Page } from '@boot/common-ui';

import { ElCard, ElCol, ElRow, ElStatistic } from 'element-plus';

const stats = [
  { title: '广告主体', value: 0 },
  { title: '投放计划', value: 0 },
  { title: '归因事件', value: 0 },
  { title: '待对账金额', value: 0 },
];
</script>

<template>
  <Page title="SCX 工作台" description="广告平台基础看板；指标将在事件、归因与账本能力上线后逐步接入。">
    <ElRow :gutter="16">
      <ElCol v-for="item in stats" :key="item.title" :xs="24" :sm="12" :lg="6">
        <ElCard class="mb-4">
          <ElStatistic :title="item.title" :value="item.value" />
        </ElCard>
      </ElCol>
    </ElRow>
    <ElCard>
      <div class="text-base font-medium">当前实施边界</div>
      <div class="mt-3 text-sm text-gray-500">
        M1 已提供广告主体对象授权。计划、素材、事件、归因、账本与报表将在后续纵切片中接入。
      </div>
    </ElCard>
  </Page>
</template>
