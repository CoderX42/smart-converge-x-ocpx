<script lang="ts" setup>
import type { OcpxBusinessCodeResource } from '#/api/ocpx';

import { ref, watch } from 'vue';

import { ElMessageBox } from 'element-plus';

import { generateOcpxBusinessCode } from '#/api/ocpx';

const props = defineProps<{
  disabled?: boolean;
  generationKey?: string;
  maxlength?: number;
  modelValue: string;
  resourceType: OcpxBusinessCodeResource;
}>();

const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const autoMode = ref(true);
const generating = ref(false);
const error = ref('');
const lastAutoCode = ref('');

async function generate() {
  if (props.disabled) return;
  generating.value = true;
  error.value = '';
  try {
    const result = await generateOcpxBusinessCode(props.resourceType);
    lastAutoCode.value = result.code;
    emit('update:modelValue', result.code);
  } catch {
    error.value = '自动编码生成失败，请重试或改为手动输入。';
  } finally {
    generating.value = false;
  }
}

function switchToManual() {
  autoMode.value = false;
  error.value = '';
}

async function switchToAuto() {
  const hasManualChange =
    Boolean(props.modelValue) && props.modelValue !== lastAutoCode.value;
  if (hasManualChange) {
    try {
      await ElMessageBox.confirm(
        '切回自动模式会生成新编码并覆盖当前输入，是否继续？',
        '覆盖当前编码',
        { confirmButtonText: '生成新编码', type: 'warning' },
      );
    } catch {
      return;
    }
  }
  autoMode.value = true;
  await generate();
}

watch(
  () => props.generationKey,
  (key) => {
    if (!key || props.disabled) return;
    autoMode.value = true;
    lastAutoCode.value = '';
    void generate();
  },
  { immediate: true },
);
</script>

<template>
  <div class="auto-code-field">
    <el-input
      :disabled="disabled"
      :loading="generating"
      :maxlength="maxlength"
      :model-value="modelValue"
      :readonly="autoMode && !disabled"
      @update:model-value="emit('update:modelValue', String($event))"
    >
      <template v-if="!disabled" #append>
        <div class="auto-code-actions">
          <el-button
            v-if="autoMode"
            aria-label="重新生成自动编码"
            :disabled="generating"
            @click="generate"
          >
            重新生成
          </el-button>
          <el-button
            v-if="autoMode"
            aria-label="改为手动输入编码"
            @click="switchToManual"
          >
            改为手动
          </el-button>
          <el-button
            v-else
            aria-label="改为自动生成编码"
            :disabled="generating"
            @click="switchToAuto"
          >
            改为自动
          </el-button>
        </div>
      </template>
    </el-input>
    <div v-if="error" class="auto-code-error" role="alert">
      <span>{{ error }}</span>
      <el-button link type="primary" @click="generate">重试</el-button>
      <el-button link @click="switchToManual">手动输入</el-button>
    </div>
  </div>
</template>

<style scoped>
.auto-code-field {
  width: 100%;
}

.auto-code-actions {
  display: flex;
  gap: 4px;
}

.auto-code-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.auto-code-error {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-color-danger);
}
</style>
