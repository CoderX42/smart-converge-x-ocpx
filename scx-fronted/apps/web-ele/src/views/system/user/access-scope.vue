<script setup lang="ts">
import type {
  AccessScope,
  AccessScopeImpact,
  AccessScopeOptions,
  AccessScopeType,
} from '#/api/ocpx';

import { computed, ref } from 'vue';

import { useAccess } from '@boot/access';

import { Delete, Plus, RefreshRight } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  getAccessScopeOptions,
  getUserAccessScopeImpact,
  getUserAccessScopes,
  replaceUserAccessScopes,
} from '#/api/ocpx';

import { normalizedScopes, validateScopes } from './access-scope-model';

type UserRow = { id: string; nickname?: string; username: string };

const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const loadError = ref('');
const user = ref<UserRow>();
const version = ref(0);
const scopes = ref<AccessScope[]>([]);
const options = ref<AccessScopeOptions>();
const impact = ref<AccessScopeImpact>();
const baseline = ref('');
let loadSequence = 0;

const { hasAccessByCodes } = useAccess();
const canEditFunction = computed(() => hasAccessByCodes(['ocpx:scope:edit']));
const editable = computed(
  () => options.value?.allManageable === true && canEditFunction.value,
);
const dirty = computed(
  () => JSON.stringify(normalizedScopes(scopes.value)) !== baseline.value,
);

const typeOptions: Array<{
  disabled?: boolean;
  label: string;
  value: AccessScopeType;
}> = [
  { label: '全部 OCPX 对象', value: 'ALL' },
  { label: '广告主', value: 'ADVERTISER' },
  { label: '渠道', value: 'CHANNEL' },
  { label: '媒体账号', value: 'MEDIA_ACCOUNT' },
];

const levelOptions = [
  { label: '查看', value: 'VIEW' },
  { label: '操作', value: 'OPERATE' },
  { label: '管理', value: 'MANAGE' },
] as const;

function objectOptions(type: AccessScopeType) {
  if (type === 'ADVERTISER') return options.value?.advertisers ?? [];
  if (type === 'CHANNEL') return options.value?.channels ?? [];
  if (type === 'MEDIA_ACCOUNT') return options.value?.mediaAccounts ?? [];
  return [];
}

function resetBaseline() {
  baseline.value = JSON.stringify(normalizedScopes(scopes.value));
}

