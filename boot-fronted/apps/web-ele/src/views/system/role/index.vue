<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getPage } from '#/api/system/sboot-role';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const SelectShop = defineAsyncComponent(
  () => import('#/components/select-shop/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));
const RoleMenu = defineAsyncComponent(() => import('./rolemenu.vue'));
const state = reactive({
  queryParams: {
    type: '',
    roleName: '',
    roleCode: '',
    shopId: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();
const roleMenuRef = ref();
const queryRef = ref();
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
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
  ElMessageBox.confirm('此操作将删除该角色，是否继续?', '提示', {
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
const onAuth = (row: any) => {
  roleMenuRef.value.initRoleMenu(row.id);
};
const handleTypeChange = () => {
  state.queryParams.shopId = '';
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
        <ElFormItem label="角色名称" prop="roleName">
          <ElInput
            v-model="state.queryParams.roleName"
            clearable
            placeholder="请输入角色名称"
          />
        </ElFormItem>
        <ElFormItem label="角色编码" prop="roleCode">
          <ElInput
            v-model="state.queryParams.roleCode"
            clearable
            placeholder="请输入角色编码"
          />
        </ElFormItem>
        <ElFormItem label="角色类型" prop="type">
          <ElSelect
            v-model="state.queryParams.type"
            style="width: 200px"
            clearable
            placeholder="请选择角色类型"
            @change="handleTypeChange"
          >
            <ElOption value="0" label="普通角色" />
            <ElOption value="1" label="范围角色" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem
          label="权限范围"
          prop="shopId"
          v-if="state.queryParams.type === '1'"
        >
          <SelectShop v-model="state.queryParams.shopId" style="width: 200px" />
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
            v-access:code="'system:sysrole:add'"
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
      <!-- 新增/编辑表单 -->
      <Form ref="formRef" @init-page="initPage" />
      <RoleMenu ref="roleMenuRef" @init-page="initPage" />
      <!-- table列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="roleName" label="角色名称" />
        <ElTableColumn prop="roleCode" label="角色编码" />
        <ElTableColumn prop="type" label="角色类型">
          <template #default="scope">
            <ElTag v-if="scope.row.type === '0'" type="success">普通角色</ElTag>
            <ElTag v-if="scope.row.type === '1'" type="danger">范围角色</ElTag>
          </template>
        </ElTableColumn>

        <ElTableColumn prop="roleDesc" label="角色描述" />
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" align="center" fixed="right" width="300">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'system:sysrole:edit'"
              v-if="scope.row.roleCode !== 'ROLE_ADMIN'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'system:sysrole:del'"
              v-if="scope.row.roleCode !== 'ROLE_ADMIN'"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'system:sysrole:add'"
              @click="onAuth(scope.row)"
              :icon="Plus"
            >
              权限
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
