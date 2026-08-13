<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { AdvertiserV2, Media, MonitorTask, PromotionLink } from '#/api/ocpx';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  archiveMonitorTask,
  changeMonitorTaskStatus,
  createMonitorTask,
  deleteMonitorTask,
  getMonitorTask,
  listAdvertisers,
  listMedia,
  listMonitorTasks,
  listPromotionLinks,
  updateMonitorTask,
} from '#/api/ocpx';

defineOptions({ name: 'OcpxMonitorTaskManagement' });

const platformMap: Record<PromotionLink['platform'], string> = {
  ANDROID: 'Android',
  IOS: 'IOS',
  OTHER: '其他',
  WEB: 'Web',
};

const rows = ref<MonitorTask[]>([]);
const advertisers = ref<AdvertiserV2[]>([]);
const links = ref<PromotionLink[]>([]);
const media = ref<Media[]>([]);
const monitorOptions = ref<MonitorTask[]>([]);
const loading = ref(false);
const total = ref(0);
const open = ref(false);
const editing = ref<MonitorTask>();
const submitLoading = ref(false);
const formRef = ref<FormInstance>();
const query = reactive({
  current: 1,
  mediaId: '',
  size: 20,
  status: '',
  taskId: '',
});
const form = reactive({
  advertiserId: '',
  bizId: '',
  mediaId: '',
  operatorUserId: '',
  promotionLinkId: '',
  remark: '',
  taskCode: '',
  taskName: '',
  unitPrice: '',
});

const availableLinks = computed(() =>
  links.value.filter((item) => item.advertiserId === form.advertiserId),
);

function validateUnitPrice(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
) {
  if (value === '' || value === undefined || value === null) {
    callback(new Error('请输入投放单价'));
    return;
  }
  if (/^\d+(?:\.\d+)?$/.test(String(value))) {
    callback();
  } else {
    callback(new Error('请输入正确的数字格式'));
  }
}

const rules: FormRules = {
  advertiserId: [{ message: '请选择广告主', required: true, trigger: 'blur' }],
  mediaId: [{ message: '请选择渠道', required: true, trigger: 'blur' }],
  promotionLinkId: [
    { message: '请选择投放链接', required: true, trigger: 'blur' },
  ],
  taskCode: [{ message: '请输入任务编码', required: true, trigger: 'blur' }],
  taskName: [{ message: '请输入监测名称', required: true, trigger: 'blur' }],
  unitPrice: [
    { message: '请输入投放单价', required: true, trigger: 'blur' },
    { trigger: 'blur', validator: validateUnitPrice },
  ],
};

