<script setup lang="ts">
import type { EntityOption } from '#/api/ocpx';
import type { RtaKind, RtaProtocol, RtaRow } from '#/api/ocpx/rta';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { ElMessage } from 'element-plus';

import { queryRta, rtaOptions, rtaProtocols, saveRta } from '#/api/ocpx/rta';

import { columns, descriptions, displayRta, titles } from './catalog';

const props = defineProps<{
  kind: RtaKind;
  scopeId?: string;
  scopeName?: string;
}>();
const emit = defineEmits<{ saved: [] }>();
const protocols = ref<RtaProtocol[]>([]);
const protocolError = ref(false);
const route = useRoute();
const router = useRouter();
const { hasAccessByCodes } = useAccess();
const editable = computed(() =>
  ['bindings', 'media', 'upstreams'].includes(props.kind),
);
const canEdit = computed(() =>
  hasAccessByCodes([`ocpx:rta:${props.kind}:edit`]),
);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const rows = ref<RtaRow[]>([]);
const total = ref(0);
const dataAsOf = ref('');
const media = ref<EntityOption[]>([]);
const advertisers = ref<EntityOption[]>([]);
const tasks = ref<EntityOption[]>([]);
const upstreams = ref<RtaRow[]>([]);
const upstreamLoading = ref(false);
const drawer = ref(false);
const detail = ref<RtaRow>();
const form = reactive<RtaRow>({});
const selectedProtocol = computed(() =>
  protocols.value.find(
    (item) =>
      item.code ===
      (props.kind === 'bindings'
        ? upstreams.value.find((item) => item.id === form.upstreamId)
            ?.protocol || form.protocol
        : form.protocol),
  ),
);
function changeMediaProtocol() {
  form.protocolConfig =
    form.protocol === 'OPPO'
      ? { ownerMode: 'EMPTY', timeoutMs: '1000', uploadCredentialRef: '' }
      : {};
  form.notifyUrl = '';
}
async function copyAccess(row: RtaRow) {
  const lines = [`RTA地址：${row.requestUrl || ''}`];
  if (row.strategyId) lines.push(`策略id：${row.strategyId}`);
  if (row.clickUrl) lines.push(`点击监测链接：${row.clickUrl}`);
  try {
    await navigator.clipboard.writeText(lines.join('\n'));
    ElMessage.success('接入资料已复制，密钥请通过约定渠道单独交付');
  } catch {
    ElMessage.error('复制失败，请从配置中手动复制');
  }
}
function changeProtocol() {
  form.protocolConfig = {};
  form.credentialRef = '';
  form.endpoint = selectedProtocol.value?.fixedEndpoint || '';
}
function changeUpstream() {
  form.protocol = upstreams.value.find(
    (item) => item.id === form.upstreamId,
  )?.protocol;
  form.productId = '';
  form.externalTaskId = '';
}

