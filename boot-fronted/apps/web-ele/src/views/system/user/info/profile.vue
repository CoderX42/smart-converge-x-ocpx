<script setup lang="ts">
import type { FormRules } from 'element-plus';

import { onMounted, reactive, ref } from 'vue';

import { useAppConfig } from '@boot/hooks';
import { useAccessStore, useUserStore } from '@boot/stores';

import {
  Edit,
  Link,
  Lock,
  Message,
  Operation,
  Phone,
  User,
} from '@element-plus/icons-vue';
import {
  ElAvatar,
  ElButton,
  ElCard,
  ElCol,
  ElDialog,
  ElDivider,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElRow,
  ElTabPane,
  ElTabs,
  ElTag,
  ElText,
  ElUpload,
} from 'element-plus';

import { getByType } from '#/api/system/social-account';
import { updateInfo as editObj, unBindOpenId } from '#/api/system/user';

import UpdatePassword from './update-password.vue';

const userStore = useUserStore();
const accessStore = useAccessStore();
const headers = ref({
  satoken: accessStore.accessToken,
});
const isOpenBoot = import.meta.env.VITE_OPEN_BOOT === 'true';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const action = ref(
  `${apiURL}${isOpenBoot ? '/boot' : '/system'}/file/upload?type=1&groupId=-1`,
);

// 响应式状态
const state = reactive({
  loading: false,
  userInfo: {
    id: '',
    username: '',
    nickname: '',
    email: '',
    phone: '',
    avatar: '',
    sex: '',
    province: '',
    city: '',
    address: '',
    deptName: '',
    wxOpenId: '',
  },
  isBound: false,
  activeTab: 'basic',
});

// 表单引用
const basicFormRef = ref();
// 对话框状态
const basicDialogVisible = ref(false);
const passwordDialogVisible = ref(false);

// 表单数据
const basicForm = reactive({
  id: '',
  nickname: '',
  email: '',
  phone: '',
  avatar: '',
  sex: '',
  province: '',
  city: '',
  address: '',
});

// 表单验证规则
const basicRules: FormRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度在 2 到 20 个字符', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }],
};

// 获取用户信息
const getUserInfo = async () => {
  try {
    state.loading = true;

    Object.assign(state.userInfo, userStore.userInfo);
    state.isBound = !!userStore.userInfo?.wxOpenId;
  } catch (error) {
    console.error('获取用户信息失败:', error);
    ElMessage.error('获取用户信息失败');
  } finally {
    state.loading = false;
  }
};

// 打开基本信息编辑对话框
const openBasicDialog = () => {
  Object.assign(basicForm, {
    id: state.userInfo.id,
    nickname: state.userInfo.nickname,
    email: state.userInfo.email,
    phone: state.userInfo.phone,
    avatar: state.userInfo.avatar,
    sex: state.userInfo.sex,
    province: state.userInfo.province,
    city: state.userInfo.city,
    address: state.userInfo.address,
  });
  basicDialogVisible.value = true;
};

// 提交基本信息修改
const submitBasicForm = async () => {
  try {
    await basicFormRef.value.validate();
    state.loading = true;

    await editObj(basicForm);
    ElMessage.success('基本信息修改成功');

    // 更新本地用户信息
    Object.assign(state.userInfo, basicForm);

    // 更新store中的用户信息
    if (userStore.userInfo) {
      Object.assign(userStore.userInfo, basicForm);
    }

    basicDialogVisible.value = false;
  } catch (error) {
    console.error('修改失败:', error);
    ElMessage.error('修改失败，请重试');
  } finally {
    state.loading = false;
  }
};

// 打开密码修改对话框
const openPasswordDialog = () => {
  passwordDialogVisible.value = true;
};

const handlePasswordUpdateSuccess = () => {
  passwordDialogVisible.value = false;
};

// 绑定微信账号
const bindWechatAccount = async () => {
  try {
    const res = await getByType('1');
    if (!res) {
      return ElMessage.error('微信配置不存在');
    }

    const appId = res.appId;
    const redirect = encodeURIComponent(
      `${window.location.origin}/#/third-party-success`,
    );
    const url = `https://open.weixin.qq.com/connect/qrconnect?appid=${appId}&redirect_uri=${redirect}&response_type=code&scope=snsapi_login&state=bind#wechat_redirect`;

    const width = 500;
    const height = 500;
    const left = (window.innerWidth - width) / 2 + window.screenX;
    const top = (window.innerHeight - height) / 2 + window.screenY;

    const windowFeatures = `width=${width},height=${height},left=${left},top=${top},resizable=yes,scrollbars=yes,status=yes`;
    window.open(url, 'WeChatLogin', windowFeatures);
    // 监听来自子窗口的消息
    const handleMessage = async (event: { data: { type: string } }) => {
      // 这里建议加安全校验，比如 event.origin === 'https://你的域名'
      if (event.data?.type === 'wechatBindSuccess') {
        // 刷新页面
        window.location.reload();
        window.removeEventListener('message', handleMessage);
      }
    };

    window.addEventListener('message', handleMessage);
  } catch {
    ElMessage.error('获取微信配置失败');
  }
};