async function load() {
  loading.value = true;
  try {
    if (query.taskId) {
      const item = await getMonitorTask(query.taskId);
      rows.value = [item];
      total.value = 1;
      return;
    }
    const result = await listMonitorTasks({
      current: query.current,
      mediaId: query.mediaId || undefined,
      size: query.size,
      status: query.status || undefined,
    });
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

async function loadMonitorOptions() {
  const result = await listMonitorTasks({
    current: 1,
    mediaId: query.mediaId || undefined,
    size: 500,
  });
  monitorOptions.value = result.records;
}

async function loadOptions() {
  const [advertiserPage, linkPage, mediaPage] = await Promise.all([
    listAdvertisers({ current: 1, size: 500, status: 'ACTIVE' }),
    listPromotionLinks({ current: 1, size: 500, status: 'ACTIVE' }),
    listMedia({ current: 1, size: 500, status: 'ACTIVE' }),
  ]);
  advertisers.value = advertiserPage.records;
  links.value = linkPage.records;
  media.value = mediaPage.records;
}

function toQuery() {
  query.current = 1;
  load();
}

function handleMediaChange() {
  query.taskId = '';
  loadMonitorOptions();
}

function linkPlatform(row: MonitorTask) {
  const link = links.value.find((item) => item.id === row.promotionLinkId);
  return link ? platformMap[link.platform] : '-';
}

function edit(row?: MonitorTask) {
  editing.value = row;
  Object.assign(form, {
    advertiserId: row?.advertiserId || '',
    bizId: row?.bizId || '',
    mediaId: row?.mediaId || '',
    operatorUserId: row?.operatorUserId || '',
    promotionLinkId: '',
    remark: row?.remark || '',
    taskCode: row?.taskCode || '',
    taskName: row?.taskName || '',
    unitPrice: row ? String(row.unitPrice) : '',
  });
  open.value = true;
  // 平移 SGYZ form.vue：先置空投放链接，nextTick 再回填，避免级联变更把值清掉
  if (row) {
    nextTick(() => {
      form.promotionLinkId = row.promotionLinkId;
    });
  }
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  submitLoading.value = true;
  try {
    const data = {
      advertiserId: form.advertiserId,
      bizId: form.bizId || undefined,
      mediaId: form.mediaId,
      operatorUserId: form.operatorUserId || undefined,
      promotionLinkId: form.promotionLinkId,
      remark: form.remark || undefined,
      taskName: form.taskName,
      unitPrice: Number(form.unitPrice),
    };
    if (editing.value) {
      await updateMonitorTask(editing.value.id, {
        ...data,
        expectedVersion: editing.value.configVersion,
      });
      ElMessage.success('编辑成功');
    } else {
      await createMonitorTask({ ...data, taskCode: form.taskCode });
      ElMessage.success('新增成功');
    }
    open.value = false;
    await load();
    await loadMonitorOptions();
  } finally {
    submitLoading.value = false;
  }
}

async function toggle(row: MonitorTask) {
  await changeMonitorTaskStatus(row.id, {
    expectedVersion: row.configVersion,
    status: row.status === 'PAUSED' ? 'ACTIVE' : 'PAUSED',
  });
  await load();
}

async function handleCopy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('复制成功');
  } catch {
    ElMessage.error('浏览器不支持复制功能');
  }
}

async function archive(row: MonitorTask) {
  await ElMessageBox.confirm('确认归档该监测任务？', '归档监测任务');
  await archiveMonitorTask(row.id, { expectedVersion: row.configVersion });
  await load();
  await loadMonitorOptions();
}

// 平移 SGYZ advertise/flow：确认后删除，成功「删除成功!」、失败「删除异常!」
async function handleDelete(row: MonitorTask) {
  await ElMessageBox.confirm('此操作将永久删除, 是否继续?', '提示', {
    cancelButtonText: '取消',
    confirmButtonText: '确定',
    type: 'warning',
  });
  try {
    await deleteMonitorTask(row.id);
  } catch {
    ElMessage.error('删除异常!');
    return;
  }
  ElMessage.success('删除成功!');
  await load();
  await loadMonitorOptions();
}

function handleDialogClose() {
  editing.value = undefined;
  formRef.value?.resetFields();
  Object.assign(form, {
    advertiserId: '',
    bizId: '',
    mediaId: '',
    operatorUserId: '',
    promotionLinkId: '',
    remark: '',
    taskCode: '',
    taskName: '',
    unitPrice: '',
  });
}

onMounted(async () => {
  await loadOptions();
  await loadMonitorOptions();
  await load();
});
</script>

