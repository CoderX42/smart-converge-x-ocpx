<script setup lang="ts" name="selectArticle">
import type { TableInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElImage,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage } from '#/api/system/article';

const props = defineProps({
  limitNum: {
    type: Number,
    default: 1,
  },
  shopId: {
    type: String,
    default: '',
  },
});
const emit = defineEmits(['currentRow']);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const tableRef = ref<TableInstance>();
const state = reactive<any>({
  dialog: false,
  loading: false,
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 20, // 每页显示多少条
  },
  queryParams: {
    groupId: '',
  },
  tableData: [],
  currentList: [], // 选中的内容
  maxHeight: window.screen.availHeight - 500,
});
/**
 * 素材分页列表
 */
const initPage = () => {
  state.dialog = true;
  state.loading = true;
  getPage({
    current: state.page.currentPage,
    size: state.page.pageSize,
    shopId: props.shopId,
  })
    .then((response) => {
      state.loading = false;
      state.tableData = response.records;
      state.page.total = response.total;
    })
    .catch(() => {
      state.loading = false;
    });
};
/**
 * 选择内容
 */
const handleCurrentChange = (val: any) => {
  if (val && val.length > props.limitNum) {
    tableRef.value!.clearSelection();
    state.currentList = [];
    return ElMessage.error(`最多只能选择【${props.limitNum}】个文章`);
  }
  state.currentList = val;
};
const onSubmit = () => {
  if (state.currentList.length <= 0) {
    return ElMessage.error('请选择文章');
  }
  if (state.currentList.length > props.limitNum) {
    return ElMessage.error(`最多只能选择【${props.limitNum}】个文章`);
  }
  const message = `已选择 【${state.currentList[0].title}】等${
    state.currentList.length
  }个文章, 是否继续?`;
  ElMessageBox.confirm(message, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    emit('currentRow', state.currentList);
    state.dialog = false;
  });
};
defineExpose({
  initPage,
});
</script>
<template>
  <div>
    <ElDialog v-model="state.dialog" width="80%" title="文章选择器">
      <ElTable
        ref="tableRef"
        v-loading="state.loading"
        :data="state.tableData"
        highlight-current-row
        @selection-change="handleCurrentChange"
        :height="state.maxHeight"
      >
        <ElTableColumn type="selection" width="55" />
        <ElTableColumn
          prop="title"
          label="标题"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="coverUrl"
          label="封面"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            <ElImage
              style="width: 60px; height: 60px"
              :src="scope.row.coverUrl"
              :preview-src-list="[scope.row.coverUrl]"
              fit="cover"
              :preview-teleported="true"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="author" label="作者" align="center" />
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
      <template #footer>
        <span class="dialog-footer">
          <ElButton @click="state.dialog = false">关闭</ElButton>
          <ElButton type="primary" @click="onSubmit"> 确 认 </ElButton>
        </span>
      </template>
    </ElDialog>
  </div>
</template>