const query = reactive({
  advertiserId: '',
  current: 1,
  enabled: '',
  grain: 'HOUR',
  keyword: '',
  mediaId: '',
  monitorTaskId: '',
  requestId: '',
  size: 30,
  status: '',
});
const range = ref<[Date, Date]>([
  new Date(Date.now() - 86_400_000),
  new Date(),
]);
let sequence = 0;
async function load(reset = false) {
  if (reset) query.current = 1;
  const token = ++sequence;
  loading.value = true;
  error.value = '';
  try {
    const result = await queryRta(props.kind, {
      ...query,
      endTime: range.value[1].getTime(),
      startTime: range.value[0].getTime(),
    });
    if (token !== sequence) return;
    rows.value = result.records;
    total.value = result.total;
    dataAsOf.value = result.dataAsOf || '';
  } catch (error_) {
    if (token === sequence) {
      rows.value = [];
      total.value = 0;
      error.value =
        error_ instanceof Error ? error_.message : '查询失败，请重试';
    }
  } finally {
    if (token === sequence) loading.value = false;
  }
}
async function searchUpstreams(keyword = '') {
  upstreamLoading.value = true;
  try {
    upstreams.value = await rtaOptions('upstreams', {
      advertiserId: form.advertiserId || '',
      keyword,
    });
  } finally {
    upstreamLoading.value = false;
  }
}
async function searchOptions(
  kind: 'advertisers' | 'media' | 'tasks',
  keyword: string,
) {
  try {
    const result = await rtaOptions(kind, { keyword });
    if (kind === 'media') media.value = result;
    if (kind === 'advertisers') advertisers.value = result;
    if (kind === 'tasks') tasks.value = result;
  } catch {
    ElMessage.error('选项加载失败，请重试');
  }
}
function edit(row?: RtaRow) {
  for (const key of Object.keys(form)) delete form[key as keyof RtaRow];
  Object.assign(
    form,
    {
      admissionSeconds: 300,
      enabled: false,
      mediaId: query.mediaId,
      advertiserId: query.advertiserId,
      protocol: 'GENERIC_ASYNC',
      protocolConfig: {},
      timeoutMs: 5000,
      version: 0,
    },
    row,
    {
      enabled: Boolean(row?.enabled),
      protocolConfig: { ...row?.protocolConfig },
    },
  );
  drawer.value = true;
  if (props.kind === 'bindings') void searchUpstreams();
}
async function save() {
  if (!['bindings', 'media', 'upstreams'].includes(props.kind)) return;
  const requiredByKind = {
    bindings: ['monitorTaskId', 'upstreamId'],
    media: ['mediaId', 'credentialRef'],
    upstreams: ['advertiserId', 'name', 'protocol'],
  };
  const required = [
    ...requiredByKind[props.kind as 'bindings' | 'media' | 'upstreams'],
  ];
  if (props.kind === 'media' && form.protocol !== 'OPPO')
    required.push('notifyUrl');
  if (props.kind !== 'media') {
    if (!selectedProtocol.value || protocolError.value) {
      ElMessage.warning('请先加载并选择有效协议');
      return;
    }
    if (props.kind === 'upstreams') {
      required.push('endpoint');
      if (selectedProtocol.value.credentialRequired)
        required.push('credentialRef');
      if (
        selectedProtocol.value.configFields.some(
          (field) =>
            !new RegExp(`^(?:${field.pattern})$`).test(
              form.protocolConfig?.[field.key] || '',
            ),
        )
      ) {
        ElMessage.warning('请正确填写协议参数');
        return;
      }
    } else
      required.push(
        ...selectedProtocol.value.bindingFields.map((field) => field.key),
      );
  }
  if (required.some((key) => !String(form[key as keyof RtaRow] || '').trim())) {
    ElMessage.warning('请填写所有必填项');
    return;
  }
  saving.value = true;
  try {
    await saveRta(props.kind as 'bindings' | 'media' | 'upstreams', form);
    drawer.value = false;
    ElMessage.success('配置已保存');
    await load();
    emit('saved');
  } finally {
    saving.value = false;
  }
}
function routeFilters() {
  query.mediaId =
    typeof route.query.mediaId === 'string' ? route.query.mediaId : '';
  query.monitorTaskId =
    typeof route.query.monitorTaskId === 'string'
      ? route.query.monitorTaskId
      : '';
  query.requestId =
    typeof route.query.requestId === 'string' ? route.query.requestId : '';
  if (props.scopeId) {
    query.advertiserId = props.kind === 'upstreams' ? props.scopeId : '';
    query.mediaId = props.kind === 'media' ? props.scopeId : '';
    query.monitorTaskId = props.kind === 'bindings' ? props.scopeId : '';
  }
  void load(true);
}
watch(() => route.query, routeFilters);
onMounted(async () => {
  routeFilters();
  if (props.kind === 'upstreams' || props.kind === 'bindings') {
    try {
      protocols.value = await rtaProtocols();
    } catch {
      protocolError.value = true;
      ElMessage.error('协议加载失败，请关闭后重试');
    }
  }
  if (props.scopeId) return;
  const result = await Promise.allSettled([
    rtaOptions('media'),
    rtaOptions('advertisers'),
    rtaOptions('tasks'),
  ]);
  if (result[0].status === 'fulfilled') media.value = result[0].value;
  if (result[1].status === 'fulfilled') advertisers.value = result[1].value;
  if (result[2].status === 'fulfilled') tasks.value = result[2].value;
  if (result.some((item) => item.status === 'rejected'))
    ElMessage.error('部分筛选选项加载失败，请刷新重试');
});
</script>

