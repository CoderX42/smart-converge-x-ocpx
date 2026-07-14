<script setup lang="ts">
import type { UploadProps } from 'element-plus';

import { computed, ref } from 'vue';

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
  type: string; // /媒体材料类型。目前支持两种：图片("image")和视频("video")，示例值："image"
  certificateType?: null | number; // 证件类型(参考：获取证件类型)，如果上传的是证件媒体材料，则必填，示例值：2
  icpOrderField: string; // 媒体材料所属的备案字段名(参考：申请小程序备案接口)，如要用于多个备案字段，则填写其中一个字段名即可。例如：要上传身份证头像面照片作为备案主体负责人和小程序负责人的证件照正面，就填写 "icp_subject.principal_info.certificate_photo_front"
}

// 定义 emits
interface Emits {
  (e: 'update:modelValue', value: string | string[]): void;
  (e: 'success', response: any): void;
}
const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  appId: '',
  limit: 1,
  disabled: false,
  type: 'image',
  certificateType: null,
  icpOrderField: '',
});
const emit = defineEmits<Emits>();
const accessStore = useAccessStore();
const headers = ref({
  satoken: accessStore.accessToken,
});
const isOpenBoot = import.meta.env.VITE_OPEN_BOOT === 'true';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
console.log('certificateType', props.certificateType);

const action = computed(() => {
  const baseUrl = `${apiURL}${isOpenBoot ? '/boot' : '/open-platform'}/open-mini-icp/upload/${props.appId}`;
  const params = `type=${props.type}&icpOrderField=${props.icpOrderField}&certificateType=${props.certificateType || ''}`;
  return `${baseUrl}?${params}`;
});
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
        上传{{ props.type === 'image' ? '图片' : '视频' }}
      </ElButton>
    </ElUpload>
  </div>
</template>
