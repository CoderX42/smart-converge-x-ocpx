<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type {
  EntityOption,
  RtaDecision,
  RtaMetric,
  RtaProfile,
  RtaProfilePayload,
} from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import {
  createRtaProfile,
  getRtaTaskConfig,
  listRtaDecisions,
  listRtaMetrics,
  listRtaProfiles,
  monitorTaskOptions,
  updateRtaProfile,
  updateRtaTaskConfig,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import { allColumnKeys, PAGE_SIZES } from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxRtaManagement' });

const { hasAccessByCodes } = useAccess();
const canViewPage = computed(() => hasAccessByCodes(['ocpx:rta:view']));
const canAdd = computed(() => hasAccessByCodes(['ocpx:rta:add']));
const canEdit = computed(() => hasAccessByCodes(['ocpx:rta:edit']));
const canBind = computed(() => hasAccessByCodes(['ocpx:rta:bind']));
const canViewDecisions = computed(() =>
  hasAccessByCodes(['ocpx:rta:decision:view']),
);
const canViewReports = computed(() => hasAccessByCodes(['ocpx:rta:report']));

const profileColumns = [
  { key: 'code', label: 'Profile 编号' },
  { key: 'name', label: '名称' },
  { key: 'adapterCode', label: '适配器' },
  { key: 'status', label: '状态' },
  { key: 'configJson', label: 'Mock 配置' },
  { key: 'updateTime', label: '更新时间' },
];
const visibleProfileColumns = ref(allColumnKeys(profileColumns));
const profiles = ref<RtaProfile[]>([]);
const profileTotal = ref(0);
const profileLoading = ref(false);
const profileError = ref('');
const profileQuery = reactive({ current: 1, keyword: '', size: 30 });

const taskOptions = ref<EntityOption[]>([]);
const taskLoading = ref(false);
const selectedTaskId = ref('');
const taskEnabled = ref(false);
const selectedProfileId = ref('');
const bindings = ref<
  Array<{ externalProductId: string; externalTaskId: string }>
>([]);
const taskError = ref('');
const taskSaving = ref(false);

const decisionColumns = [
  { key: 'receivedAt', label: '接收时间' },
  { key: 'requestId', label: '请求 ID' },
  { key: 'monitorTaskId', label: '监测任务' },
  { key: 'profileName', label: 'Profile' },
  { key: 'decision', label: '决策' },
  { key: 'reasonCode', label: '原因' },
  { key: 'latencyMs', label: '耗时(ms)' },
];
const visibleDecisionColumns = ref(allColumnKeys(decisionColumns));
const decisions = ref<RtaDecision[]>([]);
const decisionTotal = ref(0);
const decisionLoading = ref(false);
const decisionError = ref('');
const decisionCurrent = ref(1);
const decisionSize = ref(30);

const metrics = ref<RtaMetric[]>([]);
const metricLoading = ref(false);
const metricDataAsOf = ref('');
const metricError = ref('');

const dateRange = ref<[string, string]>([
  dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
]);
const grain = ref<'DAY' | 'HOUR'>('DAY');

const profileDialogOpen = ref(false);
const profileFormRef = ref<FormInstance>();
const editingProfile = ref<RtaProfile>();
const profileForm = reactive<RtaProfilePayload>({
  adapterCode: 'MOCK',
  code: '',
  configJson: '{"decision":"DENY"}',
  name: '',
  status: 'DISABLED',
});
const profileSaving = ref(false);
const profileRules: FormRules = {
  code: [{ message: '请输入 Profile 编号', required: true, trigger: 'blur' }],
  configJson: [{ message: '请输入合法 JSON', required: true, trigger: 'blur' }],
  name: [{ message: '请输入 Profile 名称', required: true, trigger: 'blur' }],
};

function hasProfileColumn(key: string) {
  return visibleProfileColumns.value.includes(key);
}

function hasDecisionColumn(key: string) {
  return visibleDecisionColumns.value.includes(key);
}

function profileStatus(status: RtaProfile['status']) {
  return status === 'ACTIVE' ? '启用' : '停用';
}

function decisionTag(decision: RtaDecision['decision']) {
  return decision === 'ALLOW' ? 'success' : 'danger';
}

function rate(value: null | number) {
  return value === null || value === undefined
    ? '--'
    : `${(value * 100).toFixed(2)}%`;
}

async function loadProfiles() {
  profileLoading.value = true;
  profileError.value = '';
  try {
    const result = await listRtaProfiles(profileQuery);
    profiles.value = result.records;
    profileTotal.value = result.total;
  } catch (error) {
    profileError.value =
      error instanceof Error ? error.message : 'Profile 加载失败';
  } finally {
    profileLoading.value = false;
  }
}

async function loadTasks() {
  taskLoading.value = true;
  taskError.value = '';
  try {
    taskOptions.value = await monitorTaskOptions();
  } catch (error) {
    taskError.value =
      error instanceof Error ? error.message : '监测任务加载失败';
  } finally {
    taskLoading.value = false;
  }
}

async function loadTaskConfig() {
  if (!selectedTaskId.value) {
    taskEnabled.value = false;
    selectedProfileId.value = '';
    bindings.value = [];
    return;
  }
  taskError.value = '';
  try {
    const result = await getRtaTaskConfig(selectedTaskId.value);
    taskEnabled.value = result.enabled;
    selectedProfileId.value = result.profileId || '';
    bindings.value = result.bindings.map((item) => ({
      externalProductId: item.externalProductId || '',
      externalTaskId: item.externalTaskId,
    }));
  } catch (error) {
    taskError.value =
      error instanceof Error ? error.message : '任务配置加载失败';
  }
}

function addBinding() {
  bindings.value.push({ externalProductId: '', externalTaskId: '' });
}

function removeBinding(index: number) {
  bindings.value.splice(index, 1);
}

async function saveTaskConfig() {
  if (!selectedTaskId.value) return;
  if (
    taskEnabled.value &&
    (!selectedProfileId.value || bindings.value.length === 0)
  ) {
    ElMessage.warning('启用 RTA 前必须选择 Profile 并绑定至少一个外部任务');
    return;
  }
  if (bindings.value.some((item) => !item.externalTaskId.trim())) {
    ElMessage.warning('外部任务 ID 不能为空');
    return;
  }
  taskSaving.value = true;
  try {
    await updateRtaTaskConfig(selectedTaskId.value, {
      bindings: bindings.value.map((item) => ({
        externalProductId: item.externalProductId.trim() || undefined,
        externalTaskId: item.externalTaskId.trim(),
      })),
      enabled: taskEnabled.value,
      profileId: selectedProfileId.value || undefined,
    });
    ElMessage.success('RTA 任务配置已保存');
    await loadTaskConfig();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    taskSaving.value = false;
  }
}

function openProfile(profile?: RtaProfile) {
  editingProfile.value = profile;
  Object.assign(
    profileForm,
    profile
      ? {
          adapterCode: 'MOCK',
          code: profile.code,
          configJson: profile.configJson,
          name: profile.name,
          status: profile.status,
        }
      : {
          adapterCode: 'MOCK',
          code: '',
          configJson: '{"decision":"DENY"}',
          name: '',
          status: 'DISABLED',
        },
  );
  profileDialogOpen.value = true;
}

async function saveProfile() {
  if (!profileFormRef.value) return;
  const valid = await profileFormRef.value.validate().catch(() => false);
  if (!valid) return;
  try {
    JSON.parse(profileForm.configJson);
  } catch {
    ElMessage.error('Mock 配置必须是合法 JSON');
    return;
  }
  profileSaving.value = true;
  try {
    await (editingProfile.value
      ? updateRtaProfile(editingProfile.value.id, profileForm)
      : createRtaProfile(profileForm));
    profileDialogOpen.value = false;
    ElMessage.success('Profile 已保存');
    await loadProfiles();
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : 'Profile 保存失败',
    );
  } finally {
    profileSaving.value = false;
  }
}

