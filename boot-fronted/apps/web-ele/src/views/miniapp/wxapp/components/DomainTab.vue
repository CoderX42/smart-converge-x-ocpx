<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue';

import {
  ElButton,
  ElCard,
  ElCol,
  ElInput,
  ElMessage,
  ElRow,
} from 'element-plus';

import {
  downloadDomainVerifyFile,
  getBusinessDomain,
  getServerDomain,
  updateBusinessDomain as updateBusinessDomainApi,
  updateServerDomain as updateServerDomainApi,
} from '#/api/open/domain';

import { downloadFileFromBlobPart } from '../../../../../../../packages/@core/base/shared/src/utils/download';

interface Props {
  appId: string;
  loading: boolean;
  miniBaseInfo: any;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:loading', value: boolean): void;
}>();

const serverDomainInputs = ref({
  requestDomain: '',
  wsRequestDomain: '',
  uploadDomain: '',
  downloadDomain: '',
});

const businessDomainInputs = ref({
  webViewDomain: '',
});

// 获取服务器域名
const getServerDomainFn = async () => {
  try {
    const response = await getServerDomain(props.appId);
    if (response) {
      serverDomainInputs.value.requestDomain =
        response.requestDomain?.join('\n') || '';
      serverDomainInputs.value.wsRequestDomain =
        response.wsRequestDomain?.join('\n') || '';
      serverDomainInputs.value.uploadDomain =
        response.uploadDomain?.join('\n') || '';
      serverDomainInputs.value.downloadDomain =
        response.downloadDomain?.join('\n') || '';
    }
  } catch (error) {
    console.error('获取服务器域名失败:', error);
  }
};

// 获取业务域名
const getBusinessDomainFn = async () => {
  try {
    const response = await getBusinessDomain(props.appId);
    if (response) {
      businessDomainInputs.value.webViewDomain =
        response.webViewDomain?.join('\n') || '';
    }
  } catch (error) {
    console.error('获取业务域名失败:', error);
  }
};

// 更新服务器域名
const updateServerDomain = async () => {
  emit('update:loading', true);
  try {
    const data = {
      appId: props.appId,
      requestDomain: serverDomainInputs.value.requestDomain
        .split('\n')
        .map((domain) => domain.trim())
        .filter(Boolean),
      wsRequestDomain: serverDomainInputs.value.wsRequestDomain
        .split('\n')
        .map((domain) => domain.trim())
        .filter(Boolean),
      uploadDomain: serverDomainInputs.value.uploadDomain
        .split('\n')
        .map((domain) => domain.trim())
        .filter(Boolean),
      downloadDomain: serverDomainInputs.value.downloadDomain
        .split('\n')
        .map((domain) => domain.trim())
        .filter(Boolean),
    };
    await updateServerDomainApi(data);
    ElMessage.success('服务器域名配置保存成功');
  } catch (error) {
    ElMessage.error('服务器域名配置保存失败');
    console.error('保存服务器域名失败:', error);
  } finally {
    emit('update:loading', false);
  }
};

// 更新业务域名
const updateBusinessDomain = async () => {
  emit('update:loading', true);
  try {
    const data = {
      appId: props.appId,
      webViewDomain: businessDomainInputs.value.webViewDomain
        .split('\n')
        .map((domain) => domain.trim())
        .filter(Boolean),
    };
    await updateBusinessDomainApi(data);
    ElMessage.success('业务域名配置保存成功');
  } catch (error) {
    ElMessage.error('业务域名配置保存失败');
    console.error('保存业务域名失败:', error);
  } finally {
    emit('update:loading', false);
  }
};

// 下载域名校验文件
const downloadVerifyFile = async () => {
  try {
    const domains = businessDomainInputs.value.webViewDomain
      .split('\n')
      .map((domain) => domain.trim())
      .filter(Boolean);

    if (domains.length === 0) {
      ElMessage.warning('请先输入域名');
      return;
    }

    const response = await downloadDomainVerifyFile(props.appId);
    // 文件内容
    const fileContent = response.fileContent;
    // 文件名称
    const fileName = response.fileName;

    // 创建并下载文件
    if (fileContent && fileName) {
      downloadFileFromBlobPart({
        fileName,
        source: fileContent,
      });
      ElMessage.success('校验文件下载成功');
    } else {
      ElMessage.error('文件内容或文件名获取失败');
    }
  } catch (error) {
    ElMessage.error('校验文件下载失败');
    console.error('下载校验文件失败:', error);
  }
};
// 监听 miniBaseInfo 变化，初始化表单数据
watch(
  () => props.miniBaseInfo,
  (newMiniBaseInfo) => {
    if (newMiniBaseInfo && newMiniBaseInfo.principal_type !== 0) {
      getBusinessDomainFn();
    }
  },
  { immediate: true },
);
// 初始化数据
onMounted(() => {
  getServerDomainFn();
});
</script>

