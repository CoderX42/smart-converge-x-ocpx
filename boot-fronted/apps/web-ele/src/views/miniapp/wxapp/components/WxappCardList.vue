<script lang="ts" setup>
import { ref } from 'vue';

import { Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCard,
  ElCol,
  ElEmpty,
  ElImage,
  ElMessage,
  ElMessageBox,
  ElRow,
  ElTag,
} from 'element-plus';

import {
  releaseObj,
  revertCodeRelease,
  submitAudit,
  addObj as submitCodeAudit,
} from '#/api/open/code-audit';
import { convertToFormal, delObj } from '#/api/open/open-platform-app';
import { getList as getTemplateList } from '#/api/open/template-info';
import QrCodeDialog from '#/views/open/code-audit/components/QrCodeDialog.vue';

interface OpenCodeAudit {
  id: string;
  appId: string;
  appName: string;
  userVersion: string;
  status: string; // 状态:0.审核成功；1.审核被拒绝；2.审核中；3.已撤回；4.审核延后；5.检测中；6.已发布
  templateInfoId: string;
}
interface WxappItem {
  appId: string;
  appName: string;
  appSecret: string;
  qrcodeUrl?: string;
  createTime: string;
  betaStatus: string; // 试用状态：0-否，1-是
  componentStatus: string; // 三方平台状态：0-未授权，1-已授权
  lastOpenCodeAudit: OpenCodeAudit;
  [key: string]: any;
}

const props = defineProps<{
  data: WxappItem[];
  loading: boolean;
}>();
const emit = defineEmits<{
  (e: 'edit', row: WxappItem): void;
  (e: 'initPage'): void;
  (e: 'add'): void;
}>();
const codeTemplate = ref<any>();
const qrCodeDialogVisible = ref(false);
const currentAuditId = ref('');
const updateButtonLoading = ref(false);
const handleEdit = (row: any) => {
  emit('edit', row);
};

const handleDelete = (id: string) => {
  ElMessageBox.confirm('此操作将删除该微信应用，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id).then(() => {
      emit('initPage');
    });
  });
};

const handleConvert = (row: WxappItem) => {
  ElMessageBox.confirm('确定要将该小程序转为正式版吗？', '转正确认', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await convertToFormal(row.appId);
      ElMessage.success('转正成功');
      emit('initPage');
    } catch {
      ElMessage.error('转正失败');
    }
  });
};
const handleUpdate = async (row: WxappItem) => {
  try {
    updateButtonLoading.value = true;
    // 根据不同的状态执行不同的操作
    if (row.lastOpenCodeAudit) {
      switch (row.lastOpenCodeAudit.status) {
        case '0': {
          // 审核成功，执行上架操作
          await releaseObj(row.lastOpenCodeAudit.id);

          ElMessage.success('发布版本成功');
          break;
        }
        case '1':
        case '3':
        case '7': {
          // 审核被驳回，重新上传版本
          await submitCodeAudit({ appId: row.appId, appName: row.appName });
          ElMessage.success('重新上传版本成功');
          break;
        }
        case '2':
        case '4':
        case '5': {
          // 审核中、已撤回、审核延后、检测中状态，查看体验码
          getQrCodeFn(row.lastOpenCodeAudit.id);
          break;
        }
        case '6': {
          // 已发布状态，检查是否需要更新版本
          if (row.lastOpenCodeAudit.templateInfoId === codeTemplate.value?.id) {
            // 版本相同，执行上架操作
            ElMessage.warning('已是最新版本，无需更新');
          } else {
            // 版本不同，执行更新版本操作
            await submitCodeAudit({ appId: row.appId, appName: row.appName });
            ElMessage.success('更新版本成功');
          }
          break;
        }
        default: {
          // 其他状态
          ElMessage.info('当前状态不支持此操作');
          return;
        }
      }
    } else {
      // 不存在审核记录，执行上传版本操作
      await submitCodeAudit({ appId: row.appId, appName: row.appName });
      ElMessage.success('上传版本成功');
    }
    emit('initPage');
  } catch {
    ElMessage.error('操作失败');
  } finally {
    updateButtonLoading.value = false;
  }
};
const getTemplateListFn = async () => {
  const res = await getTemplateList({ status: '0' });
  if (res) {
    codeTemplate.value = res[res.length - 1];
  }
};
const getQrCodeFn = (id: string) => {
  currentAuditId.value = id;
  qrCodeDialogVisible.value = true;
};
// 获取更新按钮的文本
const getUpdateButtonText = (item: WxappItem) => {
  if (!item.lastOpenCodeAudit) {
    return '上传版本';
  }

  switch (item.lastOpenCodeAudit.status) {
    case '0': {
      return '发布版本';
    }
    case '1':
    case '3':
    case '7': {
      return '重新上传版本';
    }
    case '2':
    case '4':
    case '5': {
      return '体验码';
    }
    case '6': {
      return item.lastOpenCodeAudit.templateInfoId === codeTemplate.value?.id
        ? '已是最新版本'
        : '更新版本';
    }
    default: {
      return item.betaStatus === '1' ? '体验中' : '审核中';
    }
  }
};

