<script lang="ts" setup name="wxApp">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElSwitch,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { delObj, editObj, getPage, sync } from '#/api/open/template-info';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  queryParams: { templateId: '', userVersion: '', sourceMiniprogramAppid: '' },
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 10, // 每页显示多少条
    asc: '',
    desc: 'upload_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const syncLoading = ref(false);
const queryRef = ref();
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
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
  initPage();
};

const syncFn = () => {
  syncLoading.value = true;
  sync()
    .then(() => {
      ElMessage.success('同步模板成功');
      return initPage();
    })
    .finally(() => {
      syncLoading.value = false;
    });
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该模板，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id).then(() => {
      ElMessage.success('删除成功');
      initPage();
    });
  });
};
const statusChange = (item: any) => {
  editObj({ id: item.id, status: item.status });
  ElMessage.success('修改成功');
};
initPage();
</script>
<template>
  <div class="app-layout-container">
    <div class="app-layout-container-auto app-layout-container-view">
      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="模板ID" prop="templateId">
          <ElInput
            v-model="state.queryParams.templateId"
            clearable
            placeholder="请输入模板ID"
          />
        </ElFormItem>
        <ElFormItem label="版本号" prop="userVersion">
          <ElInput
            v-model="state.queryParams.userVersion"
            clearable
            placeholder="请输入版本号"
          />
        </ElFormItem>
        <ElFormItem label="开发小程序AppId" prop="sourceMiniprogramAppid">
          <ElInput
            v-model="state.queryParams.sourceMiniprogramAppid"
            clearable
            placeholder="请输入开发小程序AppId"
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
            v-access:code="'open:template-info:sync'"
            @click="syncFn"
            :loading="syncLoading"
            :icon="Refresh"
          >
            同步模板库
          </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="templateId" label="模板ID" />
        <ElTableColumn prop="userVersion" label="版本号" />
        <ElTableColumn prop="userVersion" label="版本描述" />
        <ElTableColumn prop="sourceMiniprogramAppid" label="开发小程序AppId" />
        <ElTableColumn prop="sourceMiniprogram" label="开发小程序名称" />
        <ElTableColumn prop="uploadTime" label="上传时间" />
        <ElTableColumn prop="status" label="状态">
          <template #default="scope">
            <ElSwitch
              v-model="scope.row.status"
              active-value="0"
              inactive-value="1"
              @change="statusChange"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="danger"
              @click="del(scope.row.id)"
              v-access:code="'open:template-info:del'"
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
    </div>
  </div>
</template>