// 解绑微信账号
const unbindWechatAccount = async () => {
  try {
    state.loading = true;
    await unBindOpenId({ accountType: '1' });
    ElMessage.success('解绑成功');
    state.isBound = false;
    state.userInfo.wxOpenId = '';
  } catch (error) {
    console.error('解绑失败:', error);
    ElMessage.error('解绑失败，请重试');
  } finally {
    state.loading = false;
  }
};

// 头像上传处理
const handleAvatarSuccess = (response: any) => {
  basicForm.avatar = response.data;
};

onMounted(() => {
  getUserInfo();
});
</script>

<template>
  <div class="profile-container">
    <ElRow :gutter="24">
      <!-- 左侧用户信息卡片 -->
      <ElCol :span="8">
        <ElCard class="user-card" shadow="hover">
          <div class="user-header">
            <ElAvatar
              :size="80"
              :src="state.userInfo.avatar"
              class="user-avatar"
            >
              <ElIcon>
                <User />
              </ElIcon>
            </ElAvatar>
            <div class="user-info">
              <ElText class="username">
                {{ state.userInfo.nickname || state.userInfo.username }}
              </ElText>
              <ElText class="user-id">ID: {{ state.userInfo.id }}</ElText>
            </div>
          </div>

          <ElDivider />

          <div class="user-details">
            <div class="detail-item">
              <ElIcon>
                <Phone />
              </ElIcon>
              <ElText>{{ state.userInfo.phone || '未设置' }}</ElText>
            </div>
            <div class="detail-item">
              <ElIcon>
                <Message />
              </ElIcon>
              <ElText>{{ state.userInfo.email || '未设置' }}</ElText>
            </div>
            <div class="detail-item">
              <ElIcon>
                <Operation />
              </ElIcon>
              <ElText>{{ state.userInfo.deptName || '未设置' }}</ElText>
            </div>
          </div>
        </ElCard>
      </ElCol>

      <!-- 右侧功能区域 -->
      <ElCol :span="16">
        <ElCard shadow="hover">
          <ElTabs v-model="state.activeTab" class="profile-tabs">
            <!-- 基本信息 -->
            <ElTabPane label="基本信息" name="basic">
              <div class="tab-content">
                <div class="info-section">
                  <div class="section-header">
                    <ElText class="section-title">基本信息</ElText>
                    <ElButton
                      type="primary"
                      :icon="Edit"
                      size="small"
                      @click="openBasicDialog"
                    >
                      编辑
                    </ElButton>
                  </div>

                  <ElRow :gutter="20" class="info-grid">
                    <ElCol :span="12">
                      <div class="info-item">
                        <ElText class="label">昵称：</ElText>
                        <ElText class="value">
                          {{ state.userInfo.nickname || '未设置' }}
                        </ElText>
                      </div>
                    </ElCol>
                    <ElCol :span="12">
                      <div class="info-item">
                        <ElText class="label">用户名：</ElText>
                        <ElText class="value">
                          {{ state.userInfo.username }}
                        </ElText>
                      </div>
                    </ElCol>
                    <ElCol :span="12">
                      <div class="info-item">
                        <ElText class="label">手机号：</ElText>
                        <ElText class="value">
                          {{ state.userInfo.phone || '未设置' }}
                        </ElText>
                      </div>
                    </ElCol>
                    <ElCol :span="12">
                      <div class="info-item">
                        <ElText class="label">邮箱：</ElText>
                        <ElText class="value">
                          {{ state.userInfo.email || '未设置' }}
                        </ElText>
                      </div>
                    </ElCol>
                  </ElRow>
                </div>
              </div>
            </ElTabPane>

            <!-- 账号安全 -->
            <ElTabPane label="账号安全" name="security">
              <div class="tab-content">
                <div class="security-section">
                  <div class="section-header">
                    <ElText class="section-title">密码管理</ElText>
                    <ElButton
                      type="primary"
                      :icon="Lock"
                      size="small"
                      @click="openPasswordDialog"
                    >
                      修改密码
                    </ElButton>
                  </div>

                  <div class="security-item">
                    <div class="security-info">
                      <ElIcon class="security-icon">
                        <Lock />
                      </ElIcon>
                      <div class="security-details">
                        <ElText class="security-title">登录密码</ElText>
                        <ElText class="security-desc">用于保护账号安全</ElText>
                      </div>
                    </div>
                    <ElTag type="success">已设置</ElTag>
                  </div>
                </div>

                <ElDivider />

                <div class="security-section">
                  <div class="section-header">
                    <ElText class="section-title">第三方账号</ElText>
                  </div>

                  <div class="security-item">
                    <div class="security-info">
                      <ElIcon class="security-icon">
                        <Link />
                      </ElIcon>
                      <div class="security-details">
                        <ElText class="security-title">微信账号</ElText>
                        <ElText class="security-desc">
                          绑定微信账号，快速登录
                        </ElText>
                      </div>
                    </div>
                    <div class="security-actions">
                      <ElTag v-if="state.isBound" type="success">已绑定</ElTag>
                      <ElTag v-else type="info">未绑定</ElTag>
                      <ElButton
                        v-if="!state.isBound"
                        type="primary"
                        :icon="Link"
                        size="small"
                        @click="bindWechatAccount"
                      >
                        绑定
                      </ElButton>
                      <ElButton
                        v-else
                        type="danger"
                        :icon="Link"
                        size="small"
                        @click="unbindWechatAccount"
                      >
                        解绑
                      </ElButton>
                    </div>
                  </div>
                </div>
              </div>
            </ElTabPane>
          </ElTabs>
        </ElCard>
      </ElCol>
    </ElRow>

    <!-- 基本信息编辑对话框 -->
    <ElDialog
      v-model="basicDialogVisible"
      title="编辑基本信息"
      width="600px"
      :before-close="() => (basicDialogVisible = false)"
    >
      <ElForm
        ref="basicFormRef"
        :model="basicForm"
        :rules="basicRules"
        label-width="100px"
      >
        <ElFormItem label="头像" prop="avatar">
          <ElUpload
            class="avatar-uploader"
            append-to-body
            :action="action"
            :headers="headers"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
          >
            <ElAvatar
              v-if="basicForm.avatar"
              :src="basicForm.avatar"
              :size="100"
            />
            <ElIcon v-else class="avatar-uploader-icon">
              <User />
            </ElIcon>
          </ElUpload>
        </ElFormItem>

        <ElFormItem label="昵称" prop="nickname">
          <ElInput v-model="basicForm.nickname" placeholder="请输入昵称" />
        </ElFormItem>

        <ElFormItem label="邮箱" prop="email">
          <ElInput v-model="basicForm.email" placeholder="请输入邮箱" />
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="basicDialogVisible = false">取消</ElButton>
        <ElButton
          type="primary"
          :loading="state.loading"
          @click="submitBasicForm"
        >
          确定
        </ElButton>
      </template>
    </ElDialog>

    <!-- 密码修改对话框 -->
    <ElDialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="500px"
      :before-close="() => (passwordDialogVisible = false)"
    >
      <UpdatePassword
        v-if="passwordDialogVisible"
        :user-id="state.userInfo.id"
        @up-success="handlePasswordUpdateSuccess"
      />
    </ElDialog>
  </div>
