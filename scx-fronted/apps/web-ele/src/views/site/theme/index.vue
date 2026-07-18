<script setup lang="ts">
import { Page } from '@boot/common-ui';

import { ElAlert, ElCard } from 'element-plus';
</script>

<template>
  <Page
    title="官网主题"
    description="配置官网色彩、字体、圆角、组件样式等设计 Token"
  >
    <ElCard>
      <ElAlert
        show-icon
        title="主题配置表已规划为 site_theme，可在下一阶段接入主题编辑器。"
        type="info"
      />
    </ElCard>
  </Page>
</template>
