<script setup lang="ts" name="selectRegin">
import { computed, reactive, ref } from 'vue';

import { ElCascader } from 'element-plus';

import { getTree } from '#/api/system/region';

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => [],
  },
  disabled: {
    type: Boolean,
    default: false,
  },
});
const emit = defineEmits(['update:modelValue', 'change']);
const cascaderProps = ref({
  value: 'code',
  label: 'name',
  children: 'children',
});
const value = computed({
  get() {
    return props.modelValue;
  },
  set(value) {
    emit('update:modelValue', value);
  },
});
const handleChange = (val: any) => {
  const path = findNodePathByIds(state.reginTree, val);

  emit('change', path); // 返回对象数组
};
const state = reactive({
  reginTree: [] as any,
});
const getRegin = () => {
  getTree().then((response) => {
    state.reginTree = response;
  });
};
// 递归查找路径
function findNodePathByIds(tree: any[], codes: any[]): any[] {
  const path: any[] = [];

  function traverse(nodes: any[], level: number): boolean {
    for (const node of nodes) {
      if (node.code === codes[level]) {
        path.push({ name: node.name, code: node.code });
        if (level === codes.length - 1) {
          return true;
        }
        if (node.children && traverse(node.children, level + 1)) {
          return true;
        }
        path.pop(); // 回溯
      }
    }
    return false;
  }

  traverse(tree, 0);
  return path;
}

getRegin();
</script>
<template>
  <ElCascader
    v-model="value"
    :props="cascaderProps"
    :options="state.reginTree"
    @change="handleChange"
  />
</template>