</template>

<style scoped>
/* 响应式设计 */
@media (max-width: 768px) {
  .profile-container {
    padding: 10px;
  }

  .user-header {
    flex-direction: column;
    text-align: center;
  }

  .info-item {
    flex-direction: column;
    gap: 4px;
    align-items: flex-start;
  }

  .security-item {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .security-actions {
    justify-content: flex-end;
    width: 100%;
  }
}

.profile-container {
  min-height: calc(100vh - 120px);
  padding: 20px;
  background-color: #f5f7fa;
}

.user-card {
  height: fit-content;
}

.user-header {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

.user-avatar {
  border: 3px solid #e4e7ed;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.username {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.user-id {
  font-size: 14px;
  color: #909399;
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-item {
  display: flex;
  gap: 8px;
  align-items: center;
  color: #606266;
}

.detail-item .el-icon {
  color: #909399;
}

.profile-tabs {
  min-height: 400px;
}

.tab-content {
  padding: 20px 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.info-grid {
  margin-bottom: 20px;
}

.info-item {
  display: flex;
  padding: 12px;
  margin-bottom: 16px;
  background-color: #fafafa;
  border-radius: 6px;
}

.info-item .label {
  min-width: 80px;
  font-weight: 500;
  color: #909399;
}

.info-item .value {
  flex: 1;
  color: #303133;
}

.security-section {
  margin-bottom: 20px;
}

.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  margin-bottom: 12px;
  background-color: #fafafa;
  border-radius: 8px;
}

.security-info {
  display: flex;
  gap: 12px;
  align-items: center;
}

.security-icon {
  font-size: 20px;
  color: #409eff;
}

.security-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.security-title {
  font-weight: 500;
  color: #303133;
}

.security-desc {
  font-size: 12px;
  color: #909399;
}

.security-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.avatar-uploader {
  text-align: center;
}

.avatar-uploader .el-avatar {
  cursor: pointer;
  border: 2px dashed #d9d9d9;
  border-radius: 6px;
  transition: border-color 0.3s;
}

.avatar-uploader .el-avatar:hover {
  border-color: #409eff;
}

.avatar-uploader-icon {
  width: 100px;
  height: 100px;
  font-size: 28px;
  line-height: 100px;
  color: #8c939d;
  text-align: center;
  cursor: pointer;
  border: 2px dashed #d9d9d9;
  border-radius: 6px;
  transition: border-color 0.3s;
}

.avatar-uploader-icon:hover {
  border-color: #409eff;
}
</style>
