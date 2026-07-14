<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
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
} from 'element-plus';

import { delObj, getPage } from '#/api/user/mission';
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
const Form = defineAsyncComponent(() => import('./form.vue'));

// 字典
const { status, mission_type } = useDict('status', 'mission_type');
const state = reactive({
  queryParams: {
    name: '',
    status: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: '',
  },
  tableData: [],
});
const queryRef = ref();
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();

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
  formRef.value.initForm(row);
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该储值配置，是否继续?', '提示', {
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
  <div>
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
          v-access:code="'user:usermission:add'"
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
      <ElTableColumn prop="name" label="名称" width="180" />
      <ElTableColumn prop="type" label="类型" width="130">
        <template #default="scope">
          <DictTag :options="mission_type" :value="scope.row.type" />
        </template>
      </ElTableColumn>
      <ElTableColumn prop="icon" label="图标" width="120">
        <template #default="scope">
          <ElImage :src="scope.row.icon" style="width: 40px; height: 40px" />
        </template>
      </ElTableColumn>
      <ElTableColumn prop="dailyLimit" label="每日领取">
        <template #default="scope">
          <div v-if="scope.row.dailyLimit === '0'">无限制</div>
          <div v-if="scope.row.dailyLimit === '1'">
            每日限制领取
            <span style="color: red">{{ scope.row.dailyLimitNum }} </span>
            成长值
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="rewardNum" label="奖励">
        <template #default="scope">
          <div v-if="scope.row.type === '1'">
            订单每满
            <span style="color: red">{{ scope.row.count }}</span> 元，获得<span
              style="color: red"
              >{{ scope.row.rewardNum }}
            </span>
            成长值
          </div>
          <div v-if="scope.row.type === '2'">
            内容分享赠送
            <span style="color: red">{{ scope.row.rewardNum }} </span>
            成长值
          </div>
          <div v-if="scope.row.type === '3'">
            <span style="margin: 0 2px; color: red">{{ scope.row.count }}</span>
            积分兑换
            <span style="margin: 0 2px; color: red">{{
              scope.row.rewardNum
            }}</span>
            成长值
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="status" label="状态" width="150">
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
            v-access:code="'user:usermission:edit'"
            @click="edit(scope.row)"
            :icon="Edit"
          >
            修改
          </ElButton>
          <ElButton
            link
            type="danger"
            v-access:code="'user:usermission:del'"
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
</template>
