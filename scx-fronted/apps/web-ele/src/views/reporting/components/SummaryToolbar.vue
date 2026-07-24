<script lang="ts" setup>
import type { Media } from '#/api/ad';

defineProps<{
  dates: string[];
  media: Media[];
  mediaId: string;
}>();

const emit = defineEmits<{
  reset: [];
  'update:dates': [string[]];
  'update:mediaId': [string];
}>();
</script>

<template>
  <div class="mvp-scope-toolbar">
    <div class="mvp-control space-y-2">
      <el-date-picker
        :model-value="dates"
        class="!w-full"
        end-placeholder="结束日期"
        start-placeholder="开始日期"
        type="daterange"
        value-format="YYYY-MM-DD"
        @update:model-value="
          (value: string[] | undefined) => emit('update:dates', value || [])
        "
      />
      <el-button plain @click="emit('reset')">清空筛选</el-button>
    </div>
  </div>
</template>
