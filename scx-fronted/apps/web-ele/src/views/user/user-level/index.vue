<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElColorPicker,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import { delObj, getPage } from '#/api/user/level';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const Mission = defineAsyncComponent(() => import('../user-mission/index.vue'));

// 字典
const { status } = useDict('status');
const Form = defineAsyncComponent(() => import('./form.vue'));
const state = reactive({
  queryParams: {
    name: '',
    status: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: 'level',
    desc: '',
  },
  tableData: [],
});
const active = ref('config');
const queryRef = ref();
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();
const missionRef = ref();

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response) => {
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
  formRef.value.initForm(row);
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该等级配置，是否继续?', '提示', {
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
initPage();
</script>
<template>
  <div class="app-layout-container">
    <div class="app-layout-container-auto app-layout-container-view">
      <ElTabs v-model="active" class="demo-tabs">
        <ElTabPane label="等级配置" name="config" />
        <ElTabPane label="任务配置" name="mission" />
      </ElTabs>
      <div v-if="active === 'config'">
        <!-- 搜索 -->
        <ElForm
          :model="state.queryParams"
          ref="queryRef"
          :inline="true"
          v-show="showSearch"
        >
          <ElFormItem label="名称" prop="name">
            <ElInput
              v-model="state.queryParams.name"
              placeholder="请输入名称"
              clearable
            />
          </ElFormItem>
          <ElFormItem label="状态" prop="status">
            <ElSelect
              v-model="state.queryParams.status"
              style="width: 200px"
              placeholder="请选择状态"
              clearable
            >
              <ElOption
                v-for="item in status"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
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
              v-access:code="'user:userlevel:add'"
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
          <ElTableColumn prop="name" label="等级名称" width="200" />
          <ElTableColumn prop="level" label="等级" />
          <ElTableColumn prop="icon" label="等级图标">
            <template #default="scope">
              <ElImage
                v-if="scope.row.icon"
                :src="scope.row.icon"
                style="width: 40px; height: 40px"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn prop="bgPic" label="背景图">
            <template #default="scope">
              <ElImage
                v-if="scope.row.bgPic"
                :src="scope.row.bgPic"
                style="width: 100px; height: 40px"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn prop="bgPic" label="背景色">
            <template #default="scope">
              <ElColorPicker v-model="scope.row.bgColor" show-alpha disabled />
            </template>
          </ElTableColumn>

          <ElTableColumn prop="growthValue" label="成长值" />
          <ElTableColumn prop="discount" label="折扣（%）" />
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
                v-access:code="'user:userlevel:edit'"
                @click="edit(scope.row)"
                :icon="Edit"
              >
                修改
              </ElButton>
              <ElButton
                link
                type="danger"
                v-access:code="'user:userlevel:del'"
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
      </div>
      <Mission v-if="active === 'mission'" ref="missionRef" />
    </div>
  </div>
</template>
