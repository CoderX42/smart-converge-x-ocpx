<script lang="ts" setup>
import { ref, watch } from 'vue';

import { ElButton, ElDialog, ElMessage } from 'element-plus';

import { getQrCode } from '#/api/open/code-audit';

interface Props {
  visible: boolean;
  auditId?: string;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
}>();

const qrCodeImage = ref('');
const loading = ref(false);
// 监听弹窗显示状态
watch(
  () => props.visible,
  async (newVal) => {
    if (newVal) {
      handleVisibleChange(newVal);
    }
  },
  { immediate: true },
);
// 监听 visible 变化，当弹窗打开时获取二维码
const handleVisibleChange = async (visible: boolean) => {
  emit('update:visible', visible);

  if (visible && props.auditId) {
    await fetchQrCode();
  } else {
    // 弹窗关闭时清空图片
    qrCodeImage.value = '';
  }
};

// 获取二维码
const fetchQrCode = async () => {
  if (!props.auditId) {
    ElMessage.error('审核ID不能为空');
    return;
  }

  loading.value = true;
  try {
    const response = await getQrCode(props.auditId);
    qrCodeImage.value = `data:image/png;base64,${response}`;
  } catch (error) {
    ElMessage.error('获取二维码失败');
    console.error('获取二维码失败:', error);
  } finally {
    loading.value = false;
  }
};

// 关闭弹窗
const handleClose = () => {
  emit('update:visible', false);
};
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="体验二维码"
    width="400px"
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
  >
    <div class="qr-code-container" style="text-align: center">
      <div v-if="loading" class="loading-container">
        <p>二维码加载中...</p>
      </div>
      <img
        v-else-if="qrCodeImage"
        :src="qrCodeImage"
        alt="体验二维码"
        style="max-width: 100%; height: auto"
      />
      <div v-else class="no-image">
        <p>暂无二维码</p>
      </div>
    </div>
    <template #footer>
      <ElButton @click="handleClose">关闭</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.qr-code-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.loading-container,
.no-image {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #909399;
}

.loading-container p,
.no-image p {
  margin: 0;
  font-size: 14px;
}
</style>
