<script lang="ts" setup>
import QueryTabs from '../../workspaces/components/QueryTabs.vue';
import CommercialWorkspace from '../index.vue';

const tabs = [
  { label: '资质审核', name: 'qualifications' },
  { label: '协议模板', name: 'templates' },
  { label: '协议实例', name: 'instances' },
  { label: '数据授权', name: 'authorizations' },
  { label: '发布资格', name: 'eligibility' },
];
</script>

<template>
  <QueryTabs default-tab="qualifications" :tabs="tabs">
    <CommercialWorkspace />
  </QueryTabs>
</template>
