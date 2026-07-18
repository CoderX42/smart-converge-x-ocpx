<script setup lang="ts">
import { Page } from '@boot/common-ui';

import { ElAlert, ElCard } from 'element-plus';
</script>

<template>
  <Page title="官网导航" description="配置官网头部导航、页脚导航和移动端导航">
    <ElCard>
      <ElAlert
        show-icon
        title="导航配置表已规划为 site_navigation，可在下一阶段接入导航树编辑器。"
        type="info"
      />
    </ElCard>
  </Page>
</template>