// 获取审核状态的显示文本
const getAuditStatusText = (status: string) => {
  switch (status) {
    case '0': {
      return '审核成功';
    }
    case '1': {
      return '审核被拒绝';
    }
    case '2': {
      return '审核中';
    }
    case '3': {
      return '已撤回';
    }
    case '4': {
      return '审核延后';
    }
    case '5': {
      return '检测中';
    }
    case '6': {
      return '已发布';
    }
    case '7': {
      return '已回退';
    }
    default: {
      return '未知状态';
    }
  }
};

// 获取审核状态的标签类型
const getAuditStatusType = (status: string) => {
  switch (status) {
    case '0': {
      return 'success';
    }
    case '1': {
      return 'danger';
    }
    case '2': {
      return 'warning';
    }
    case '3': {
      return 'info';
    }
    case '4': {
      return 'warning';
    }
    case '5': {
      return 'warning';
    }
    case '6': {
      return 'success';
    }
    case '7': {
      return 'danger';
    }
    default: {
      return 'info';
    }
  }
};

// 判断按钮是否可点击
const isUpdateButtonDisabled = (item: WxappItem) => {
  if (!item.lastOpenCodeAudit) {
    return false;
  }

  // 状态 2、3、4、5 都可以查看体验版，所以不禁用
  return false;
};
// 回退版本
const handleRollback = (item: WxappItem) => {
  ElMessageBox.confirm('此操作将回退上一个版本，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      updateButtonLoading.value = true;
      await revertCodeRelease(item.id);
      ElMessage.success('回退版本成功');
      emit('initPage');
    } finally {
      updateButtonLoading.value = false;
    }
  });
};
// 提交审核
const handleSubmitAudit = (item: WxappItem) => {
  ElMessageBox.confirm('此操作将提交审核，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      updateButtonLoading.value = true;
      await submitAudit(item.lastOpenCodeAudit.id);
      ElMessage.success('提交审核成功');
      emit('initPage');
    } finally {
      updateButtonLoading.value = false;
    }
  });
};

getTemplateListFn();
</script>

