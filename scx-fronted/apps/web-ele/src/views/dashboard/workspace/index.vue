<script lang="ts" setup>
import type { WorkbenchQuickNavItem } from '@boot/common-ui';

import { useRouter } from 'vue-router';

import { Page, WorkbenchQuickNav } from '@boot/common-ui';

const router = useRouter();
const quickNavItems: WorkbenchQuickNavItem[] = [
  { color: '#1fdaca', icon: 'ion:home-outline', title: '控制台', url: '/analytics' },
  { color: '#3fb27f', icon: 'ion:md-reorder', title: '订单中心', url: '/order/order-info' },
  { color: '#00d8ff', icon: 'ion:color-palette-outline', title: '官网装修', url: '/site/page-design' },
  { color: '#e18525', icon: 'ion:people-outline', title: '会员中心', url: '/user/user-info' },
];

function navTo(nav: WorkbenchQuickNavItem) {
  if (nav.url) {
    router.push(nav.url);
  }
}
</script>

<template>
  <Page title="工作台" description="通用 通用项目基座快捷入口">
    <WorkbenchQuickNav :items="quickNavItems" @click="navTo" />
  </Page>
</template>
