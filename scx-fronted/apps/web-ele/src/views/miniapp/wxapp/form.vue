<script lang="ts" setup>
import { computed, defineAsyncComponent, reactive, ref } from 'vue';

import { ElButton, ElDrawer, ElMessage, ElTabPane, ElTabs } from 'element-plus';

import { getIcpEntrance } from '#/api/open/mini-icp';
import { getOpenMiniBase } from '#/api/open/open-mini-base';
import { addObj, editObj, getById } from '#/api/open/open-platform-app';

const emit = defineEmits(['initPage']);

const BasicInfoTab = defineAsyncComponent(
  () => import('./components/BasicInfoTab.vue'),
);

const DetailInfoTab = defineAsyncComponent(
  () => import('./components/DetailInfoTab.vue'),
);

const DomainTab = defineAsyncComponent(
  () => import('./components/DomainTab.vue'),
);

const AuthTab = defineAsyncComponent(() => import('./components/AuthTab.vue'));
const IcpTab = defineAsyncComponent(() => import('./components/IcpTab.vue'));
const state = reactive({
  form: {
    appId: '',
    appSecret: '',
    token: '',
    aesKey: '',
    appName: '',
    qrcodeUrl: '',
    shopId: '',
    componentStatus: '0',
    platformType: 'WX_MA',
    extraJson: '',
  },
});
const dialog = ref(false);
const loading = ref(false);
const basicRef = ref();
const isAdd = ref(false);
interface MiniBaseInfo {
  errcode: number;
  errmsg: string;
  appid: string;
  account_type: number;
  principal_type: number;
  principal_name: string;
  realname_status: number;
  wx_verify_info: {
    naming_verify: boolean;
    qualification_verify: boolean;
  };
  signature_info: {
    modify_quota: number;
    modify_used_count: number;
    signature: string;
  };
  head_image_info: {
    head_image_url: string;
    modify_quota: number;
    modify_used_count: number;
  };
  nickname: string;
  registered_country: number;
  nickname_info: {
    modify_quota: number;
    modify_used_count: number;
    nickname: string;
  };
  credential: string;
  customer_type: number;
}

interface MiniIcpEntrance {
  info: {
    status: number;
  };
}

const miniBaseInfo = ref<MiniBaseInfo | null>(null);
const miniIcpEntrance = ref<MiniIcpEntrance | null>(null);
const activeTab = ref('basic');

const domainLoading = ref(false);

const initForm = (row: any | undefined) => {
  dialog.value = true;
  isAdd.value = true;
  state.form.appId = row?.appId;
  if (row && row.appId) {
    getDetail(row.appId);
  }
};
const getDetail = (id: string) => {
  loading.value = true;
  getById(id).then((response) => {
    state.form = response;
    loading.value = false;
    isAdd.value = false;
    if (state.form.componentStatus === '1') {
      getOpenMiniBaseFn();
      getIcpEntranceFn();
    }
  });
};
/**
 * 关闭事件
 */
const handleClose = () => {
  dialog.value = false;
  resetForm();
};

/**
 * 抽屉关闭前事件
 */
const handleBeforeClose = (done: () => void) => {
  resetForm();
  done();
};
/**
 * 重置表单
 */
const resetForm = () => {
  // 重置表单数据
  state.form = {
    appId: '',
    appSecret: '',
    token: '',
    aesKey: '',
    appName: '',
    qrcodeUrl: '',
    shopId: '',
    componentStatus: '0',
    platformType: 'WX_MA',
    extraJson: '',
  };

  // 重置其他状态
  loading.value = false;
  isAdd.value = false;
  activeTab.value = 'basic';
  miniBaseInfo.value = null;
  miniIcpEntrance.value = null;
  domainLoading.value = false;
};
/**
 * 提交按钮
 */
const submitForm = async () => {
  loading.value = true;
  if (isAdd.value) {
    // 新增
    add();
  } else {
    // 修改
    edit();
  }
};
/**
 * 新增
 */
const add = async () => {
  addObj(state.form).then(() => {
    ElMessage.success('新增成功');
    handleClose();
    emit('initPage');
  });
};
/**
 * 修改
 */
const edit = async () => {
  editObj(state.form).then(() => {
    ElMessage.success('修改成功');
    handleClose();
    emit('initPage');
  });
};
const getOpenMiniBaseFn = async () => {
  const res = await getOpenMiniBase(state.form.appId);
  if (res) {
    miniBaseInfo.value = JSON.parse(res);
  }
};

// 计算属性：获取小程序基本信息
const miniBaseInfoData = computed(() => {
  if (!miniBaseInfo.value || Object.keys(miniBaseInfo.value).length === 0) {
    return null;
  }
  return miniBaseInfo.value;
});

// 计算属性：获取备案状态数据
const miniIcpEntranceData = computed(() => {
  if (
    !miniIcpEntrance.value ||
    Object.keys(miniIcpEntrance.value).length === 0
  ) {
    return null;
  }
  return miniIcpEntrance.value;
});