<template>
  <component
    :is="scopeId ? 'section' : Page"
    :title="scopeId ? undefined : titles[kind]"
    :description="scopeId ? undefined : descriptions[kind]"
  >
    <p v-if="scopeId" class="text-muted-foreground mb-4 text-sm">
      {{ descriptions[kind] }}
    </p>
    <div class="rta-workspace">
      <el-form inline class="rta-filters" @submit.prevent="load(true)">
        <el-form-item v-if="!scopeId && kind !== 'upstreams'" label="媒体"
          ><el-select
            v-model="query.mediaId"
            remote
            :remote-method="
              (keyword: string) => searchOptions('media', keyword)
            "
            clearable
            filterable
            placeholder="全部媒体"
            ><el-option
              v-for="item in media"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item v-if="!scopeId && kind !== 'media'" label="广告主"
          ><el-select
            v-model="query.advertiserId"
            remote
            :remote-method="
              (keyword: string) => searchOptions('advertisers', keyword)
            "
            clearable
            filterable
            placeholder="全部广告主"
            ><el-option
              v-for="item in advertisers"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item
          v-if="!scopeId && !['media', 'upstreams'].includes(kind)"
          label="任务"
          ><el-select
            v-model="query.monitorTaskId"
            remote
            :remote-method="
              (keyword: string) => searchOptions('tasks', keyword)
            "
            clearable
            filterable
            placeholder="全部任务"
            ><el-option
              v-for="item in tasks"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item v-if="editable" label="名称"
          ><el-input v-model="query.keyword" clearable placeholder="搜索名称"
        /></el-form-item>
        <el-form-item v-if="editable" label="启用状态"
          ><el-select v-model="query.enabled" clearable placeholder="全部状态"
            ><el-option label="已启用" value="true" /><el-option
              label="已停用"
              value="false" /></el-select
        ></el-form-item>
        <el-form-item
          v-if="kind === 'records' || kind === 'logs'"
          label="请求 ID"
          ><el-input v-model="query.requestId" clearable
        /></el-form-item>
        <el-form-item v-if="kind === 'records'" label="决策状态"
          ><el-select v-model="query.status" clearable placeholder="全部状态"
            ><el-option
              v-for="value in ['PENDING', 'ALLOW', 'DENY', 'ERROR', 'TIMEOUT']"
              :key="value"
              :label="value"
              :value="value" /></el-select
        ></el-form-item>
        <el-form-item v-if="kind === 'logs' || kind === 'reports'" label="时间"
          ><el-date-picker
            v-model="range"
            type="datetimerange"
            :clearable="false"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
        /></el-form-item>
        <el-form-item v-if="kind === 'reports'" label="粒度"
          ><el-select v-model="query.grain"
            ><el-option label="小时" value="HOUR" /><el-option
              label="天"
              value="DAY" /></el-select
        ></el-form-item>
        <el-form-item
          ><el-button type="primary" native-type="submit">查询</el-button
          ><el-button :loading="loading" @click="load()"
            >刷新</el-button
          ></el-form-item
        >
      </el-form>
      <div class="rta-toolbar">
        <span
          >{{ total }} 条 ·
          {{
            editable
              ? '修改后立即生效'
              : kind === 'records'
                ? 'Redis 实时状态 · 保留 24 小时'
                : `最近落库：${dataAsOf || '暂无数据'}`
          }}</span
        ><el-button
          v-if="
            canEdit &&
            kind !== 'bindings' &&
            !(kind === 'media' && rows.length > 0)
          "
          type="primary"
          @click="edit()"
          >新增{{ titles[kind] }}</el-button
        >
      </div>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
      />
      <el-table
        v-loading="loading"
        :data="rows"
        border
        stripe
        empty-text="暂无符合条件的记录"
      >
        <el-table-column
          v-for="column in columns[kind]"
          :key="column.key"
          :label="column.label"
          :min-width="column.width || 135"
          show-overflow-tooltip
          ><template #default="{ row }">{{
            displayRta(row[column.key], column.key)
          }}</template></el-table-column
        >
        <el-table-column
          v-if="
            canEdit ||
            kind === 'records' ||
            kind === 'media' ||
            kind === 'bindings'
          "
          label="操作"
          fixed="right"
          width="210"
          ><template #default="{ row }"
            ><el-button v-if="canEdit" link type="primary" @click="edit(row)"
              >配置</el-button
            ><el-button
              v-if="row.requestUrl && (kind === 'media' || kind === 'bindings')"
              link
              type="primary"
              @click="copyAccess(row)"
              >复制接入资料</el-button
            ><el-button
              v-if="kind === 'records'"
              link
              type="primary"
              @click="detail = row"
              >查看详情</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 30, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="load()"
        @size-change="load(true)"
      />
    </div>
    <el-drawer
      v-model="drawer"
      :title="`配置${titles[kind]}`"
      size="min(560px, 100vw)"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent="save">
        <template v-if="kind === 'upstreams'">
          <el-form-item v-if="!scopeId" label="广告主" required
            ><el-select
              v-model="form.advertiserId"
              remote
              :remote-method="
                (keyword: string) => searchOptions('advertisers', keyword)
              "
              :disabled="!!form.id"
              filterable
              ><el-option
                v-for="item in advertisers"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item v-if="scopeId" label="广告主"
            >{{ scopeName }} · {{ scopeId }}</el-form-item
          >
          <el-form-item label="接入名称" required
            ><el-input v-model="form.name" maxlength="128"
          /></el-form-item>
          <el-form-item
            v-if="form.id && selectedProtocol?.mode === 'ASYNC_CALLBACK'"
            label="回调接入编号"
            ><el-input
              v-model="form.accessCode"
              :disabled="!!form.id"
              placeholder="字母、数字、下划线或短横线"
              maxlength="64"
          /></el-form-item>
          <el-form-item label="RTA 协议" required>
            <el-select
              v-model="form.protocol"
              :disabled="!!form.id"
              @change="changeProtocol"
              ><el-option
                v-for="item in protocols"
                :key="item.code"
                :label="item.name"
                :value="item.code"
            /></el-select>
          </el-form-item>
          <el-alert
            v-if="selectedProtocol"
            :title="`${selectedProtocol.mode === 'SYNC_HTTP' ? '同步返回决策' : '独立回调决策'} · 每次最多 ${selectedProtocol.maxCandidates} 个任务`"
            :closable="false"
            class="mb-4"
          />
          <el-form-item
            v-for="field in selectedProtocol?.configFields || []"
            :key="field.key"
            :label="field.label"
            required
            ><el-input
              v-if="form.protocolConfig"
              v-model="form.protocolConfig[field.key]"
              maxlength="128"
          /></el-form-item>
          <el-form-item label="广告主请求地址" required
            ><el-input
              v-model="form.endpoint"
              :readonly="!!selectedProtocol?.fixedEndpoint"
              placeholder="https://允许的域名/路径"
          /></el-form-item>
          <el-form-item label="等待时限 / 毫秒"
            ><el-input-number v-model="form.timeoutMs" :min="100" :max="30000"
          /></el-form-item>
          <el-form-item label="放行有效期 / 秒"
            ><el-input-number
              v-model="form.admissionSeconds"
              :min="1"
              :max="86400"
          /></el-form-item>
          <el-form-item
            v-if="
              form.callbackUrl && selectedProtocol?.mode === 'ASYNC_CALLBACK'
            "
            label="SCX 决策回调地址"
            ><el-input :model-value="form.callbackUrl" readonly
          /></el-form-item>
        </template>
        <template v-if="kind === 'media'">
          <el-form-item v-if="scopeId" label="媒体"
            >{{ scopeName }} · {{ scopeId }}</el-form-item
          >
          <el-form-item v-if="!scopeId" label="现有媒体" required
            ><el-select
              v-model="form.mediaId"
              remote
              :remote-method="
                (keyword: string) => searchOptions('media', keyword)
              "
              :disabled="!!form.version"
              filterable
              ><el-option
                v-for="item in media"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="媒体 RTA 协议" required>
            <el-select v-model="form.protocol" @change="changeMediaProtocol">
              <el-option label="通用异步 RTA" value="GENERIC_ASYNC" />
              <el-option label="OPPO 策略 ID 协议" value="OPPO" />
            </el-select>
          </el-form-item>
          <template v-if="form.protocol === 'OPPO' && form.protocolConfig">
            <el-form-item label="owner_id 签名约定">
              <el-select v-model="form.protocolConfig.ownerMode">
                <el-option label="为空（签名拼空字符串）" value="EMPTY" />
                <el-option label="指定 OPPO owner_id" value="VALUE" />
              </el-select>
            </el-form-item>
            <el-form-item
              v-if="form.protocolConfig.ownerMode === 'VALUE'"
              label="OPPO owner_id"
              required
            >
              <el-input v-model="form.protocolConfig.ownerId" maxlength="18" />
            </el-form-item>
            <el-form-item label="同步等待时限 / 毫秒" required>
              <el-input
                v-model="form.protocolConfig.timeoutMs"
                placeholder="100–5000，按 OPPO 约定填写"
              />
            </el-form-item>
            <el-form-item label="转化回传凭据引用">
              <el-input
                v-model="form.protocolConfig.uploadCredentialRef"
                maxlength="64"
                placeholder="服务端配置中的 OPPO 上传凭据名称"
              />
            </el-form-item>
            <el-alert
              title="本次请求直接返回允许投放的策略。签名密钥由服务端配置，通过约定渠道单独交付。"
              :closable="false"
            />
          </template>
          <el-form-item v-if="form.requestUrl" label="RTA 地址">
            <el-input :model-value="form.requestUrl" readonly />
          </el-form-item>
          <el-form-item
            v-if="form.protocol !== 'OPPO'"
            label="决策通知地址"
            required
            ><el-input
              v-model="form.notifyUrl"
              placeholder="https://允许的域名/路径"
          /></el-form-item>
          <el-alert
            v-if="form.protocol !== 'OPPO'"
            title="HMAC-SHA256：X-Rta-Key、X-Rta-Timestamp、X-Rta-Nonce、X-Rta-Sign。请求体参与签名，凭据绑定当前媒体。"
            :closable="false"
            type="info"
          />
        </template>
        <template v-if="kind === 'bindings'">
          <template v-if="form.mediaProtocol === 'OPPO'">
            <el-form-item label="OPPO 策略 ID">
              <el-input
                :model-value="
                  form.strategyId ? String(form.strategyId) : '首次保存后生成'
                "
                readonly
              />
            </el-form-item>
            <el-form-item label="投放应用包名">
              <el-input
                v-model="form.oppoPkg"
                maxlength="128"
                placeholder="转化回传时必填，如 com.example.app"
              />
            </el-form-item>
            <el-form-item v-if="form.clickUrl" label="OPPO 点击监测链接">
              <el-input
                :model-value="form.clickUrl"
                type="textarea"
                :rows="4"
                readonly
              />
            </el-form-item>
          </template>
          <el-form-item label="监测任务"
            >{{ form.taskName }} · {{ form.monitorTaskId }}</el-form-item
          >
          <el-form-item label="归属"
            >{{ form.advertiserName }} / {{ form.mediaName }}</el-form-item
          >
          <el-form-item label="广告主接入" required
            ><el-select
              v-model="form.upstreamId"
              @change="changeUpstream"
              filterable
              remote
              :remote-method="searchUpstreams"
              :loading="upstreamLoading"
              ><el-option
                v-for="item in upstreams"
                :key="item.id"
                :label="item.name"
                :value="item.id!" /></el-select
          ></el-form-item>
          <el-form-item
            v-for="field in selectedProtocol?.bindingFields || []"
            :key="field.key"
            :label="field.label"
            required
          >
            <el-input
              v-model="form[field.key as 'productId' | 'externalTaskId']"
              maxlength="128"
            />
          </el-form-item>
          <el-alert
            v-if="
              selectedProtocol && selectedProtocol.bindingFields.length === 0
            "
            title="使用所选接入的广告计划；系统将校验 RTA 与点击链接的计划参数一致。"
            :closable="false"
          />
        </template>
        <el-form-item
          v-if="
            kind === 'media' ||
            (kind === 'upstreams' && selectedProtocol?.credentialRequired)
          "
          label="服务端凭据引用"
          required
          ><el-input
            v-model="form.credentialRef"
            maxlength="64"
            placeholder="配置文件中的凭据名称，不填写密钥"
        /></el-form-item>
        <el-form-item label="状态"
          ><el-switch
            v-model="form.enabled"
            active-text="启用"
            inactive-text="停用"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="drawer = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存配置</el-button
        ></template
      >
    </el-drawer>
    <el-drawer
      :model-value="!!detail"
      title="请求详情"
      size="min(620px, 100vw)"
      @close="detail = undefined"
    >
      <el-descriptions v-if="detail" :column="1" border
        ><el-descriptions-item
          v-for="key in [
            'requestId',
            'relayRequestId',
            'protocol',
            'decisionMode',
            'providerRequestId',
            'dispatchStatus',
            'mediaId',
            'status',
            'matchedTaskIds',
            'deliveryStatus',
            'deliveryAttempts',
            'deadline',
            'expiresAt',
            'reasonCode',
          ] as const"
          :key="key"
          :label="key"
          >{{ displayRta(detail[key], key) }}</el-descriptions-item
        ></el-descriptions
      >
      <el-button
        v-if="detail && hasAccessByCodes(['ocpx:rta:logs:view'])"
        class="mt-4"
        type="primary"
        @click="
          router.push({
            path: '/ocpx/rta/logs',
            query: { requestId: detail.requestId, mediaId: detail.mediaId },
          })
        "
        >查看关联流程日志</el-button
      >
    </el-drawer>
  </component>
</template>

<style scoped>
.rta-workspace {
  padding: 20px;
  background: var(--el-bg-color);
  border-radius: 8px;
}

.rta-filters :deep(.el-select) {
  width: 180px;
}

.rta-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  color: var(--el-text-color-secondary);
}

.el-pagination {
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
