<script lang="ts" setup>
import QueryTabs from '../../workspaces/components/QueryTabs.vue';
import InventoryWorkspace from '../index.vue';

const tabs = [
  { label: '媒体投放账户', name: 'accounts' },
  { label: '广告位', name: 'placements' },
];
</script>

<template>
  <QueryTabs default-tab="accounts" :tabs="tabs">
    <InventoryWorkspace />
  </QueryTabs>
</template>
