<script lang="ts" setup>
defineProps<{ modelValue?: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
</script>

<template>
  <ElSelect
    :model-value="modelValue"
    clearable
    disabled
    placeholder="通用 基座已移除业务范围维度"
    @update:model-value="(value) => emit('update:modelValue', value as string)"
  />
</template>
