<script lang="ts" setup>
import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCard,
  ElCol,
  ElEmpty,
  ElMessage,
  ElMessageBox,
  ElRow,
} from 'element-plus';

import { delObj } from '#/api/open/open-platform-app';

import MpAppQr from './MpAppQr.vue';

interface MpAppItem {
  appId: string;
  appName: string;
  appSecret: string;
  aesKey: string;
  token: string;
  createTime: string;
  [key: string]: any;
}

defineProps<{
  data: MpAppItem[];
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: 'edit', row: MpAppItem): void;
  (e: 'initPage'): void;
  (e: 'add'): void;
}>();

const getQrUrl = (appId: string) => {
  const base = import.meta.env.VITE_PREVIEW_URL;
  return `${base}?appid=${appId}`;
};

const handleEdit = (row: any) => {
  emit('edit', row);
};

const handleDelete = (id: string) => {
  ElMessageBox.confirm('此操作将删除该公众号，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        emit('initPage');
      })
      .catch(() => {});
  });
};
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
            <h3 class="empty-title">暂无公众号</h3>
            <p class="empty-description">
              您还没有添加任何公众号应用<br />
              点击下方按钮开始创建您的第一个公众号
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
          添加公众号
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
        <ElCard shadow="hover" class="mp-app-card">
          <div class="mp-app-card-header">
            <div class="mp-app-card-title">{{ item.appName }}</div>
          </div>
          <div class="mp-app-card-content">
            <MpAppQr :url="getQrUrl(item.appId)" />
            <div class="mp-app-card-info">
              <div class="info-item">
                <span class="label">应用ID：</span>
                <span class="value">{{ item.appId }}</span>
              </div>
              <div class="info-item">
                <span class="label">秘钥：</span>
                <span class="value">{{ item.appSecret }}</span>
              </div>
              <div class="info-item">
                <span class="label">Token：</span>
                <span class="value">{{ item.token }}</span>
              </div>
              <div class="info-item">
                <span class="label">创建时间：</span>
                <span class="value">{{ item.createTime }}</span>
              </div>
            </div>
          </div>
          <div class="mp-app-card-footer">
            <ElButton
              type="primary"
              size="small"
              @click="handleEdit(item)"
              v-access:code="'open:openPlatformApp:edit'"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              type="danger"
              size="small"
              @click="handleDelete(item.appId)"
              v-access:code="'open:openPlatformApp:del'"
              :icon="Delete"
            >
              删除
            </ElButton>
          </div>
        </ElCard>
      </ElCol>
    </ElRow>
  </div>
</template>

<style scoped>
.mb-4 {
  margin-bottom: 16px;
}

.mp-app-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.mp-app-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.mp-app-card-title {
  flex: 1;
  margin-right: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 16px;
  font-weight: bold;
  white-space: nowrap;
}

.mp-app-card-content {
  display: flex;
  margin-bottom: 16px;
}

.qr-code {
  flex-shrink: 0;
  width: 80px;
  height: 80px;
  margin-right: 12px;
}

.mp-app-card-info {
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

.mp-app-card-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  margin-top: auto;
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
