<script lang="ts" setup>
import type { FormInstance, UploadFile } from 'element-plus';

import type { Clue } from '#/api/adx';

import { onBeforeUnmount, onMounted, reactive, ref } from 'vue';

import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  getClues,
  getClueTaskStatus,
  saveClue,
  submitClues,
  updateClueStatus,
  updateClueStatusByFilter,
  uploadClues,
} from '#/api/adx';

defineOptions({ name: 'AdxClueReport' });

const current = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const uploading = ref(false);
const submitting = ref(false);
const rows = ref<Clue[]>([]);
const selected = ref<Clue[]>([]);
const dialog = ref(false);
const batchDialog = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
const task = ref<Record<string, any>>({ status: 'IDLE', pending: 0 });
const filters = reactive({
  cluePhone: '',
  clueStatus: undefined as number | undefined,
  dates: [] as string[],
  keyword: '',
  sourceType: undefined as number | undefined,
});
const form = reactive<Partial<Clue>>({});
const batch = reactive({
  dates: [dayjs().format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')] as [
    string,
    string,
  ],
  sourceStatus: 2,
});
let timer: ReturnType<typeof setInterval> | undefined;

function query() {
  return {
    cluePhone: filters.cluePhone || undefined,
    clueStatus: filters.clueStatus,
    current: current.value,
    endDate: filters.dates?.[1],
    keyword: filters.keyword || undefined,
    size: size.value,
    sourceType: filters.sourceType,
    startDate: filters.dates?.[0],
  };
}

