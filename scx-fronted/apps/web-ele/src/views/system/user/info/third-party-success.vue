<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useUserStore } from '@boot/stores';

import { Check, SuccessFilled } from '@element-plus/icons-vue';
import { ElButton, ElCard, ElCol, ElIcon, ElRow, ElText } from 'element-plus';

import { bindOpenId } from '#/api/system/user';

// 常量定义
const CONSTANTS = {
  COUNTDOWN_SECONDS: 3,
  ANIMATION_DELAYS: {
    SHOW_ANIMATION: 100,
    SHOW_SUCCESS: 300,
    SHOW_MESSAGE: 600,
    START_COUNTDOWN: 1000,
  },
  ACCOUNT_TYPES: {
    WECHAT: '1',
  },
  OPERATION_TYPES: {
    BIND: 'bind',
    LOGIN: 'login',
  },
  ROUTES: {
    USER_MANAGEMENT: '/#/system/user',
  },
} as const;

// 类型定义
interface AuthState {
  loading: boolean;
  success: boolean;
  error: boolean;
  message: string;
}

interface RouteParams {
  code: string;
  type: string;
}

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

// 响应式状态
const state = reactive<AuthState>({
  loading: false,
  success: false,
  error: false,
  message: '',
});

// 动画状态
const animationState = reactive({
  showAnimation: false,
  showSuccess: false,
  showMessage: false,
});

// 倒计时相关
const countdown = ref(CONSTANTS.COUNTDOWN_SECONDS);
const countdownTimer = ref<null | number>(null);

// 计算属性
const countdownProgress = computed(
  () => (countdown.value / CONSTANTS.COUNTDOWN_SECONDS) * 100,
);
const isWindowOpener = computed(() => window.opener && !window.opener.closed);

// 工具函数
const getRouteParams = (): null | RouteParams => {
  const code = route.query.code as string;
  const type = route.query.state as string;

  if (!code || !type) {
    state.error = true;
    state.message = '授权失败：未获取到授权码或授权类型';
    return null;
  }

  return { code, type };
};

const startCountdown = () => {
  countdown.value = CONSTANTS.COUNTDOWN_SECONDS;
  countdownTimer.value = window.setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      clearInterval(countdownTimer.value!);
      closeWindow();
    }
  }, 1000);
};

const stopCountdown = () => {
  if (countdownTimer.value) {
    clearInterval(countdownTimer.value);
    countdownTimer.value = null;
  }
};

const showSuccessAnimation = () => {
  setTimeout(() => {
    animationState.showAnimation = true;
  }, CONSTANTS.ANIMATION_DELAYS.SHOW_ANIMATION);

  setTimeout(() => {
    animationState.showSuccess = true;
  }, CONSTANTS.ANIMATION_DELAYS.SHOW_SUCCESS);

  setTimeout(() => {
    animationState.showMessage = true;
  }, CONSTANTS.ANIMATION_DELAYS.SHOW_MESSAGE);

  setTimeout(() => {
    startCountdown();
  }, CONSTANTS.ANIMATION_DELAYS.START_COUNTDOWN);
};

const notifyParentWindow = (type: string, code?: string) => {
  if (!isWindowOpener.value) return;

  const message =
    type === CONSTANTS.OPERATION_TYPES.BIND
      ? { type: 'wechatBindSuccess' }
      : { type: 'wechatLoginSuccess', code };

  window.opener.postMessage(message, window.location.origin);
};

// 业务逻辑
const handleWechatAuth = async (code: string, type: string) => {
  try {
    state.loading = true;

    if (type === CONSTANTS.OPERATION_TYPES.BIND) {
      await bindOpenId({
        code,
        accountType: CONSTANTS.ACCOUNT_TYPES.WECHAT,
        id: userStore.userInfo?.id || '',
      });
    } else if (type === CONSTANTS.OPERATION_TYPES.LOGIN) {
      notifyParentWindow(type, code);
    }

    state.success = true;
    state.message =
      type === CONSTANTS.OPERATION_TYPES.BIND
        ? '微信账号绑定成功！'
        : '微信账号登录成功！';

    showSuccessAnimation();
    notifyParentWindow(type, code);
  } catch (error: any) {
    state.error = true;
    state.message = error?.message || '操作失败，请重试';
  } finally {
    state.loading = false;
  }
};

