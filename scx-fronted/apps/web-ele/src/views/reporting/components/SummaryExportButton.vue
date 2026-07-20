<script lang="ts" setup>
import { computed, ref } from 'vue';

import { ElMessage } from 'element-plus';

import { createExportTask } from '#/api/ad';

const props = withDefaults(
  defineProps<{
    mediaId: string;
    dimension: string;
    startDate?: string;
    endDate?: string;
  }>(),
  {
    startDate: '',
    endDate: '',
  },
);

const creating = ref(false);
const canExport = computed(
  () => Boolean(props.mediaId && props.startDate && props.endDate),
);

async function createExport() {
  if (!canExport.value) {
    ElMessage.warning('请先选择媒体与日期范围');
    return;
  }
  creating.value = true;
  try {
    const task = await createExportTask({
      endDate: props.endDate,
      exportType: 'DAILY_CSV',
      mediaId: props.mediaId,
      startDate: props.startDate,
    });
    ElMessage.success(`导出任务已创建：${task.id}（${props.dimension}）`);
  } finally {
    creating.value = false;
  }
}
</script>

<template>
  <el-button
    v-access:code="'ad:export:create'"
    :loading="creating"
    :disabled="!canExport"
    type="primary"
    @click="createExport"
  >
    导出汇总
  </el-button>
</template>
