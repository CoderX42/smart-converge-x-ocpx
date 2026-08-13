<script lang="ts" setup>
import type { PropType } from 'vue';

import { computed } from 'vue';

import { Download, Grid, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElPopover,
  ElTooltip,
} from 'element-plus';

export type ToolbarColumn = { key: string; label: string };

const props = defineProps({
  columns: {
    default: () => [],
    type: Array as PropType<ToolbarColumn[]>,
  },
  exportBtn: { default: false, type: Boolean },
  loading: { default: false, type: Boolean },
  refreshBtn: { default: false, type: Boolean },
  searchBtn: { default: false, type: Boolean },
  visibleColumns: {
    default: () => [],
    type: Array as PropType<string[]>,
  },
});

const emit = defineEmits<{
  export: [];
  refresh: [];
  search: [];
  'update:visibleColumns': [value: string[]];
}>();

const selectedColumns = computed({
  get: () => props.visibleColumns,
  set: (value: string[]) => emit('update:visibleColumns', value),
});
</script>

<template>
  <div class="right-toolbar" role="toolbar" aria-label="表格工具栏">
    <slot></slot>
    <ElTooltip v-if="props.exportBtn" content="导出" placement="top">
      <ElButton
        aria-label="导出"
        circle
        :disabled="loading"
        :icon="Download"
        @click="emit('export')"
      />
    </ElTooltip>
    <ElTooltip
      v-if="props.searchBtn"
      content="显示或隐藏查询条件"
      placement="top"
    >
      <ElButton
        aria-label="显示或隐藏查询条件"
        circle
        :disabled="loading"
        :icon="Search"
        @click="emit('search')"
      />
    </ElTooltip>
    <ElTooltip v-if="props.refreshBtn" content="刷新" placement="top">
      <ElButton
        aria-label="刷新表格"
        circle
        :disabled="loading"
        :icon="Refresh"
        @click="emit('refresh')"
      />
    </ElTooltip>
    <ElPopover
      v-if="columns.length > 0"
      placement="bottom-end"
      :width="220"
      trigger="click"
    >
      <template #reference>
        <ElButton
          aria-label="选择表格列"
          circle
          :disabled="loading"
          :icon="Grid"
        />
      </template>
      <div class="column-title">选择显示列</div>
      <ElCheckboxGroup v-model="selectedColumns" class="column-options">
        <ElCheckbox
          v-for="column in columns"
          :key="column.key"
          :value="column.key"
        >
          {{ column.label }}
        </ElCheckbox>
      </ElCheckboxGroup>
    </ElPopover>
  </div>
</template>

<style lang="scss" scoped>
.right-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.column-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.column-options {
  display: grid;
  grid-template-columns: 1fr;
  max-height: 320px;
  overflow-y: auto;
}
</style>