// 用户操作
const goBack = () => {
  stopCountdown();
  router.push(CONSTANTS.ROUTES.USER_MANAGEMENT);
};

const closeWindow = () => {
  stopCountdown();
  window.close();
};

const retryOperation = () => {
  const params = getRouteParams();
  if (params) {
    state.error = false;
    state.message = '';
    handleWechatAuth(params.code, params.type);
  }
};

// 生命周期
onMounted(() => {
  const params = getRouteParams();
  if (params) {
    handleWechatAuth(params.code, params.type);
  }
});

onUnmounted(() => {
  stopCountdown();
});
</script>

<template>
  <div
    class="auth-success-container"
    role="main"
    aria-label="第三方登录结果页面"
  >
    <ElRow justify="center" align="middle" class="full-height">
      <ElCol :span="24">
        <ElCard class="auth-card" shadow="hover">
          <!-- 加载状态 -->
          <div
            v-if="state.loading"
            class="loading-container"
            role="status"
            aria-live="polite"
          >
            <div class="loading-spinner" aria-label="加载中"></div>
            <ElText class="loading-text">正在处理授权请求...</ElText>
          </div>

          <!-- 成功状态 -->
          <div v-else-if="state.success" class="success-container">
            <!-- 成功动画 -->
            <div
              class="success-animation"
              :class="{ show: animationState.showAnimation }"
              role="img"
              aria-label="成功图标"
            >
              <div class="circle-animation">
                <ElIcon
                  class="success-icon"
                  :class="{ show: animationState.showSuccess }"
                >
                  <SuccessFilled />
                </ElIcon>
              </div>
            </div>

            <!-- 成功消息 -->
            <div
              class="success-message"
              :class="{ show: animationState.showMessage }"
            >
              <ElText class="success-title">操作成功</ElText>
              <ElText class="success-desc">{{ state.message }}</ElText>

              <!-- 倒计时显示 -->
              <div class="countdown-container">
                <ElText class="countdown-text">
                  {{ countdown }}秒后自动关闭窗口
                </ElText>
                <div
                  class="countdown-progress"
                  role="progressbar"
                  :aria-valuenow="countdown"
                  aria-valuemin="0"
                  :aria-valuemax="CONSTANTS.COUNTDOWN_SECONDS"
                >
                  <div
                    class="countdown-bar"
                    :style="{ width: `${countdownProgress}%` }"
                  ></div>
                </div>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="action-buttons">
              <ElButton
                type="primary"
                @click="goBack"
                aria-label="返回用户管理页面"
              >
                返回用户管理
              </ElButton>
              <ElButton @click="closeWindow" aria-label="关闭当前窗口">
                关闭窗口
              </ElButton>
            </div>
          </div>

          <!-- 错误状态 -->
          <div v-else-if="state.error" class="error-container">
            <div class="error-icon" role="img" aria-label="错误图标">
              <ElIcon class="error-icon-svg">
                <Check />
              </ElIcon>
            </div>
            <ElText class="error-title">操作失败</ElText>
            <ElText class="error-desc">{{ state.message }}</ElText>
            <div class="action-buttons">
              <ElButton
                type="primary"
                @click="retryOperation"
                aria-label="重试操作"
              >
                重试
              </ElButton>
              <ElButton @click="goBack" aria-label="返回用户管理页面">
                返回用户管理
              </ElButton>
              <ElButton @click="closeWindow" aria-label="关闭当前窗口">
                关闭窗口
              </ElButton>
            </div>
          </div>
        </ElCard>
      </ElCol>
    </ElRow>
  </div>
</template>

<style scoped>
@keyframes spin {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg);
  }
}