<template>
  <div class="domain-container" v-loading="loading">
    <!-- 服务器域名配置 -->
    <ElCard class="domain-card">
      <template #header>
        <div class="card-header">
          <span>服务器域名配置</span>
          <ElButton
            type="primary"
            size="small"
            @click="updateServerDomain"
            :loading="loading"
          >
            保存配置
          </ElButton>
        </div>
      </template>

      <ElRow :gutter="20">
        <ElCol :span="12">
          <div class="domain-item">
            <label>request合法域名：</label>
            <div class="domain-input-group">
              <ElInput
                v-model="serverDomainInputs.requestDomain"
                type="textarea"
                :rows="4"
                placeholder="请输入域名，多个域名用换行分隔"
              />
              <div class="domain-tips">
                <p>• 域名必须是https，不能带端口号</p>
                <p>• 域名不能使用IP地址或localhost</p>
              </div>
            </div>
          </div>
        </ElCol>
        <ElCol :span="12">
          <div class="domain-item">
            <label>socket合法域名：</label>
            <div class="domain-input-group">
              <ElInput
                v-model="serverDomainInputs.wsRequestDomain"
                type="textarea"
                :rows="4"
                placeholder="请输入域名，多个域名用换行分隔"
              />
              <div class="domain-tips">
                <p>• 域名必须是wss，不能带端口号</p>
                <p>• 域名不能使用IP地址或localhost</p>
              </div>
            </div>
          </div>
        </ElCol>
      </ElRow>

      <ElRow :gutter="20">
        <ElCol :span="12">
          <div class="domain-item">
            <label>uploadFile合法域名：</label>
            <div class="domain-input-group">
              <ElInput
                v-model="serverDomainInputs.uploadDomain"
                type="textarea"
                :rows="4"
                placeholder="请输入域名，多个域名用换行分隔"
              />
              <div class="domain-tips">
                <p>• 域名必须是https，不能带端口号</p>
                <p>• 域名不能使用IP地址或localhost</p>
              </div>
            </div>
          </div>
        </ElCol>
        <ElCol :span="12">
          <div class="domain-item">
            <label>downloadFile合法域名：</label>
            <div class="domain-input-group">
              <ElInput
                v-model="serverDomainInputs.downloadDomain"
                type="textarea"
                :rows="4"
                placeholder="请输入域名，多个域名用换行分隔"
              />
              <div class="domain-tips">
                <p>• 域名必须是https，不能带端口号</p>
                <p>• 域名不能使用IP地址或localhost</p>
              </div>
            </div>
          </div>
        </ElCol>
      </ElRow>
    </ElCard>

    <!-- 业务域名配置 -->
    <ElCard class="domain-card" v-if="miniBaseInfo?.principal_type !== 0">
      <template #header>
        <div class="card-header">
          <span>业务域名配置</span>
          <ElButton
            type="primary"
            size="small"
            @click="updateBusinessDomain"
            :loading="loading"
          >
            保存配置
          </ElButton>
        </div>
      </template>

      <ElRow :gutter="20">
        <ElCol :span="24">
          <div class="domain-item">
            <label>webView合法域名：</label>
            <div class="domain-input-group">
              <ElInput
                v-model="businessDomainInputs.webViewDomain"
                type="textarea"
                :rows="4"
                placeholder="请输入域名，多个域名用换行分隔"
              />
              <div class="domain-tips">
                <p>• 域名必须是https，不能带端口号</p>
                <p>• 域名不能使用IP地址或localhost</p>
                <p>• 需要下载校验文件并上传到域名根目录</p>
              </div>
              <ElButton
                type="info"
                size="small"
                @click="downloadVerifyFile"
                :disabled="!businessDomainInputs.webViewDomain"
              >
                下载校验文件
              </ElButton>
            </div>
          </div>
        </ElCol>
      </ElRow>
    </ElCard>
  </div>
</template>

<style scoped>
.domain-container {
  padding: 20px 0;
}

.domain-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.domain-item {
  margin-bottom: 20px;
}

.domain-item label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  color: #606266;
}

.domain-input-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.domain-tips {
  padding: 12px;
  background-color: #f5f7fa;
  border-left: 4px solid #409eff;
  border-radius: 4px;
}

.domain-tips p {
  margin: 0 0 4px;
  font-size: 12px;
  line-height: 1.4;
  color: #606266;
}

.domain-tips p:last-child {
  margin-bottom: 0;
}
</style>
