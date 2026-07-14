<script lang="ts" setup>
import { Grid as IconGrid, List as IconList } from '@element-plus/icons-vue';
import { ElButton, ElButtonGroup } from 'element-plus';

defineProps<{
  mode: 'card' | 'list';
}>();

const emit = defineEmits<{
  (e: 'toggle'): void;
}>();

const toggleView = () => {
  emit('toggle');
};
</script>

<template>
  <ElButtonGroup class="ml-4">
    <ElButton
      :type="mode === 'list' ? 'primary' : 'default'"
      :icon="IconList"
      @click="toggleView"
      title="列表视图"
    />
    <ElButton
      :type="mode === 'card' ? 'primary' : 'default'"
      :icon="IconGrid"
      @click="toggleView"
      title="卡片视图"
    />
  </ElButtonGroup>
</template>
