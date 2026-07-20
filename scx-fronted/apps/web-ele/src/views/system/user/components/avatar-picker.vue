<script lang="ts" setup>
import { computed, defineAsyncComponent } from 'vue';

import { UserFilled } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

const props = defineProps<{
  modelValue?: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);

const defaultAvatars = [
  { label: '澄空蓝', value: '/static/avatars/avatar-sky.svg' },
  { label: '松石绿', value: '/static/avatars/avatar-mint.svg' },
  { label: '珊瑚橙', value: '/static/avatars/avatar-coral.svg' },
  { label: '暮光紫', value: '/static/avatars/avatar-violet.svg' },
  { label: '暖阳黄', value: '/static/avatars/avatar-sun.svg' },
] as const;

const avatar = computed({
  get: () => props.modelValue || '',
  set: (value: string) => emit('update:modelValue', value),
});

const selectDefaultAvatar = (value: string) => {
  avatar.value = value;
};
</script>

<template>
  <div class="avatar-picker">
    <div class="current-avatar" aria-live="polite">
      <ElImage v-if="avatar" :src="avatar" fit="cover" />
      <ElIcon v-else aria-label="尚未选择头像"><UserFilled /></ElIcon>
    </div>

    <div class="avatar-options">
      <div class="option-heading">
        <span>选择默认头像</span>
        <span class="option-hint">也可以使用素材库中的图片</span>
      </div>
      <div class="default-avatar-list" role="radiogroup" aria-label="默认头像">
        <button
          v-for="item in defaultAvatars"
          :key="item.value"
          type="button"
          class="default-avatar"
          :class="{ 'is-selected': avatar === item.value }"
          :aria-checked="avatar === item.value"
          :aria-label="item.label"
          role="radio"
          @click="selectDefaultAvatar(item.value)"
        >
          <img :src="item.value" :alt="item.label" />
        </button>
      </div>
      <SelectMaterial
        v-model="avatar"
        :can-choose-images-num="1"
        :show-preview="false"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.avatar-picker {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  width: 100%;
}

.current-avatar {
  display: grid;
  flex: 0 0 96px;
  place-items: center;
  width: 96px;
  height: 96px;
  overflow: hidden;
  font-size: 34px;
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: 50%;

  :deep(.el-image) {
    width: 100%;
    height: 100%;
  }
}

.avatar-options {
  min-width: 0;
}

.option-heading {
  display: flex;
  gap: 10px;
  align-items: baseline;
  margin-bottom: 10px;
  line-height: 20px;
  color: var(--el-text-color-primary);
}

.option-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.default-avatar-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.default-avatar {
  box-sizing: border-box;
  width: 52px;
  height: 52px;
  padding: 2px;
  overflow: hidden;
  cursor: pointer;
  background: transparent;
  border: 2px solid transparent;
  border-radius: 50%;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;

  img {
    display: block;
    width: 100%;
    height: 100%;
    border-radius: 50%;
  }

  &:hover,
  &:focus-visible {
    outline: none;
    border-color: var(--el-color-primary-light-5);
    transform: translateY(-1px);
  }

  &.is-selected {
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
  }
}

@media (max-width: 720px) {
  .avatar-picker {
    flex-direction: column;
  }

  .option-heading {
    flex-direction: column;
    gap: 0;
  }
}
</style>
