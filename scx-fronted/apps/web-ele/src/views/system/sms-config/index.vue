<script lang="ts" setup>
import type { TabsPaneContext } from 'element-plus';

import { defineAsyncComponent, nextTick, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import { delObj, getPage } from '#/api/system/sms-config';
import { useDict } from '#/utils/dict';
import SmsSendRecord from '#/views/system/sms-send-record/index.vue';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

const Form = defineAsyncComponent(() => import('./form.vue'));
const { status, sms_supplier, sms_config_code } = useDict(
  'status',
  'sms_supplier',
  'sms_config_code',
);
const active = ref('config');
const state = reactive({
  queryParams: {
    templateId: '',
    signature: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();
const queryRef = ref();
const smsSendRecordRef = ref();
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response: any) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 新增按钮
 */
const add = () => {
  formRef.value.initForm();
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  formRef.value.initForm(row.id);
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该系统配置，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
  initPage();
};
const handleClick = (_tab: TabsPaneContext, _event: Event) => {
  if (active.value === 'send') {
    nextTick(() => {
      smsSendRecordRef.value?.initPage();
    });
  }
};
initPage();
</script>
<template>
  <div class="app-layout-container">
    <div class="app-layout-container-auto app-layout-container-view">
      <div>
        <ElTabs v-model="active" class="sms-tabs" @tab-click="handleClick">
          <ElTabPane label="短信配置" name="config">
            <!-- 搜索 -->
            <ElForm
              :model="state.queryParams"
              ref="queryRef"
              :inline="true"
              v-show="showSearch"
            >
              <ElFormItem label="短信签名" prop="signature">
                <ElInput
                  v-model="state.queryParams.signature"
                  clearable
                  placeholder="请输入短信签名"
                />
              </ElFormItem>
              <ElFormItem label="模板ID" prop="templateId">
                <ElInput
                  v-model="state.queryParams.templateId"
                  clearable
                  placeholder="请输入短信签名"
                />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" @click="initPage" :icon="Search">
                  搜索
                </ElButton>
                <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
              </ElFormItem>
            </ElForm>
            <!-- 工具栏 -->
            <div class="app-table-toolbar">
              <div>
                <ElButton
                  type="primary"
                  v-access:code="'system:sms-config:add'"
                  @click="add"
                  :icon="Plus"
                >
                  新增
                </ElButton>
              </div>
              <RightToolbar
                :search-btn="true"
                :refresh-btn="true"
                @search="showSearch = !showSearch"
                @refresh="initPage"
              />
            </div>
            <Form ref="formRef" @init-page="initPage" />
            <!-- 列表 -->
            <ElTable v-loading="loading" :data="state.tableData" border>
              <ElTableColumn prop="name" label="短信名称" />
              <ElTableColumn prop="smsCode" label="短信编码">
                <template #default="scope">
                  <DictTag
                    :options="sms_config_code"
                    :value="scope.row.smsCode"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn prop="supplier" label="厂商标识">
                <template #default="scope">
                  <DictTag
                    :options="sms_supplier"
                    :value="scope.row.supplier"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn prop="accessKeyId" label="accessKeyId" />
              <ElTableColumn prop="accessKeySecret" label="accessKeySecret" />
              <ElTableColumn prop="signature" label="短信签名" />
              <ElTableColumn prop="templateId" label="模板ID" />
              <ElTableColumn prop="status" label="状态">
                <template #default="scope">
                  <DictTag :options="status" :value="scope.row.status" />
                </template>
              </ElTableColumn>
              <ElTableColumn prop="createTime" label="创建时间" />
              <ElTableColumn label="操作" align="center" width="200">
                <template #default="scope">
                  <ElButton
                    link
                    type="primary"
                    v-access:code="'system:sms-config:edit'"
                    @click="edit(scope.row)"
                    :icon="Edit"
                  >
                    修改
                  </ElButton>
                  <ElButton
                    link
                    type="danger"
                    v-access:code="'system:sms-config:del'"
                    @click="del(scope.row.id)"
                    :icon="Delete"
                  >
                    删除
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
            <!-- 分页 -->
            <Pagination
              :total="state.page.total"
              v-model:current="state.page.currentPage"
              v-model:size="state.page.pageSize"
              @change="initPage"
            />
          </ElTabPane>
          <ElTabPane label="发送记录" name="send">
            <SmsSendRecord ref="smsSendRecordRef" />
          </ElTabPane>
        </ElTabs>
      </div>
    </div>
  </div>
</template>
