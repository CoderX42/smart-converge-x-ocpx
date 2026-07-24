<script lang="ts" setup>
import type { Media } from '#/api/ad';

import { reactive, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { getMedia, getUpstreamCustomers, saveUpstreamCustomer } from '#/api/ad';

import '../components/mvp-page.css';

defineOptions({ name: 'AdUpstreamCustomer' });

const media = ref<Media[]>([]);
const mediaId = ref('');
const keyword = ref('');
const status = ref('');
const rows = ref<any[]>([]);
const loading = ref(false);
const saving = ref(false);
const drawer = ref(false);
const current = ref(1);
const size = ref(20);
const total = ref(0);
const form = reactive<any>({});

async function bootstrap() {
  const result = await getMedia();
  media.value = result.records || [];
  mediaId.value = media.value[0]?.id || '';
}

async function load() {
  if (!mediaId.value) return;
  loading.value = true;
  try {
    const result = await getUpstreamCustomers({
      current: current.value,
      keyword: keyword.value,
      mediaId: mediaId.value,
      size: size.value,
      status: status.value,
    });
    rows.value = result.records || [];
    total.value = result.total || 0;
  } finally {
    loading.value = false;
  }
}

function open(row?: any) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(
    form,
    row || {
      code: `CUST-${Date.now()}`,
      mediaId: mediaId.value,
      status: 'ACTIVE',
    },
  );
  drawer.value = true;
}

async function submit() {
  if (!form.code || !form.name) {
    ElMessage.warning('请填写客户编码和名称');
    return;
  }
  saving.value = true;
  try {
    await saveUpstreamCustomer({ ...form, mediaId: mediaId.value });
    ElMessage.success('上游客户已保存');
    drawer.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

watch([mediaId, status], () => {
  current.value = 1;
  load();
});
bootstrap().then(load);
</script>

<template>
  <main class="mvp-page">
    <el-card class="mvp-card" shadow="never">
      <div class="mvp-toolbar">
        <div class="mvp-filters">
          <el-input
            v-model="keyword"
            clearable
            placeholder="名称或编码搜索"
            style="width: 240px"
            @keyup.enter="load"
          />
          <el-select
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 130px"
          >
            <el-option label="启用" value="ACTIVE" /><el-option
              label="禁用"
              value="DISABLED"
            />
          </el-select>
          <el-button @click="load">查询</el-button>
        </div>
        <el-button
          v-access:code="'ad:customer:write'"
          type="primary"
          @click="open()"
        >
          新增客户
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="编码" min-width="170">
          <template #default="{ row }">
            <span class="mvp-code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="客户名称" prop="name" min-width="180" />
        <el-table-column
          label="描述"
          prop="description"
          min-width="240"
          show-overflow-tooltip
        />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              effect="plain"
              :type="row.status === 'ACTIVE' ? 'success' : 'info'"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" prop="updateTime" min-width="175" />
        <el-table-column align="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-access:code="'ad:customer:write'"
              link
              type="primary"
              @click="open(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="mvp-empty">暂无上游客户</div>
        </template>
      </el-table>
      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          background
          layout="total, prev, pager, next"
          :total="total"
          @change="load"
        />
      </div>
    </el-card>
    <el-drawer
      v-model="drawer"
      :title="form.id ? '编辑上游客户' : '新增上游客户'"
      size="460px"
    >
      <el-form label-position="top" :model="form">
        <el-form-item label="客户编码" required>
          <el-input v-model="form.code" :disabled="Boolean(form.id)" />
        </el-form-item>
        <el-form-item label="客户名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="客户描述">
          <el-input
            v-model="form.description"
            :rows="4"
            maxlength="500"
            show-word-limit
            type="textarea"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button label="ACTIVE">启用</el-radio-button
            ><el-radio-button label="DISABLED"> 禁用 </el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button
        ><el-button :loading="saving" type="primary" @click="submit">
          保存
        </el-button>
      </template>
    </el-drawer>
  </main>
</template>
