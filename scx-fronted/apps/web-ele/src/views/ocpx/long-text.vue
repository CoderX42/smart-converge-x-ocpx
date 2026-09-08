<script lang="ts" setup>
import { CopyDocument } from '@element-plus/icons-vue';

import { copyLongText } from './table';

withDefaults(defineProps<{ value?: null | string; wrap?: boolean }>(), {
  wrap: false,
});
</script>

<template>
  <el-tooltip :content="value || '-'" placement="top" :show-after="300">
    <button
      v-if="value"
      :aria-label="`复制：${value}`"
      class="long-text"
      :class="[{ 'long-text--wrap': wrap }]"
      type="button"
      @click="copyLongText(value)"
    >
      <span>{{ value }}</span>
      <el-icon><CopyDocument /></el-icon>
    </button>
    <span v-else>-</span>
  </el-tooltip>
</template>

<style scoped>
.long-text {
  display: flex;
  gap: 6px;
  align-items: center;
  width: 100%;
  min-width: 0;
  padding: 0;
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.long-text span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.long-text--wrap {
  align-items: flex-start;
}

.long-text--wrap span {
  overflow: visible;
  text-overflow: clip;
  overflow-wrap: anywhere;
  white-space: normal;
}

.long-text--wrap .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
}

.long-text:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
