<script lang="ts" setup>
import { CopyDocument } from '@element-plus/icons-vue';

import { copyLongText } from './table';

defineProps<{ value?: null | string }>();
</script>

<template>
  <el-tooltip :content="value || '-'" placement="top" :show-after="300">
    <button
      v-if="value"
      :aria-label="`复制：${value}`"
      class="long-text"
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

.long-text:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
