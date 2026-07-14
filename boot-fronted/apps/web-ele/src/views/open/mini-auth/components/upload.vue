<script setup lang="ts">
import type { UploadProps } from 'element-plus';

import { ref } from 'vue';

import { useAppConfig } from '@boot/hooks';
import { useAccessStore } from '@boot/stores';

import { Upload } from '@element-plus/icons-vue';
import { ElButton, ElMessage, ElUpload } from 'element-plus';

// 定义 props
interface Props {
  modelValue?: string | string[];
  appId: string;
  limit: number;
  disabled?: boolean;
}

// 定义 emits
interface Emits {
  (e: 'update:modelValue', value: string | string[]): void;
  (e: 'success', response: any): void;
}
const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  disabled: false,
});
const emit = defineEmits<Emits>();
const accessStore = useAccessStore();
const headers = ref({
  satoken: accessStore.accessToken,
});
const isOpenBoot = import.meta.env.VITE_OPEN_BOOT === 'true';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const action = ref(
  `${apiURL}${isOpenBoot ? '/boot' : '/open-platform'}/open-mini-auth/upload/${props.appId}`,
);
/**
 * 上传成功事件
 */
const handleSuccess: UploadProps['onSuccess'] = (response) => {
  ElMessage.success('上传成功');

  // 处理 v-model 更新
  if (props.limit > 1) {
    // 如果是数组，添加新的文件URL
    const newValue = [...props.modelValue, response.data];
    emit('update:modelValue', newValue);
  } else {
    // 如果是单个值，直接更新
    emit('update:modelValue', response.data);
  }

  // 触发成功事件
  emit('success', response.data);
};
/**
 * 上传失败事件
 */
const handleError: UploadProps['onError'] = (error) => {
  ElMessage.error(`${error}`);
};
/**
 * 上传素材前事件
 */
const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const allowedTypes = [
    'image/jpeg',
    'image/png',
    'image/gif',
    'image/webp',
    'image/svg+xml',
  ];

  if (!allowedTypes.includes(rawFile.type)) {
    ElMessage.error('图片格式必须是 JPG、PNG、GIF、WEBP 或 SVG！');
    return false;
  }

  const isLt5M = rawFile.size / 1024 / 1024 <= 2;
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 2MB！');
    return false;
  }

  return true;
};
</script>

<template>
  <div>
    <ElUpload
      :action="action"
      :headers="headers"
      :show-file-list="false"
      :before-upload="beforeAvatarUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      :disabled="props.disabled"
    >
      <ElButton type="primary" :icon="Upload" :disabled="props.disabled">
        上传图片
      </ElButton>
    </ElUpload>
  </div>
</template>