// 获取小程序备案状态
const getIcpEntranceFn = async () => {
  const res = await getIcpEntrance(state.form.appId);
  if (res) {
    miniIcpEntrance.value = res;
  }
};
defineExpose({
  initForm,
});
</script>
<template>
  <div class="system-add-user-container">
    <ElDrawer
      v-model="dialog"
      :title="!isAdd ? '修改微信应用' : '新增微信应用'"
      size="60%"
      :before-close="handleBeforeClose"
    >
      <ElTabs v-model="activeTab">
        <ElTabPane label="基本信息" name="basic">
          <BasicInfoTab
            ref="basicRef"
            :form="state.form"
            :is-add="isAdd"
            :loading="loading"
            @submit="submitForm"
          />
        </ElTabPane>

        <ElTabPane
          label="详细信息"
          name="mini-info"
          v-if="
            !isAdd && miniBaseInfoData && state.form.componentStatus === '1'
          "
        >
          <DetailInfoTab
            v-if="activeTab === 'mini-info'"
            :mini-base-info-data="miniBaseInfoData"
            :mini-icp-entrance-data="miniIcpEntranceData"
          />
        </ElTabPane>

        <ElTabPane
          label="域名管理"
          name="domain"
          v-if="!isAdd && state.form.componentStatus === '1'"
        >
          <DomainTab
            v-if="activeTab === 'domain'"
            :app-id="state.form.appId"
            :loading="domainLoading"
            :mini-base-info="miniBaseInfoData"
            @update:loading="(value) => (domainLoading = value)"
          />
        </ElTabPane>
        <ElTabPane
          label="认证"
          name="auth"
          v-if="
            !isAdd &&
            miniBaseInfoData &&
            !miniBaseInfoData.wx_verify_info?.qualification_verify &&
            state.form.componentStatus === '1'
          "
        >
          <AuthTab
            v-if="activeTab === 'auth'"
            :app-id="state.form.appId"
            :mini-base-info="miniBaseInfoData"
          />
        </ElTabPane>
        <ElTabPane
          label="备案"
          name="icp"
          v-if="!isAdd && state.form.componentStatus === '1'"
        >
          <IcpTab
            v-if="activeTab === 'icp'"
            :app-id="state.form.appId"
            :mini-base-info="miniBaseInfoData"
          />
        </ElTabPane>
      </ElTabs>
      <template #footer>
        <span class="dialog-footer">
          <ElButton @click="handleClose">关 闭</ElButton>
        </span>
      </template>
    </ElDrawer>
  </div>
</template>

<style scoped>
.mini-info-container {
  padding: 20px 0;
}

.mini-info-card {
  margin-bottom: 20px;
  overflow: hidden;
  border-radius: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.header-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: white;
  background: rgb(255 255 255 / 20%);
  border-radius: 8px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
}

.header-status {
  display: flex;
  align-items: center;
}

.info-section {
  padding: 24px;
  margin-bottom: 32px;
  background: #fafbfc;
  border: 1px solid #e8eaed;
  border-radius: 12px;
}

.section-title {
  position: relative;
  padding-bottom: 12px;
  margin-bottom: 20px;
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  border-bottom: 2px solid #e8eaed;
}

.section-title::before {
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 40px;
  height: 2px;
  content: '';
  background: linear-gradient(90deg, #667eea, #764ba2);
  border-radius: 1px;
}

.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 16px;
  background: white;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.info-item-three-segments {
  display: flex;
  gap: 16px;
  align-items: center;
}

.segment-one {
  flex: 0 0 120px;
  min-width: 120px;
}

.segment-two {
  flex: 1;
  font-size: 14px;
  color: #4a4a4a;
}

.segment-three {
  flex: 0 0 120px;
  padding: 4px 8px;
  font-size: 12px;
  color: #909399;
  text-align: right;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.status-success {
  font-weight: 500;
  color: #67c23a;
}

.status-error {
  font-weight: 500;
  color: #f56c6c;
}

.info-item:hover {
  box-shadow: 0 4px 12px rgb(0 0 0 / 10%);
  transform: translateY(-2px);
}

.info-label {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
}

.label-icon {
  width: 20px;
  font-size: 16px;
  text-align: center;
}

.label-text {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  white-space: nowrap;
}

.info-value {
  flex: 1;
  margin-left: 16px;
  font-size: 14px;
  line-height: 1.5;
  color: #4a4a4a;
  text-align: right;
  overflow-wrap: break-word;
}

.info-item .el-tag {
  margin-left: 8px;
}

.info-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
}

.modify-count {
  display: inline-block;
  width: fit-content;
  padding: 4px 8px;
  font-size: 12px;
  color: #909399;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.avatar-container {
  display: flex;
  align-items: center;
}

.domain-container {
  padding: 20px 0;
}

.domain-card {
  margin-bottom: 20px;
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

.dialog-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