async function load() {
  loading.value = true;
  try {
    const result = await getClues(query());
    rows.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

async function loadTask() {
  task.value = await getClueTaskStatus();
}

function reset() {
  Object.assign(filters, {
    cluePhone: '',
    clueStatus: undefined,
    dates: [],
    keyword: '',
    sourceType: undefined,
  });
  current.value = 1;
  load();
}

function openEditor(row?: Clue) {
  Object.keys(form).forEach((key) => delete (form as any)[key]);
  Object.assign(form, row || { clueStatus: 0, sourceType: 1 });
  dialog.value = true;
}

async function save() {
  if (!(await formRef.value?.validate())) return;
  saving.value = true;
  try {
    await saveClue(form);
    ElMessage.success('线索已保存');
    dialog.value = false;
    await Promise.all([load(), loadTask()]);
  } finally {
    saving.value = false;
  }
}

async function upload(file: UploadFile) {
  if (!file.raw) return;
  if (!/\.xlsx?$/i.test(file.name)) {
    ElMessage.warning('仅支持 xls 或 xlsx 文件');
    return;
  }
  uploading.value = true;
  try {
    const count = await uploadClues(file.raw);
    ElMessage.success(`导入完成，共新增 ${count} 条线索`);
    await Promise.all([load(), loadTask()]);
  } finally {
    uploading.value = false;
  }
}

async function resetSelected() {
  if (selected.value.length === 0) {
    ElMessage.warning('请先勾选线索');
    return;
  }
  await ElMessageBox.confirm(
    `确认将选中的 ${selected.value.length} 条线索重置为待上报？`,
    '批量重置',
    { type: 'warning' },
  );
  await updateClueStatus(
    selected.value.map((item) => item.id),
    0,
  );
  ElMessage.success('所选线索已重置为待上报');
  await Promise.all([load(), loadTask()]);
}

function openBatchFilter() {
  batch.sourceStatus = 2;
  batch.dates = filters.dates?.length
    ? ([...filters.dates] as [string, string])
    : [dayjs().format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')];
  batchDialog.value = true;
}

async function resetFiltered() {
  if (!batch.dates?.[0] || !batch.dates?.[1]) {
    ElMessage.warning('请选择日期范围');
    return;
  }
  await ElMessageBox.confirm(
    `确认将 ${batch.dates[0]} 至 ${batch.dates[1]}、原状态为“${statusName(batch.sourceStatus)}”的线索重置为待上报？`,
    '按条件批量重置',
    { type: 'warning' },
  );
  const count = await updateClueStatusByFilter({
    cluePhone: filters.cluePhone || undefined,
    endDate: batch.dates[1],
    keyword: filters.keyword || undefined,
    sourceStatus: batch.sourceStatus,
    sourceType: filters.sourceType,
    startDate: batch.dates[0],
    status: 0,
  });
  ElMessage.success(`批量修改成功，更新 ${count} 条`);
  batchDialog.value = false;
  await Promise.all([load(), loadTask()]);
}

async function submit() {
  submitting.value = true;
  try {
    const result = await submitClues();
    task.value = result;
    if (result.status === 'BLOCKED') ElMessage.warning(result.message);
    else ElMessage.success(result.message || '上报任务已启动');
  } finally {
    submitting.value = false;
  }
}

function statusTag(status: number) {
  if (status === 1) return 'success';
  if (status === 2) return 'danger';
  return 'warning';
}

function statusName(status: number) {
  if (status === 1) return '成功';
  if (status === 2) return '失败';
  return '待上报';
}

onMounted(async () => {
  await Promise.all([load(), loadTask()]);
  timer = setInterval(loadTask, 10_000);
});
onBeforeUnmount(() => timer && clearInterval(timer));
</script>

<template>
  <div class="clue-page">
    <header>
      <div>
        <p class="eyebrow">ADX · CLUE DELIVERY</p>
        <h1>线索上报</h1>
        <p>导入、核验并批量管理汽车之家线索上报状态。</p>
      </div>
      <div class="header-actions">
        <el-upload
          v-access:code="'adx:clue:write'"
          accept=".xls,.xlsx"
          action="#"
          :auto-upload="false"
          :on-change="upload"
          :show-file-list="false"
        >
          <el-button :loading="uploading">上传 Excel</el-button>
        </el-upload>
        <el-button
          v-access:code="'adx:clue:write'"
          type="primary"
          @click="openEditor()"
        >
          新增线索
        </el-button>
      </div>
    </header>

    <el-alert
      :closable="false"
      :title="`任务状态：${task.status || 'IDLE'} · 待上报 ${task.pending || 0} 条`"
      :type="
        task.status === 'BLOCKED' || task.status === 'FAILED'
          ? 'warning'
          : 'info'
      "
      :description="task.message"
      show-icon
    />

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" label-position="top">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="姓名或线索编号"
          />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input
            v-model="filters.cluePhone"
            clearable
            placeholder="手机号"
          />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="filters.sourceType" clearable>
            <el-option label="汽车之家" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.clueStatus" clearable>
            <el-option label="待上报" :value="0" />
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建日期">
          <el-date-picker
            v-model="filters.dates"
            end-placeholder="结束日期"
            range-separator="至"
            start-placeholder="开始日期"
            type="daterange"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label=" ">
          <el-button type="primary" :loading="loading" @click="load"
            >查询</el-button
          >
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="batch-actions">
        <el-button
          v-access:code="'adx:clue:write'"
          :disabled="selected.length === 0"
          @click="resetSelected"
          >所选重置为待上报</el-button
        >
        <el-button v-access:code="'adx:clue:write'" @click="openBatchFilter">
          按条件批量重置
        </el-button>
        <el-button
          v-access:code="'adx:clue:submit'"
          type="primary"
          :loading="submitting"
          @click="submit"
        >
          提交待上报线索
        </el-button>
      </div>
      <el-table
        v-loading="loading"
        :data="rows"
        border
        height="calc(100vh - 470px)"
        @selection-change="selected = $event"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column
          label="线索ID"
          min-width="170"
          prop="id"
          show-overflow-tooltip
        />
        <el-table-column
          label="三级子任务"
          min-width="140"
          prop="sonTaskId"
          show-overflow-tooltip
        />
        <el-table-column
          label="线索编号"
          min-width="140"
          prop="clueIndex"
          show-overflow-tooltip
        />
        <el-table-column label="姓名" min-width="110" prop="clueName" />
        <el-table-column label="手机号" min-width="130" prop="cluePhone" />
        <el-table-column label="来源" min-width="110">
          <template #default>汽车之家</template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.clueStatus)">{{
              statusName(row.clueStatus)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="城市ID" min-width="110" prop="clueCityId" />
        <el-table-column label="品牌ID" min-width="110" prop="clueBrandId" />
        <el-table-column
          label="车系ID"
          min-width="110"
          prop="clueCarSeriesId"
        />
        <el-table-column label="车型ID" min-width="110" prop="clueCarModelId" />
        <el-table-column label="上牌时间" min-width="140">
          <template #default="{ row }">{{
            row.firstCarRegistrationTime
              ? dayjs(row.firstCarRegistrationTime).format('YYYY-MM-DD')
              : '—'
          }}</template>
        </el-table-column>
        <el-table-column
          label="行驶里程(万公里)"
          min-width="150"
          prop="carMileage"
        />
        <el-table-column
          label="备注"
          min-width="220"
          prop="remark"
          show-overflow-tooltip
        />
        <el-table-column label="创建时间" min-width="170" prop="createTime">
          <template #default="{ row }">{{
            row.createTime
              ? dayjs(row.createTime).format('YYYY-MM-DD HH:mm:ss')
              : '—'
          }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="90">
          <template #default="{ row }"
            ><el-button
              v-access:code="'adx:clue:write'"
              link
              type="primary"
              @click="openEditor(row)"
              >编辑</el-button
            ></template
          >
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="load"
      />
    </el-card>

    <el-dialog
      v-model="dialog"
      :close-on-click-modal="false"
      :title="form.id ? '编辑线索' : '新增线索'"
      width="620px"
    >
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-form-item label="线索来源" required>
          <el-select v-model="form.sourceType"
            ><el-option label="汽车之家" :value="1"
          /></el-select>
        </el-form-item>
        <el-form-item label="状态" required>
          <el-select v-model="form.clueStatus">
            <el-option label="待上报" :value="0" /><el-option
              label="成功"
              :value="1"
            /><el-option label="失败" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名"
          ><el-input v-model="form.clueName"
        /></el-form-item>
        <el-form-item
          label="手机号"
          prop="cluePhone"
          :rules="[
            { required: true, message: '请输入手机号' },
            { pattern: /^1\d{10}$/, message: '手机号格式不正确' },
          ]"
        >
          <el-input v-model="form.cluePhone" maxlength="11" />
        </el-form-item>
        <el-form-item label="线索编号"
          ><el-input v-model="form.clueIndex"
        /></el-form-item>
        <el-form-item label="三级子任务"
          ><el-input v-model="form.sonTaskId"
        /></el-form-item>
        <div class="form-grid">
          <el-form-item label="城市ID"
            ><el-input v-model="form.clueCityId"
          /></el-form-item>
          <el-form-item label="区县ID"
            ><el-input v-model="form.clueAreaId"
          /></el-form-item>
          <el-form-item label="品牌ID"
            ><el-input v-model="form.clueBrandId"
          /></el-form-item>
          <el-form-item label="车系ID"
            ><el-input v-model="form.clueCarSeriesId"
          /></el-form-item>
          <el-form-item label="车型ID"
            ><el-input v-model="form.clueCarModelId"
          /></el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="上牌时间">
            <el-date-picker
              v-model="form.firstCarRegistrationTime"
              type="date"
              value-format="YYYY-MM-DDT00:00:00"
            />
          </el-form-item>
          <el-form-item label="行驶里程">
            <el-input-number
              v-model="form.carMileage"
              :min="0"
              :precision="2"
              :step="0.1"
            />
            <span>万公里</span>
          </el-form-item>
        </div>
        <el-form-item label="备注"
          ><el-input v-model="form.remark" :rows="3" type="textarea"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >确认</el-button
        ></template
      >
    </el-dialog>

    <el-dialog v-model="batchDialog" title="按条件批量重置" width="500px">
      <el-form label-width="100px">
        <el-form-item label="原状态" required>
          <el-select v-model="batch.sourceStatus">
            <el-option label="待上报" :value="0" />
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建日期" required>
          <el-date-picker
            v-model="batch.dates"
            end-placeholder="结束日期"
            range-separator="至"
            start-placeholder="开始日期"
            type="daterange"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialog = false">取消</el-button>
        <el-button type="primary" @click="resetFiltered">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.clue-page {
  min-height: 100%;
  padding: 20px;
  background: #f5f7fa;
}

header {
  display: flex;
  gap: 20px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}

header h1 {
  margin: 2px 0 6px;
  font-size: 24px;
  font-weight: 650;
  color: #172033;
}

header p {
  margin: 0;
  color: #667085;
}

.eyebrow {
  font-size: 11px;
  font-weight: 700;
  color: #2563eb !important;
  letter-spacing: 0.14em;
}

.header-actions,
.batch-actions {
  display: flex;
  gap: 10px;
}

.el-alert {
  margin-bottom: 14px;
}

.filter-card,
.table-card {
  border-color: #e6eaf0;
}

.filter-card {
  margin-bottom: 14px;
}

.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.filter-card :deep(.el-input),
.filter-card :deep(.el-select) {
  width: 160px;
}

.filter-card :deep(.el-date-editor) {
  width: 250px;
}

.batch-actions {
  margin-bottom: 14px;
}

.el-pagination {
  justify-content: flex-end;
  margin-top: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
}

.el-select {
  width: 100%;
}

@media (max-width: 768px) {
  .clue-page {
    padding: 12px;
  }

  header {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-card :deep(.el-form-item),
  .filter-card :deep(.el-input),
  .filter-card :deep(.el-select),
  .filter-card :deep(.el-date-editor) {
    width: 100%;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