<template>
  <Page auto-content-height title="监测任务">
    <el-card shadow="never">
      <div class="mb-4 flex gap-3">
        <el-select
          v-model="query.mediaId"
          clearable
          filterable
          placeholder="渠道"
          @change="handleMediaChange"
        >
          <el-option
            v-for="item in media"
            :key="item.id"
            :label="item.mediaName"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="query.taskId"
          clearable
          filterable
          placeholder="监测链接"
        >
          <el-option
            v-for="item in monitorOptions"
            :key="item.id"
            :label="item.taskName"
            :value="item.id"
          />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="状态">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="暂停" value="PAUSED" />
        </el-select>
        <el-button type="primary" @click="toQuery">搜索</el-button>
        <el-button
          v-access:code="'ocpx:monitor-task:add'"
          type="primary"
          @click="edit()"
        >
          新增
        </el-button>
      </div>
      <el-alert
        class="mb-4"
        :closable="false"
        title="任务直接绑定一个广告主、一个推广链接和一个媒体；启用后 Feedback、Callback 即按此关系转发。"
        type="info"
      />
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="监测编号" min-width="140" prop="taskCode" />
        <el-table-column label="渠道编号" min-width="120" prop="mediaName" />
        <el-table-column
          label="应用链接名称"
          min-width="140"
          prop="promotionLinkName"
        />
        <el-table-column
          label="广告主名称"
          min-width="120"
          prop="advertiserName"
        />
        <el-table-column label="业务编号" min-width="110" prop="bizId" />
        <el-table-column label="平台" width="90">
          <template #default="{ row }">{{ linkPlatform(row) }}</template>
        </el-table-column>
        <el-table-column label="投放单价" width="100" prop="unitPrice" />
        <el-table-column label="展示监测链接" min-width="220">
          <template #default="{ row }">
            <el-tooltip
              :content="row.showUrl"
              effect="dark"
              placement="top-start"
            >
              <span class="url-copy" @click="handleCopy(row.showUrl)">
                {{ row.showUrl }}
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="点击监测链接" min-width="220">
          <template #default="{ row }">
            <el-tooltip
              :content="row.clkUrl"
              effect="dark"
              placement="top-start"
            >
              <span class="url-copy" @click="handleCopy(row.clkUrl)">
                {{ row.clkUrl }}
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120" prop="remark" />
        <el-table-column label="状态" width="80" prop="status" />
        <el-table-column label="创建时间" width="170" prop="createTime" />
        <el-table-column label="修改时间" width="170" prop="updateTime" />
        <el-table-column fixed="right" label="操作" width="260">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'ARCHIVED'"
              v-access:code="'ocpx:monitor-task:edit'"
              link
              type="primary"
              @click="edit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status !== 'ARCHIVED'"
              v-access:code="'ocpx:monitor-task:status'"
              link
              @click="toggle(row)"
            >
              {{ row.status === 'PAUSED' ? '恢复' : '暂停' }}
            </el-button>
            <el-button
              v-if="row.status !== 'ARCHIVED'"
              v-access:code="'ocpx:monitor-task:archive'"
              link
              type="danger"
              @click="archive(row)"
            >
              归档
            </el-button>
            <el-button
              v-access:code="'ocpx:monitor-task:delete'"
              link
              type="danger"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="mt-4"
        :total="total"
        @change="load"
      />
    </el-card>

    <el-dialog
      v-model="open"
      :close-on-click-modal="false"
      :title="editing ? '投放链接 编辑' : '投放链接 新增'"
      width="800px"
      @close="handleDialogClose"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务编码" prop="taskCode">
          <el-input
            v-model="form.taskCode"
            :disabled="!!editing"
            placeholder="请输入任务编码"
          />
        </el-form-item>
        <el-form-item label="所属渠道" prop="mediaId">
          <el-select v-model="form.mediaId" filterable>
            <el-option
              v-for="item in media"
              :key="item.id"
              :label="item.mediaName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属广告主" prop="advertiserId">
          <el-select
            v-model="form.advertiserId"
            filterable
            @change="form.promotionLinkId = ''"
          >
            <el-option
              v-for="item in advertisers"
              :key="item.id"
              :label="item.advertiserName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="投放链接" prop="promotionLinkId">
          <el-select v-model="form.promotionLinkId" filterable>
            <el-option
              v-for="item in availableLinks"
              :key="item.id"
              :label="item.linkName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="监测名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="请输入监测名称" />
        </el-form-item>
        <el-form-item label="业务编号" prop="bizId">
          <el-input v-model="form.bizId" placeholder="请输入业务编号" />
        </el-form-item>
        <el-form-item label="投放单价" prop="unitPrice">
          <el-input v-model="form.unitPrice" placeholder="请输入投放单价" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            autosize
            placeholder="请输入备注"
            type="textarea"
          />
        </el-form-item>
        <el-form-item label="运营人员 ID" prop="operatorUserId">
          <el-input v-model="form.operatorUserId" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button :loading="submitLoading" type="primary" @click="save">
          确定
        </el-button>
      </template>
    </el-dialog>
  </Page>
</template>

<style scoped>
.url-copy {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}
</style>