<template>
  <div v-loading="loading">
    <!-- 空状态占位UI -->
    <div v-if="!loading && data.length === 0" class="empty-state">
      <ElEmpty :image-size="120" description="">
        <template #image>
          <div class="empty-icon">
            <svg viewBox="0 0 1024 1024" width="120" height="120">
              <path
                d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z"
                fill="#e6f7ff"
              />
              <path
                d="M623.6 316.7C593.6 290.4 554 276 512 276s-81.6 14.4-111.6 40.7C369.2 344 352 380.7 352 420.5c0 39.8 17.2 76.5 48.4 103.8 30 26.3 69.6 40.7 111.6 40.7s81.6-14.4 111.6-40.7C654.8 497 672 460.3 672 420.5c0-39.8-17.2-76.5-48.4-103.8zM512 536c-63.9 0-115.7-51.8-115.7-115.7S448.1 304.6 512 304.6s115.7 51.8 115.7 115.7S575.9 536 512 536z"
                fill="#1890ff"
              />
              <path
                d="M448 420.5c0-35.3 28.7-64 64-64s64 28.7 64 64-28.7 64-64 64-64-28.7-64-64z"
                fill="#40a9ff"
              />
              <path
                d="M512 576c-97.2 0-176 78.8-176 176h352c0-97.2-78.8-176-176-176z"
                fill="#69c0ff"
              />
            </svg>
          </div>
        </template>
        <template #description>
          <div class="empty-content">
            <h3 class="empty-title">暂无微信小程序</h3>
            <p class="empty-description">
              您还没有添加任何微信小程序应用<br />
              点击下方按钮开始创建您的第一个小程序
            </p>
          </div>
        </template>
        <ElButton
          type="primary"
          :icon="Plus"
          size="large"
          @click="emit('add')"
          class="add-button"
        >
          添加小程序
        </ElButton>
      </ElEmpty>
    </div>

    <!-- 数据列表 -->
    <ElRow v-else :gutter="16">
      <ElCol
        v-for="item in data"
        :key="item.appId"
        :xs="24"
        :sm="12"
        :md="8"
        :lg="6"
        :xl="6"
        class="mb-4"
      >
        <ElCard shadow="hover" class="wxapp-card">
          <div class="wxapp-card-header">
            <div class="wxapp-card-title">{{ item.appName }}</div>
            <div class="wxapp-card-tags">
              <ElTag
                :type="item.componentStatus === '1' ? 'success' : 'info'"
                size="small"
              >
                {{ item.componentStatus === '1' ? '已授权' : '未授权' }}
              </ElTag>
            </div>
          </div>
          <div class="wxapp-card-content">
            <div class="wxapp-card-image">
              <ElImage
                v-if="item.qrcodeUrl"
                style="width: 80px; height: 80px"
                :src="item.qrcodeUrl"
                :preview-src-list="[item.qrcodeUrl]"
                referrer-policy="no-referrer"
                fit="cover"
                :preview-teleported="true"
              />
              <div v-else class="no-image">暂无图片</div>
            </div>
            <div class="wxapp-card-info">
              <div class="info-item">
                <span class="label">应用ID：</span>
                <span class="value">{{ item.appId }}</span>
              </div>
              <div class="info-item">
                <span class="label">秘钥：</span>
                <span class="value">{{ item.appSecret }}</span>
              </div>
              <div v-if="item.lastOpenCodeAudit" class="info-item">
                <span class="label">最新提审版本：</span>
                <span
                  class="value"
                  style="font-size: 16px; font-weight: 700; color: #303133"
                >
                  {{ item.lastOpenCodeAudit.userVersion }}
                  <ElTag
                    :type="getAuditStatusType(item.lastOpenCodeAudit.status)"
                    size="small"
                    class="ml-2"
                  >
                    {{ getAuditStatusText(item.lastOpenCodeAudit.status) }}
                  </ElTag>
                </span>
              </div>
              <div class="info-item">
                <span class="label">创建时间：</span>
                <span class="value">{{ item.createTime }}</span>
              </div>
            </div>
          </div>
          <div class="wxapp-card-footer">
            <ElButton
              type="primary"
              size="small"
              @click="handleEdit(item)"
              v-access:code="'open:openPlatformApp:edit'"
            >
              修改
            </ElButton>
            <ElButton
              type="primary"
              size="small"
              v-if="
                item.lastOpenCodeAudit && item.lastOpenCodeAudit.status === '6'
              "
              @click="handleRollback(item)"
              :loading="updateButtonLoading"
              v-access:code="'open:openPlatformApp:edit'"
            >
              回退版本
            </ElButton>
            <ElButton
              type="primary"
              size="small"
              v-if="
                item.lastOpenCodeAudit && item.lastOpenCodeAudit.status === '5'
              "
              @click="handleSubmitAudit(item)"
              :loading="updateButtonLoading"
              v-access:code="'open:openPlatformApp:edit'"
            >
              提交审核
            </ElButton>
            <ElButton
              v-if="item.betaStatus === '1'"
              type="success"
              size="small"
              @click="handleConvert(item)"
              v-access:code="'open:openPlatformApp:edit'"
            >
              转正小程序
            </ElButton>
            <ElButton
              v-if="item.componentStatus === '1'"
              :type="item.betaStatus === '1' ? 'warning' : 'success'"
              size="small"
              @click="handleUpdate(item)"
              v-access:code="'open:openPlatformApp:edit'"
              :disabled="isUpdateButtonDisabled(item)"
              :loading="updateButtonLoading"
            >
              {{ getUpdateButtonText(item) }}
            </ElButton>
            <ElButton
              type="danger"
              size="small"
              @click="handleDelete(item.appId)"
              v-access:code="'open:openPlatformApp:del'"
            >
              删除
            </ElButton>
          </div>
        </ElCard>
      </ElCol>
    </ElRow>

    <!-- 二维码弹窗 -->
    <QrCodeDialog
      v-model:visible="qrCodeDialogVisible"
      :audit-id="currentAuditId"
    />
  </div>
</template>

<style scoped>
.mb-4 {
  margin-bottom: 16px;
}

.wxapp-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.wxapp-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.wxapp-card-title {
  flex: 1;
  margin-right: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 16px;
  font-weight: bold;
  white-space: nowrap;
}

.wxapp-card-tags {
  display: flex;
  align-items: center;
}

.mr-2 {
  margin-right: 8px;
}

.wxapp-card-content {
  display: flex;
  margin-bottom: 16px;
}

.wxapp-card-image {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.no-image {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  font-size: 12px;
  color: #909399;
  background-color: #f5f7fa;
}

.wxapp-card-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: space-around;
}

.info-item {
  margin-bottom: 4px;
  font-size: 13px;
}

.label {
  color: #606266;
}

.value {
  color: #303133;
  word-break: break-all;
}

.audit-info {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.ml-2 {
  margin-left: 8px;
}

.wxapp-card-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  margin-top: auto;
}

.text-gray-600 {
  color: #6b7280;
}

.font-medium {
  font-weight: 500;
}

.dialog-footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

/* 空状态样式 */
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: 40px 20px;
}

.empty-icon {
  margin-bottom: 16px;
}

.empty-content {
  margin-bottom: 24px;
  text-align: center;
}

.empty-title {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.empty-description {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #909399;
}

.add-button {
  padding: 12px 32px;
  font-size: 16px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.add-button:hover {
  box-shadow: 0 4px 12px rgb(24 144 255 / 30%);
  transform: translateY(-2px);
}
</style>
