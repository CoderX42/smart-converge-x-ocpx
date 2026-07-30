<script lang="ts" setup>
import QueryTabs from '../../workspaces/components/QueryTabs.vue';
import CommercialWorkspace from '../index.vue';

const tabs = [
  { label: '组织', name: 'organizations' },
  { label: '广告主档案', name: 'advertisers' },
  { label: '代理关系', name: 'agencies' },
  { label: '数据范围', name: 'scopes' },
];
</script>

<template>
  <QueryTabs default-tab="organizations" :tabs="tabs">
    <CommercialWorkspace />
  </QueryTabs>
</template>