async function load() {
  if (!user.value) return;
  const sequence = ++loadSequence;
  loading.value = true;
  loadError.value = '';
  try {
    const [scopeResult, optionResult, impactResult] = await Promise.all([
      getUserAccessScopes(user.value.id),
      getAccessScopeOptions(),
      getUserAccessScopeImpact(user.value.id),
    ]);
    if (sequence !== loadSequence) return;
    version.value = scopeResult.version;
    scopes.value = scopeResult.scopes.map((scope) => ({ ...scope }));
    options.value = optionResult;
    impact.value = impactResult;
    resetBaseline();
  } catch {
    if (sequence === loadSequence) {
      loadError.value = '对象范围加载失败，请检查权限或稍后重试。';
    }
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}

function init(row: UserRow) {
  user.value = row;
  visible.value = true;
  scopes.value = [];
  options.value = undefined;
  impact.value = undefined;
  resetBaseline();
  void load();
}

function addScope() {
  if (scopes.value.some((scope) => scope.scopeType === 'ALL')) {
    ElMessage.warning('已有全部对象授权，请先移除后再添加具体对象');
    return;
  }
  scopes.value.push({ permissionLevel: 'VIEW', scopeType: 'ADVERTISER' });
}

function changeType(index: number, type: AccessScopeType) {
  const current = scopes.value[index];
  if (!current) return;
  current.scopeId = undefined;
  if (type === 'ALL') {
    scopes.value = [current];
  }
}

function removeScope(index: number) {
  scopes.value.splice(index, 1);
}

async function save() {
  if (!user.value || saving.value) return;
  const validation = validateScopes(scopes.value);
  if (!validation.valid) {
    ElMessage.warning(validation.message || '请检查授权范围');
    return;
  }
  saving.value = true;
  try {
    const result = await replaceUserAccessScopes(
      user.value.id,
      version.value,
      normalizedScopes(scopes.value),
    );
    version.value = result.version;
    scopes.value = result.scopes.map((scope) => ({ ...scope }));
    resetBaseline();
    ElMessage.success('OCPX 对象范围已更新');
    try {
      impact.value = await getUserAccessScopeImpact(user.value.id);
    } catch {
      impact.value = undefined;
    }
  } catch (error: any) {
    const code = error?.response?.data?.code;
    if (Number(code) === 409) {
      ElMessage.warning('范围已被其他人修改，正在刷新最新版本');
      await load();
    } else {
      ElMessage.error('范围保存失败，请检查权限或稍后重试');
    }
  } finally {
    saving.value = false;
  }
}

function beforeClose(done: () => void) {
  if (!dirty.value) {
    done();
    return;
  }
  ElMessageBox.confirm('尚有未保存的范围调整，确认关闭吗？', '放弃修改', {
    cancelButtonText: '继续编辑',
    confirmButtonText: '放弃修改',
    type: 'warning',
  })
    .then(done)
    .catch(() => undefined);
}

function close() {
  beforeClose(() => {
    visible.value = false;
  });
}

defineExpose({ init });
</script>

<template>
  <el-drawer
    v-model="visible"
    :before-close="beforeClose"
    :close-on-click-modal="!saving"
    :title="`${user?.username || '用户'} · OCPX 对象范围`"
    class="scope-drawer"
    size="760px"
  >
    <div class="scope-shell">
      <el-alert
        :closable="false"
        show-icon
        title="功能权限与对象范围同时生效；未授权对象按不存在处理。"
        type="info"
      />

      <el-skeleton v-if="loading" :rows="7" animated class="scope-loading" />

      <el-result
        v-else-if="loadError"
        icon="error"
        sub-title="不会使用旧缓存继续授权"
        :title="loadError"
      >
        <template #extra>
          <el-button :icon="RefreshRight" type="primary" @click="load">
            重新加载
          </el-button>
        </template>
      </el-result>

      <template v-else>
        <section v-if="impact" class="scope-impact" aria-label="当前授权影响">
          <div>
            <span>广告主</span><strong>{{ impact.advertiserCount }}</strong>
          </div>
          <div>
            <span>渠道</span><strong>{{ impact.channelCount }}</strong>
          </div>
          <div>
            <span>媒体账号</span><strong>{{ impact.mediaAccountCount }}</strong>
          </div>
          <div>
            <span>应用链接</span><strong>{{ impact.appLinkCount }}</strong>
          </div>
          <div>
            <span>监测链接</span><strong>{{ impact.monitorCount }}</strong>
          </div>
          <div>
            <span>已发布路由</span
            ><strong>{{ impact.publishedMonitorCount }}</strong>
          </div>
        </section>

        <el-alert
          v-if="!editable"
          :closable="false"
          class="scope-readonly"
          show-icon
          title="当前账号可以查看范围，但缺少范围维护权限或全局范围管理权。"
          type="warning"
        />

        <div class="scope-toolbar">
          <div>
            <h3>授权条目</h3>
            <p>版本 {{ version }} · 时间区间左闭右开</p>
          </div>
          <el-button
            v-access:code="'ocpx:scope:edit'"
            :disabled="!editable"
            :icon="Plus"
            @click="addScope"
          >
            添加范围
          </el-button>
        </div>

        <el-empty
          v-if="scopes.length === 0"
          description="未配置任何 OCPX 对象，保存后该用户将无法访问 OCPX 业务数据"
        />

        <div v-else class="scope-list">
          <article
            v-for="(scope, index) in scopes"
            :key="scope.id || index"
            class="scope-card"
          >
            <div class="scope-card-index">{{ index + 1 }}</div>
            <div class="scope-fields">
              <el-form-item label="对象类型">
                <el-select
                  v-model="scope.scopeType"
                  :disabled="!editable"
                  @change="changeType(index, $event)"
                >
                  <el-option
                    v-for="item in typeOptions"
                    :key="item.value"
                    :disabled="item.disabled"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>

              <el-form-item v-if="scope.scopeType !== 'ALL'" label="授权对象">
                <el-select
                  v-model="scope.scopeId"
                  :disabled="!editable"
                  filterable
                  placeholder="请选择"
                >
                  <el-option
                    v-for="item in objectOptions(scope.scopeType)"
                    :key="item.id"
                    :disabled="item.status !== 'ACTIVE'"
                    :label="`${item.name}${item.status === 'ACTIVE' ? '' : '（已停用）'}`"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="权限级别">
                <el-select
                  v-model="scope.permissionLevel"
                  :disabled="!editable"
                >
                  <el-option
                    v-for="item in levelOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="生效时间">
                <el-date-picker
                  v-model="scope.validFrom"
                  :disabled="!editable"
                  placeholder="立即生效"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                />
              </el-form-item>

              <el-form-item label="失效时间">
                <el-date-picker
                  v-model="scope.validTo"
                  :disabled="!editable"
                  placeholder="长期有效"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                />
              </el-form-item>
            </div>
            <el-button
              v-if="editable"
              :icon="Delete"
              aria-label="移除授权条目"
              circle
              plain
              type="danger"
              @click="removeScope(index)"
            />
          </article>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="scope-footer">
        <span v-if="dirty" class="scope-dirty">有未保存的调整</span>
        <span v-else>范围缓存按数据库版本自动失效</span>
        <div>
          <el-button :disabled="saving" @click="close">关闭</el-button>
          <el-button
            v-access:code="'ocpx:scope:edit'"
            :disabled="!editable || !dirty || loading"
            :loading="saving"
            type="primary"
            @click="save"
          >
            保存范围
          </el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.scope-shell {
  display: grid;
  gap: 18px;
}

.scope-loading {
  padding: 20px 4px;
}

.scope-impact {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  overflow: hidden;
  background: linear-gradient(135deg, var(--el-fill-color-light), transparent);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.scope-impact div {
  display: grid;
  gap: 5px;
  padding: 16px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.scope-impact div:last-child {
  border-right: 0;
}

.scope-impact span,
.scope-toolbar p,
.scope-footer {
  color: var(--el-text-color-secondary);
}

.scope-impact strong {
  font-size: 24px;
  color: var(--el-text-color-primary);
}

.scope-readonly {
  margin-top: -6px;
}

.scope-toolbar,
.scope-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.scope-toolbar h3,
.scope-toolbar p {
  margin: 0;
}

.scope-toolbar p {
  margin-top: 4px;
  font-size: 12px;
}

.scope-list {
  display: grid;
  gap: 12px;
}

.scope-card {
  position: relative;
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 14px;
  align-items: start;
  padding: 18px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.scope-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 8px 24px rgb(30 55 90 / 8%);
}

.scope-card-index {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  font-weight: 700;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 8px;
}

.scope-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.scope-fields :deep(.el-form-item) {
  margin-bottom: 12px;
}

.scope-fields :deep(.el-select),
.scope-fields :deep(.el-date-editor) {
  width: 100%;
}

.scope-dirty {
  color: var(--el-color-warning);
}

@media (max-width: 760px) {
  .scope-impact {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .scope-fields {
    grid-template-columns: 1fr;
  }
}
</style>
