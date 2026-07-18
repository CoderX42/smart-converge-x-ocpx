<script lang="ts" setup>
import { Page } from '@boot/common-ui';

import { ElAlert, ElCard } from 'element-plus';
</script>

<template>
  <Page title="广告位配置" description="通用官网/应用广告位配置">
    <ElCard>
      <ElAlert
        show-icon
        title="业务跳转选择器已移除，后续可接入官网页面、文章、外链等通用链接选择器。"
        type="info"
      />
    </ElCard>
  </Page>
</template>
