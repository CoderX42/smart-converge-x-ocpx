<script setup lang="ts" name="dict-tag">
import { computed } from 'vue';

import { ElTag } from 'element-plus';

const props = defineProps({
  options: {
    type: Array as any,
    default: null,
  },
  value: {
    type: [String, Array, Number],
    default: '',
  },
});

const values = computed(() => {
  if (props.value !== null && props.value !== 'undefined') {
    const val = Array.isArray(props.value) ? props.value : [props.value];
    // 将值转换为字符串进行比较，以处理数字和字符串的类型不匹配问题
    return val.map(String);
  } else {
    return [];
  }
});
</script>

<template>
  <div>
    <template v-for="(item, index) in options">
      <template v-if="values.includes(String(item.value || item))">
        <span
          :key="item.value"
          v-if="item.showClass === '' || item.showClass === 'default'"
          >{{ item.label || item }}
        </span>
        <ElTag
          v-else
          :key="index * 2"
          :index="index"
          :type="item.showClass"
          :disable-transitions="true"
        >
          {{ item.label || item }}
        </ElTag>
      </template>
    </template>
  </div>
</template>

<style scoped></style>