function queryParams() {
  return {
    endDate: dateRange.value[1],
    grain: grain.value,
    monitorTaskId: selectedTaskId.value || undefined,
    size: 30,
    startDate: dateRange.value[0],
  } as const;
}

async function loadDecisions() {
  if (!canViewDecisions.value) return;
  decisionLoading.value = true;
  decisionError.value = '';
  try {
    const result = await listRtaDecisions({
      ...queryParams(),
      current: decisionCurrent.value,
      size: decisionSize.value,
    });
    decisions.value = result.records;
    decisionTotal.value = result.total;
  } catch (error) {
    decisionError.value =
      error instanceof Error ? error.message : 'RTA 决策加载失败';
  } finally {
    decisionLoading.value = false;
  }
}

async function loadMetrics() {
  if (!canViewReports.value) return;
  metricLoading.value = true;
  metricError.value = '';
  try {
    const result = await listRtaMetrics(queryParams());
    metrics.value = result.records;
    metricDataAsOf.value = result.dataAsOf || '';
  } catch (error) {
    metricError.value =
      error instanceof Error ? error.message : 'RTA 统计加载失败';
  } finally {
    metricLoading.value = false;
  }
}

async function refresh() {
  await Promise.all([loadProfiles(), loadDecisions(), loadMetrics()]);
}