@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 10px 30px rgb(103 194 58 / 30%);
    transform: scale(1);
  }

  50% {
    box-shadow: 0 15px 40px rgb(103 194 58 / 40%);
    transform: scale(1.05);
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes scaleIn {
  from {
    opacity: 0;
    transform: scale(0.5);
  }

  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .auth-card {
    padding: 30px 15px;
  }

  .success-title {
    font-size: 24px;
  }

  .circle-animation {
    width: 100px;
    height: 100px;
  }

  .success-icon {
    font-size: 50px;
  }

  .action-buttons {
    flex-direction: column;
    align-items: center;
  }

  .action-buttons .el-button {
    width: 200px;
  }
}

/* 高对比度模式支持 */
@media (prefers-contrast: high) {
  .auth-card {
    border: 2px solid #000;
  }

  .action-buttons .el-button {
    border-width: 2px;
  }
}

/* 减少动画模式支持 */
@media (prefers-reduced-motion: reduce) {
  .success-animation,
  .success-icon,
  .success-message,
  .countdown-bar {
    transition: none;
  }

  .circle-animation {
    animation: none;
  }

  .loading-spinner {
    animation: none;
  }
}

/* 基础样式 */
.auth-success-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.full-height {
  min-height: 100vh;
}

.auth-card {
  padding: 40px 20px;
  text-align: center;
  background: rgb(255 255 255 / 95%);
  border: none;
  border-radius: 16px;
  box-shadow: 0 20px 40px rgb(0 0 0 / 10%);
  backdrop-filter: blur(10px);
  will-change: transform;
}

/* 加载状态样式 */
.loading-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
  align-items: center;
}

.loading-spinner {
  width: 60px;
  height: 60px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #409eff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  will-change: transform;
}

.loading-text {
  font-size: 16px;
  color: #606266;
}

/* 成功状态样式 */
.success-container {
  display: flex;
  flex-direction: column;
  gap: 30px;
  align-items: center;
}

.success-animation {
  opacity: 0;
  transform: scale(0.5);
  transition: all 0.6s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  will-change: transform, opacity;
}

.success-animation.show {
  opacity: 1;
  transform: scale(1);
}

.circle-animation {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 120px;
  background: linear-gradient(135deg, #67c23a, #85ce61);
  border-radius: 50%;
  box-shadow: 0 10px 30px rgb(103 194 58 / 30%);
  animation: pulse 2s ease-in-out infinite;
  will-change: transform, box-shadow;
}

.success-icon {
  font-size: 60px;
  color: white;
  opacity: 0;
  transform: scale(0.5);
  transition: all 0.4s ease;
  will-change: transform, opacity;
}

.success-icon.show {
  opacity: 1;
  transform: scale(1);
}

.success-message {
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.6s ease;
  will-change: transform, opacity;
}

.success-message.show {
  opacity: 1;
  transform: translateY(0);
}

.success-title {
  display: block;
  margin-bottom: 10px;
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.success-desc {
  display: block;
  font-size: 16px;
  line-height: 1.6;
  color: #606266;
}

/* 倒计时样式 */
.countdown-container {
  margin-top: 20px;
  text-align: center;
}

.countdown-text {
  display: block;
  margin-bottom: 10px;
  font-size: 14px;
  color: #909399;
}

.countdown-progress {
  width: 200px;
  height: 4px;
  margin: 0 auto;
  overflow: hidden;
  background: #f0f0f0;
  border-radius: 2px;
}

.countdown-bar {
  height: 100%;
  background: linear-gradient(90deg, #67c23a, #85ce61);
  border-radius: 2px;
  transition: width 0.3s ease;
  will-change: width;
}

/* 错误状态样式 */
.error-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
  align-items: center;
}

.error-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #f56c6c, #f78989);
  border-radius: 50%;
  box-shadow: 0 10px 30px rgb(245 108 108 / 30%);
}

.error-icon-svg {
  font-size: 40px;
  color: white;
}

.error-title {
  display: block;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.error-desc {
  display: block;
  font-size: 16px;
  line-height: 1.6;
  color: #606266;
  text-align: center;
}

/* 操作按钮样式 */
.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  margin-top: 20px;
}

.action-buttons .el-button {
  min-width: 120px;
  font-weight: 500;
  border-radius: 8px;
  transition: all 0.3s ease;
  will-change: transform, box-shadow;
}

.action-buttons .el-button:hover {
  box-shadow: 0 8px 20px rgb(0 0 0 / 15%);
  transform: translateY(-2px);
}

.action-buttons .el-button:focus {
  outline: 2px solid #409eff;
  outline-offset: 2px;
}

/* 动画定义 */
</style>
