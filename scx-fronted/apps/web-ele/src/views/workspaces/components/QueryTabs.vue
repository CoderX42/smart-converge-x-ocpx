<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

type Tab = {
  disabled?: boolean;
  label: string;
  name: string;
};

const props = defineProps<{
  defaultTab: string;
  tabs: Tab[];
}>();

const route = useRoute();
const router = useRouter();
const active = computed({
  get() {
    const requested = Array.isArray(route.query.tab)
      ? route.query.tab[0]
      : route.query.tab;
    return props.tabs.some((tab) => tab.name === requested)
      ? (requested as string)
      : props.defaultTab;
  },
  set(value: string) {
    void router.replace({
      hash: route.hash,
      query: { ...route.query, tab: value },
    });
  },
});
</script>

<template>
  <el-tabs v-model="active" class="workspace-tabs">
    <el-tab-pane
      v-for="tab in tabs"
      :key="tab.name"
      :disabled="tab.disabled"
      :label="tab.label"
      :name="tab.name"
    />
  </el-tabs>
  <slot :active="active"></slot>
</template>

<style scoped>
.workspace-tabs {
  padding: 0 20px;
  margin: 0 0 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
}

.workspace-tabs :deep(.el-tabs__header) {
  margin: 0;
}
</style>
