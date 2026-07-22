<script lang="ts" setup>
import type { Media } from '#/api/ad';

defineProps<{
  media: Media[];
  mediaId: string;
  dates: string[];
}>();

const emit = defineEmits<{
  'update:mediaId': [string];
  'update:dates': [string[]];
  reset: [];
}>();
</script>

<template>
  <div class="mvp-scope-toolbar">
    <div class="mvp-control space-y-2">
      <div class="mvp-scope-label text-xs">媒体安全范围</div>
      <el-select
        :model-value="mediaId"
        class="w-full"
        @update:model-value="(value: string) => emit('update:mediaId', value)"
      >
        <el-option
          v-for="item in media"
          :key="item.id"
          :label="item.name"
          :value="item.id"
        />
      </el-select>
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