onMounted(async () => {
  if (!canViewPage.value) return;
  await Promise.all([loadProfiles(), loadTasks()]);
  await refresh();
});
</script>

<template>
  <Page auto-content-height title="RTA 管理">
    <el-alert
      v-if="!canViewPage"
      :closable="false"
      show-icon
      title="当前账号没有 RTA 管理权限"
      type="warning"
    />
    <el-alert
      v-else
      class="rta-boundary"
      show-icon
      title="当前仅支持 Mock 适配器；生产默认关闭。RTA 命中率是 RTA 决策命中率，不等同于 Callback 转化率。"
      type="info"
    />

    <el-card v-if="canViewPage" shadow="never" class="rta-card">
      <template #header>
        <div class="rta-card-header">
          <strong>RTA Profile</strong>
          <el-button
            v-access:code="'ocpx:rta:add'"
            :disabled="!canAdd"
            type="primary"
            @click="openProfile()"
          >
            新增 Profile
          </el-button>
        </div>
      </template>
      <div class="rta-query">
        <el-input
          v-model="profileQuery.keyword"
          clearable
          placeholder="按编号或名称搜索"
          @keyup.enter="loadProfiles"
        />
        <el-button type="primary" @click="loadProfiles">搜索</el-button>
        <RightToolbar
          v-model:visible-columns="visibleProfileColumns"
          :columns="profileColumns"
          :loading="profileLoading"
          refresh-btn
          @refresh="loadProfiles"
        />
      </div>
      <el-alert
        v-if="profileError"
        :title="profileError"
        type="error"
        show-icon
      />
      <el-table
        v-loading="profileLoading"
        class="ocpx-table"
        :data="profiles"
        highlight-current-row
        max-height="300"
        size="small"
      >
        <el-table-column
          v-if="hasProfileColumn('code')"
          label="Profile 编号"
          prop="code"
          min-width="150"
        />
        <el-table-column
          v-if="hasProfileColumn('name')"
          label="名称"
          prop="name"
          min-width="150"
        />
        <el-table-column
          v-if="hasProfileColumn('adapterCode')"
          label="适配器"
          prop="adapterCode"
          width="100"
        />
        <el-table-column
          v-if="hasProfileColumn('status')"
          label="状态"
          width="90"
        >
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{
              profileStatus(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasProfileColumn('configJson')"
          label="Mock 配置"
          min-width="220"
        >
          <template #default="{ row }"
            ><code>{{ row.configJson }}</code></template
          >
        </el-table-column>
        <el-table-column
          v-if="hasProfileColumn('updateTime')"
          label="更新时间"
          prop="updateTime"
          min-width="170"
        />
        <el-table-column v-if="canEdit" fixed="right" label="操作" width="90">
          <template #default="{ row }">
            <el-button
              v-access:code="'ocpx:rta:edit'"
              link
              type="primary"
              @click="openProfile(row)"
              >编辑</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!profileLoading && !profileError && profiles.length === 0"
        description="暂无 RTA Profile"
      />
      <el-pagination
        v-model:current-page="profileQuery.current"
        v-model:page-size="profileQuery.size"
        class="ocpx-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="PAGE_SIZES"
        :total="profileTotal"
        @change="loadProfiles"
      />
    </el-card>

    <el-card v-if="canViewPage" shadow="never" class="rta-card">
      <template #header
        ><strong>监测任务 RTA 开关与外部任务绑定</strong></template
      >
      <el-form label-width="110px" class="rta-task-form">
        <el-form-item label="监测任务">
          <el-select
            v-model="selectedTaskId"
            filterable
            clearable
            :loading="taskLoading"
            placeholder="请选择监测任务"
            @change="loadTaskConfig"
          >
            <el-option
              v-for="item in taskOptions"
              :key="item.id"
              :label="`${item.name} (${item.id})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <template v-if="selectedTaskId">
          <el-form-item label="RTA 开关">
            <el-switch
              v-model="taskEnabled"
              :disabled="!canBind"
              active-text="启用"
              inactive-text="关闭"
            />
            <span class="rta-help"
              >启用前必须绑定 Profile 和至少一个外部任务</span
            >
          </el-form-item>
          <el-form-item label="Profile">
            <el-select
              v-model="selectedProfileId"
              filterable
              clearable
              :disabled="!canBind"
              placeholder="请选择 ACTIVE Profile"
            >
              <el-option
                v-for="profile in profiles.filter(
                  (item) => item.status === 'ACTIVE',
                )"
                :key="profile.id"
                :label="`${profile.name} (${profile.code})`"
                :value="profile.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="外部任务绑定">
            <div class="rta-bindings">
              <div
                v-for="(binding, index) in bindings"
                :key="index"
                class="rta-binding-row"
              >
                <el-input
                  v-model="binding.externalTaskId"
                  :disabled="!canBind"
                  placeholder="外部任务 ID"
                />
                <el-input
                  v-model="binding.externalProductId"
                  :disabled="!canBind"
                  placeholder="外部产品 ID（可选）"
                />
                <el-button
                  :disabled="!canBind"
                  text
                  type="danger"
                  @click="removeBinding(index)"
                  >移除</el-button
                >
              </div>
              <el-button
                :disabled="!canBind || bindings.length >= 100"
                text
                type="primary"
                @click="addBinding"
                >+ 添加外部任务</el-button
              >
            </div>
          </el-form-item>
          <el-form-item>
            <el-button
              v-access:code="'ocpx:rta:bind'"
              :disabled="!canBind"
              :loading="taskSaving"
              type="primary"
              @click="saveTaskConfig"
              >保存任务配置</el-button
            >
          </el-form-item>
        </template>
        <el-alert v-if="taskError" :title="taskError" type="error" show-icon />
        <el-empty
          v-if="!selectedTaskId"
          description="选择监测任务后编辑 RTA 绑定"
        />
      </el-form>
    </el-card>

    <el-card v-if="canViewDecisions" shadow="never" class="rta-card">
      <template #header>
        <div class="rta-card-header">
          <strong>RTA 决策记录</strong
          ><el-button @click="loadDecisions">刷新</el-button>
        </div>
      </template>
      <div class="rta-query">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
        <el-select v-model="grain" @change="loadMetrics"
          ><el-option label="按天" value="DAY" /><el-option
            label="按小时"
            value="HOUR"
        /></el-select>
        <RightToolbar
          v-model:visible-columns="visibleDecisionColumns"
          :columns="decisionColumns"
          :loading="decisionLoading"
          refresh-btn
          @refresh="loadDecisions"
        />
      </div>
      <el-table
        v-loading="decisionLoading"
        class="ocpx-table"
        :data="decisions"
        highlight-current-row
        max-height="360"
        size="small"
      >
        <el-table-column
          v-if="hasDecisionColumn('receivedAt')"
          label="接收时间"
          prop="receivedAt"
          min-width="170"
        />
        <el-table-column
          v-if="hasDecisionColumn('requestId')"
          label="请求 ID"
          prop="requestId"
          min-width="180"
        />
        <el-table-column
          v-if="hasDecisionColumn('monitorTaskId')"
          label="监测任务"
          prop="monitorTaskId"
          min-width="150"
        />
        <el-table-column
          v-if="hasDecisionColumn('profileName')"
          label="Profile"
          prop="profileName"
          min-width="130"
        />
        <el-table-column
          v-if="hasDecisionColumn('decision')"
          label="决策"
          width="100"
          ><template #default="{ row }"
            ><el-tag :type="decisionTag(row.decision)">{{
              row.decision
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column
          v-if="hasDecisionColumn('reasonCode')"
          label="原因"
          prop="reasonCode"
          min-width="160"
        />
        <el-table-column
          v-if="hasDecisionColumn('latencyMs')"
          label="耗时(ms)"
          prop="latencyMs"
          width="100"
        />
      </el-table>
      <el-alert
        v-if="decisionError"
        :title="decisionError"
        type="error"
        show-icon
      />
      <el-empty
        v-if="!decisionLoading && !decisionError && decisions.length === 0"
        description="暂无 RTA 决策记录"
      />
      <el-pagination
        v-model:current-page="decisionCurrent"
        v-model:page-size="decisionSize"
        class="ocpx-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="PAGE_SIZES"
        :total="decisionTotal"
        @change="loadDecisions"
      />
    </el-card>

    <el-card v-if="canViewReports" shadow="never" class="rta-card">
      <template #header
        ><div class="rta-card-header">
          <strong>RTA 统计</strong
          ><span v-if="metricDataAsOf" class="rta-asof"
            >数据截至 {{ metricDataAsOf }}</span
          >
        </div></template
      >
      <el-table
        v-loading="metricLoading"
        class="ocpx-table"
        :data="metrics"
        highlight-current-row
        max-height="360"
        size="small"
      >
        <el-table-column label="时间桶" prop="bucketStart" min-width="170" />
        <el-table-column
          label="监测任务"
          prop="monitorTaskId"
          min-width="150"
        />
        <el-table-column label="Profile" prop="profileName" min-width="130" />
        <el-table-column label="请求数" prop="requestCount" width="90" />
        <el-table-column label="ALLOW" prop="allowCount" width="90" />
        <el-table-column label="DENY" prop="denyCount" width="90" />
        <el-table-column label="超时" prop="timeoutCount" width="90" />
        <el-table-column label="错误" prop="errorCount" width="90" />
        <el-table-column label="RTA 命中率" width="120"
          ><template #default="{ row }">{{
            rate(row.allowRate)
          }}</template></el-table-column
        >
      </el-table>
      <el-alert
        v-if="metricError"
        :title="metricError"
        type="error"
        show-icon
      />
      <el-empty
        v-if="!metricLoading && !metricError && metrics.length === 0"
        description="暂无 RTA 统计数据"
      />
    </el-card>

    <el-dialog
      v-model="profileDialogOpen"
      :close-on-click-modal="false"
      :title="editingProfile ? '编辑 RTA Profile' : '新增 RTA Profile'"
      width="620px"
    >
      <el-form
        ref="profileFormRef"
        label-width="110px"
        :model="profileForm"
        :rules="profileRules"
      >
        <el-form-item label="编号" prop="code"
          ><el-input v-model="profileForm.code" maxlength="64"
        /></el-form-item>
        <el-form-item label="名称" prop="name"
          ><el-input v-model="profileForm.name" maxlength="128"
        /></el-form-item>
        <el-form-item label="适配器"><el-tag>MOCK</el-tag></el-form-item>
        <el-form-item label="状态"
          ><el-select v-model="profileForm.status"
            ><el-option label="停用" value="DISABLED" /><el-option
              label="启用"
              value="ACTIVE" /></el-select
        ></el-form-item>
        <el-form-item label="Mock 配置" prop="configJson"
          ><el-input
            v-model="profileForm.configJson"
            type="textarea"
            :rows="6"
            placeholder="例如：{ decision: ALLOW, taskIds: [external-task-1] }"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="profileDialogOpen = false">取消</el-button
        ><el-button :loading="profileSaving" type="primary" @click="saveProfile"
          >保存</el-button
        ></template
      >
    </el-dialog>
  </Page>
</template>

<style scoped>
.rta-card {
  margin-bottom: 16px;
}

.rta-card-header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.rta-query {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.rta-query .el-input {
  width: 260px;
}

.rta-query .el-date-editor {
  width: 280px;
}

.rta-task-form {
  max-width: 900px;
}

.rta-task-form .el-select {
  width: 420px;
}

.rta-help,
.rta-asof {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rta-bindings {
  display: grid;
  gap: 8px;
  width: 100%;
}

.rta-binding-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) 70px;
  gap: 8px;
}

.rta-boundary {
  margin-bottom: 16px;
}

code {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
