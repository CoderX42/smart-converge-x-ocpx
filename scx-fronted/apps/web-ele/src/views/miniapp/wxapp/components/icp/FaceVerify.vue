<script setup lang="ts">
import type { FaceVerifyProps } from './types';

import { computed, ref } from 'vue';

import { ElButton, ElCard, ElIcon, ElMessage } from 'element-plus';

import { startFaceVerify } from '#/api/open/mini-icp';

const props = defineProps<FaceVerifyProps>();

const emit = defineEmits<{
  'face-verify-success': [];
  'update:faceStatus': [status: number];
}>();

// 人脸核身状态
const faceVerifyLoading = ref(false);
const faceVerifyTaskId = ref('');

// 计算属性
const statusIcon = computed(() => {
  if (props.faceStatus === 3) {
    return 'SuccessFilled';
  }
  return 'InfoFilled';
});

const statusIconClass = computed(() => {
  if (props.faceStatus === 3) {
    return 'status-icon-success';
  }
  return 'status-icon-unauth';
});

const statusTitle = computed(() => {
  if (!props.faceStatus) {
    return '人脸核身未开始';
  }
  switch (props.faceStatus) {
    case 0: {
      return '人脸核身未开始';
    }
    case 1: {
      return '人脸核身等待中';
    }
    case 2: {
      return '人脸核身未通过';
    }
    case 3: {
      return '人脸核身已通过';
    }
    default: {
      return '人脸核身未开始';
    }
  }
});

const statusDesc = computed(() => {
  if (!props.faceStatus) {
    return '请先完成小程序管理员人脸核身，核身通过后才能进行备案申请';
  }
  switch (props.faceStatus) {
    case 0: {
      return '请先完成小程序管理员人脸核身，核身通过后才能进行备案申请';
    }
    case 1: {
      return '人脸核身进行中，请稍后再试';
    }
    case 2: {
      return '人脸核身未通过，请重新核身';
    }
    case 3: {
      return '人脸核身已通过，请填写备案信息';
    }
    default: {
      return '请先完成小程序管理员人脸核身，核身通过后才能进行备案申请';
    }
  }
});

// 发起人脸核身
const startFaceVerifyProcess = async () => {
  if (!props.appId) {
    ElMessage.error('小程序ID不能为空');
    return;
  }

  try {
    faceVerifyLoading.value = true;
    const response = await startFaceVerify(props.appId);

    faceVerifyTaskId.value = response.taskId;
    // 发起成功后，将状态设置为0，防止重复发起
    emit('update:faceStatus', 0);
    ElMessage.success('人脸核身已发起，请扫码完成核身');
  } catch (error) {
    console.error('发起人脸核身失败:', error);
    ElMessage.error('发起人脸核身失败，请重试');
  } finally {
    faceVerifyLoading.value = false;
  }
};

// 进入下一步
const goToNextStep = () => {
  emit('face-verify-success');
};
</script>

<template>
  <div class="face-verify-container">
    <ElCard class="status-card">
      <div class="status-header">
        <ElIcon class="status-icon" :class="statusIconClass">
          <component :is="statusIcon" />
        </ElIcon>
        <div class="status-info">
          <h3 class="status-title">{{ statusTitle }}</h3>
          <p class="status-desc">{{ statusDesc }}</p>
        </div>
      </div>
    </ElCard>

    <!-- 操作按钮 -->
    <div class="step-actions">
      <ElButton
        v-if="
          faceStatus === null || faceStatus === undefined || faceStatus === 2
        "
        type="primary"
        :loading="faceVerifyLoading"
        @click="startFaceVerifyProcess"
      >
        发起人脸核身
      </ElButton>
      <ElButton v-if="faceStatus === 3" type="primary" @click="goToNextStep">
        进入第二步：备案申请
      </ElButton>
    </div>
  </div>
</template>

<style scoped>
.face-verify-container {
  width: 100%;
}

.status-card {
  border-radius: 8px;
}

.status-header {
  display: flex;
  gap: 16px;
  align-items: center;
}

.status-icon {
  padding: 12px;
  font-size: 24px;
  border-radius: 50%;
}

.status-icon-unauth {
  color: #1890ff;
  background-color: #e6f7ff;
}

.status-icon-success {
  color: #52c41a;
  background-color: #f6ffed;
}

.status-info {
  flex: 1;
}

.status-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
  color: #262626;
}

.status-desc {
  margin: 0;
  font-size: 14px;
  color: #8c8c8c;
}

.step-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  padding: 24px;
  margin-top: 20px;
  background: white;
  border-radius: 8px;
}
</style>
