<script lang="ts" setup>
import Partners from '../../delivery/partners/index.vue';
import Products from '../../delivery/products/index.vue';
import QueryTabs from '../../workspaces/components/QueryTabs.vue';

const tabs = [
  { label: '合作方', name: 'partners' },
  { label: '产品', name: 'products' },
];
</script>

<template>
  <QueryTabs v-slot="{ active }" default-tab="partners" :tabs="tabs">
    <Partners v-if="active === 'partners'" />
    <Products v-else />
  </QueryTabs>
</template>
