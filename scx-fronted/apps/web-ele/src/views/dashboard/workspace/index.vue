<script lang="ts" setup>
import type { WorkbenchQuickNavItem } from '@boot/common-ui';

import { useRouter } from 'vue-router';

import { Page, WorkbenchQuickNav } from '@boot/common-ui';

const router = useRouter();
const quickNavItems: WorkbenchQuickNavItem[] = [
  {
    color: '#1fdaca',
    icon: 'ion:home-outline',
    title: 'SCX 看板',
    url: '/analytics',
  },
];

function navTo(nav: WorkbenchQuickNavItem) {
  if (nav.url) {
    router.push(nav.url);
  }
}
</script>

<template>
  <Page title="工作台" description="Smart Converge X 快捷入口">
    <WorkbenchQuickNav title="常用入口" :items="quickNavItems" @click="navTo" />
  </Page>
</template>
