<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type {
  CanonicalEventDefinition,
  CanonicalEventMapping,
  Channel,
  EventDefinitionCreateInput,
  EventMappingCreateInput,
} from '#/api/ocpx';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';

import { EditPen, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  changeCanonicalEventDefinitionStatus,
  changeCanonicalEventMappingStatus,
  createCanonicalEventDefinition,
  createCanonicalEventMapping,
  getCatalog,
  listCanonicalEventDefinitions,
  listCanonicalEventMappings,
  updateCanonicalEventDefinition,
  updateCanonicalEventMapping,
} from '#/api/ocpx';

import {
  compatibleDefinitions,
  resolveEventPageState,
  sanitizedEventEditorSnapshot,
  statusNeedsReferenceConfirmation,
} from './event-model';

defineOptions({ name: 'OcpxCanonicalEventManagement' });

const { hasAccessByCodes } = useAccess();
const canDefinitionView = computed(() => hasAccessByCodes(['ocpx:event:view']));
const canDefinitionAdd = computed(() => hasAccessByCodes(['ocpx:event:add']));
const canDefinitionEdit = computed(() => hasAccessByCodes(['ocpx:event:edit']));
const canDefinitionStatus = computed(() =>
  hasAccessByCodes(['ocpx:event:status']),
);
const canMappingView = computed(() =>
  hasAccessByCodes(['ocpx:event-mapping:view']),
);
const canMappingAdd = computed(() =>
  hasAccessByCodes(['ocpx:event-mapping:add']),
);
const canMappingEdit = computed(() =>
  hasAccessByCodes(['ocpx:event-mapping:edit']),
);
const canMappingStatus = computed(() =>
  hasAccessByCodes(['ocpx:event-mapping:status']),
);

const activeTab = ref<'definitions' | 'mappings' | 'references'>('definitions');
const definitions = ref<CanonicalEventDefinition[]>([]);
const mappings = ref<CanonicalEventMapping[]>([]);
const channels = ref<Channel[]>([]);
const definitionTotal = ref(0);
const mappingTotal = ref(0);
const loading = ref(false);
const loadError = ref(false);
const forbidden = ref(false);
const filters = reactive({
  channelId: '',
  direction: '',
  keyword: '',
  status: '',
});
const filtered = computed(() => Object.values(filters).some(Boolean));
const visibleTotal = computed(() => {
  if (activeTab.value === 'definitions') return definitionTotal.value;
  if (activeTab.value === 'mappings') return mappingTotal.value;
  return definitions.value.length + mappings.value.length;
});
const pageState = computed(() =>
  resolveEventPageState({
    canView:
      !forbidden.value && (canDefinitionView.value || canMappingView.value),
    error: loadError.value,
    filtered: filtered.value,
    total: visibleTotal.value,
  }),
);

async function load() {
  loading.value = true;
  loadError.value = false;
  forbidden.value = false;
  try {
    const [definitionResult, mappingResult, channelResult] = await Promise.all([
      canDefinitionView.value
        ? listCanonicalEventDefinitions({
            current: 1,
            direction: filters.direction || undefined,
            keyword: filters.keyword.trim() || undefined,
            size: 200,
            status: filters.status || undefined,
          })
        : Promise.resolve({ records: [], total: 0 }),
      canMappingView.value
        ? listCanonicalEventMappings({
            channelId: filters.channelId || undefined,
            current: 1,
            direction: filters.direction || undefined,
            keyword: filters.keyword.trim() || undefined,
            size: 200,
            status: filters.status || undefined,
          })
        : Promise.resolve({ records: [], total: 0 }),
      getCatalog('channel', { current: 1, size: 200 }),
    ]);
    definitions.value = definitionResult.records;
    definitionTotal.value = definitionResult.total;
    mappings.value = mappingResult.records;
    mappingTotal.value = mappingResult.total;
    channels.value = channelResult.records;
  } catch (error: any) {
    definitions.value = [];
    mappings.value = [];
    definitionTotal.value = 0;
    mappingTotal.value = 0;
    forbidden.value = isForbidden(error);
    loadError.value = !forbidden.value;
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  Object.assign(filters, {
    channelId: '',
    direction: '',
    keyword: '',
    status: '',
  });
  load();
}

type DefinitionEditor = EventDefinitionCreateInput & {
  id?: string;
  version?: number;
};
type MappingEditor = EventMappingCreateInput & {
  id?: string;
  version?: number;
};

const drawerOpen = ref(false);
const editorKind = ref<'definition' | 'mapping'>('definition');
const saving = ref(false);
const conflict = ref('');
const formError = ref('');
const savedSnapshot = ref('');
const formRef = ref<FormInstance>();
const definitionForm = reactive<DefinitionEditor>({
  billingEligible: false,
  code: '',
  description: '',
  direction: 'DOWNSTREAM_DELIVERY',
  name: '',
  sortOrder: 0,
  standardType: '',
  status: 'ACTIVE',
  subjectType: 'DEVICE',
});
const mappingForm = reactive<MappingEditor>({
  adapterCode: '',
  channelId: '',
  description: '',
  direction: 'DOWNSTREAM_DELIVERY',
  eventDefinitionId: '',
  externalCode: '',
  parameterTemplateCode: 'STANDARD_EVENT_V1',
  status: 'ACTIVE',
  successRuleCode: 'HTTP_2XX',
});
const rules: FormRules = {
  code: [{ message: '请输入大写标准 code', required: true }],
  eventDefinitionId: [{ message: '请选择标准事件', required: true }],
  externalCode: [{ message: '请输入渠道 externalCode', required: true }],
  name: [{ message: '请输入名称', required: true }],
  standardType: [{ message: '请输入大写标准类型', required: true }],
};
const currentEditor = computed(() =>
  editorKind.value === 'definition' ? definitionForm : mappingForm,
);
const mappingDefinitions = computed(() =>
  compatibleDefinitions(definitions.value, mappingForm.direction),
);

function openCreate(kind: 'definition' | 'mapping') {
  editorKind.value = kind;
  conflict.value = '';
  formError.value = '';
  if (kind === 'definition') {
    Object.assign(definitionForm, {
      billingEligible: false,
      code: '',
      description: '',
      direction: 'DOWNSTREAM_DELIVERY',
      id: undefined,
      name: '',
      sortOrder: 0,
      standardType: '',
      status: 'ACTIVE',
      subjectType: 'DEVICE',
      version: undefined,
    });
  } else {
    Object.assign(mappingForm, {
      adapterCode: '',
      channelId: '',
      description: '',
      direction: 'DOWNSTREAM_DELIVERY',
      eventDefinitionId: '',
      externalCode: '',
      id: undefined,
      parameterTemplateCode: 'STANDARD_EVENT_V1',
      status: 'ACTIVE',
      successRuleCode: 'HTTP_2XX',
      version: undefined,
    });
  }
  savedSnapshot.value = sanitizedEventEditorSnapshot(currentEditor.value);
  drawerOpen.value = true;
}

function openDefinition(row: CanonicalEventDefinition) {
  editorKind.value = 'definition';
  Object.assign(definitionForm, {
    billingEligible: row.billingEligible,
    code: row.code,
    description: row.description || '',
    direction: row.direction,
    id: row.id,
    name: row.name,
    sortOrder: row.sortOrder,
    standardType: row.standardType,
    status: row.status === 'ACTIVE' ? 'ACTIVE' : 'DISABLED',
    subjectType: row.subjectType,
    version: row.configVersion,
  });
  conflict.value = '';
  formError.value = '';
  savedSnapshot.value = sanitizedEventEditorSnapshot(definitionForm);
  drawerOpen.value = true;
}

function openMapping(row: CanonicalEventMapping) {
  editorKind.value = 'mapping';
  Object.assign(mappingForm, {
    adapterCode: row.adapterCode,
    channelId: row.channelId,
    description: row.description || '',
    direction: row.direction,
    eventDefinitionId: row.eventDefinitionId,
    externalCode: row.externalCode,
    id: row.id,
    parameterTemplateCode: row.parameterTemplateCode,
    status: row.status === 'ACTIVE' ? 'ACTIVE' : 'DISABLED',
    successRuleCode: row.successRuleCode,
    version: row.configVersion,
  });
  conflict.value = '';
  formError.value = '';
  savedSnapshot.value = sanitizedEventEditorSnapshot(mappingForm);
  drawerOpen.value = true;
}

function channelChanged() {
  const channel = channels.value.find(
    (item) => item.id === mappingForm.channelId,
  );
  mappingForm.adapterCode = channel?.adapterCode || '';
  mappingForm.eventDefinitionId = '';
}

function directionChanged() {
  if (
    !mappingDefinitions.value.some(
      (item) => item.id === mappingForm.eventDefinitionId,
    )
  ) {
    mappingForm.eventDefinitionId = '';
  }
}

async function save() {
  if (!(await formRef.value?.validate())) return;
  saving.value = true;
  conflict.value = '';
  formError.value = '';
  try {
    if (editorKind.value === 'definition') {
      const data = {
        billingEligible: definitionForm.billingEligible,
        description: definitionForm.description || undefined,
        direction: definitionForm.direction,
        name: definitionForm.name.trim(),
        sortOrder: definitionForm.sortOrder,
        standardType: definitionForm.standardType.trim().toUpperCase(),
        subjectType: definitionForm.subjectType,
      };
      await (definitionForm.id
        ? updateCanonicalEventDefinition(definitionForm.id, {
            ...data,
            expectedVersion: definitionForm.version || 0,
          })
        : createCanonicalEventDefinition(
            {
              ...data,
              code: definitionForm.code.trim().toUpperCase(),
              status: definitionForm.status,
            },
            crypto.randomUUID().replaceAll('-', ''),
          ));
    } else {
      const data = {
        description: mappingForm.description || undefined,
        direction: mappingForm.direction,
        eventDefinitionId: mappingForm.eventDefinitionId,
        externalCode: mappingForm.externalCode.trim(),
        parameterTemplateCode: mappingForm.parameterTemplateCode,
        successRuleCode: mappingForm.successRuleCode,
      };
      await (mappingForm.id
        ? updateCanonicalEventMapping(mappingForm.id, {
            ...data,
            expectedVersion: mappingForm.version || 0,
          })
        : createCanonicalEventMapping(
            {
              ...data,
              adapterCode: mappingForm.adapterCode,
              channelId: mappingForm.channelId,
              status: mappingForm.status,
            },
            crypto.randomUUID().replaceAll('-', ''),
          ));
    }
    drawerOpen.value = false;
    ElMessage.success('事件配置草稿已保存；不会发布或触发 Callback');
    await load();
  } catch (error: any) {
    if (isConflict(error)) conflict.value = errorText(error);
    else formError.value = errorText(error);
  } finally {
    saving.value = false;
  }
}

async function changeStatus(
  kind: 'definition' | 'mapping',
  row: CanonicalEventDefinition | CanonicalEventMapping,
) {
  const target = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  const reference = row.references;
  try {
    const message = statusNeedsReferenceConfirmation(row, 'DISABLED')
      ? `当前有 Product 默认 ${reference.productDefaultCount}、Task 绑定 ${reference.taskBindingCount}、已发布快照 ${reference.publishedSnapshotCount} 条引用。停用仅阻止新绑定/新发布，旧快照不变。`
      : '状态变更仅影响新绑定与后续发布，不会修改旧快照。';
    await ElMessageBox.confirm(
      message,
      target === 'ACTIVE' ? '重新启用' : '停用影响确认',
      {
        confirmButtonText: target === 'ACTIVE' ? '启用' : '确认停用',
        type: 'warning',
      },
    );
    const payload = {
      confirmed: true,
      expectedVersion: row.configVersion,
      reason: '管理页状态变更',
      status: target,
    } as const;
    await (kind === 'definition'
      ? changeCanonicalEventDefinitionStatus(row.id, payload)
      : changeCanonicalEventMappingStatus(row.id, payload));
    ElMessage.success(target === 'ACTIVE' ? '已启用' : '已停用；旧快照未变化');
    await load();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(errorText(error));
  }
}

async function beforeClose(done: () => void) {
  if (saving.value) return;
  if (
    sanitizedEventEditorSnapshot(currentEditor.value) === savedSnapshot.value
  ) {
    done();
    return;
  }
  try {
    await ElMessageBox.confirm('尚有未保存修改，确定放弃？', '未保存修改', {
      confirmButtonText: '放弃修改',
      type: 'warning',
    });
    done();
  } catch {
    // X、遮罩/ESC 与取消按钮统一保留输入。
  }
}

function isForbidden(error: any) {
  return [
    error?.data?.code,
    error?.response?.data?.code,
    error?.response?.status,
  ].includes(403);
}

function isConflict(error: any) {
  return [
    error?.data?.code,
    error?.response?.data?.code,
    error?.response?.status,
  ].includes(409);
}

function errorText(error: any) {
  return (
    error?.response?.data?.msg ||
    error?.response?.data?.message ||
    error?.data?.msg ||
    error?.message ||
    '请求失败，请稍后重试'
  );
}

onMounted(load);
</script>

<template>
  <div class="p-5">
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold">事件字典与渠道映射</h2>
        <p class="mt-1 text-sm text-gray-500">
          EventDefinition → ChannelEventMapping → TaskEventBinding →
          RouteEventSnapshot
        </p>
      </div>
      <div class="flex gap-2">
        <el-button
          v-if="activeTab === 'definitions' && canDefinitionAdd"
          type="primary"
          @click="openCreate('definition')"
        >
          <el-icon><Plus /></el-icon>新增标准事件
        </el-button>
        <el-button
          v-if="activeTab === 'mappings' && canMappingAdd"
          type="primary"
          @click="openCreate('mapping')"
        >
          <el-icon><Plus /></el-icon>新增渠道映射
        </el-button>
        <el-tooltip content="正式发布、Outbox 与实例分发属于 S7">
          <el-button disabled>发布配置</el-button>
        </el-tooltip>
      </div>
    </div>

    <el-alert
      class="mb-4"
      :closable="false"
      show-icon
      title="S6 仅收口事件配置真相、不可变快照合同和本地纯函数；不会访问第三方、发送 Callback、计费或重放"
      type="info"
    />

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="事件定义" name="definitions" />
        <el-tab-pane label="渠道映射" name="mappings" />
        <el-tab-pane label="发布引用" name="references" />
      </el-tabs>

      <div class="mb-4 grid gap-3 md:grid-cols-5">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="code / 名称 / externalCode"
          @keyup.enter="load"
        />
        <el-select v-model="filters.status" clearable placeholder="全部状态">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="DISABLED" />
          <el-option label="归档" value="ARCHIVED" />
        </el-select>
        <el-select v-model="filters.direction" clearable placeholder="全部方向">
          <el-option label="上游回调" value="UPSTREAM_CALLBACK" />
          <el-option label="下游投递" value="DOWNSTREAM_DELIVERY" />
          <el-option label="双向标准" value="BIDIRECTIONAL" />
        </el-select>
        <el-select
          v-model="filters.channelId"
          clearable
          :disabled="activeTab === 'definitions'"
          placeholder="全部渠道"
        >
          <el-option
            v-for="channel in channels"
            :key="channel.id"
            :label="channel.channelName"
            :value="channel.id"
          />
        </el-select>
        <div class="flex gap-2">
          <el-button type="primary" @click="load">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="resetFilters">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </div>
      </div>

      <div v-loading="loading">
        <el-result
          v-if="pageState === 'NO_PERMISSION'"
          icon="warning"
          sub-title="需要事件查看权限；渠道映射还会与 Channel 对象范围求交集"
          title="无权查看事件配置"
        />
        <el-result
          v-else-if="pageState === 'ERROR'"
          icon="error"
          sub-title="请求失败，未展示缓存或可能过期的数据"
          title="事件配置加载失败"
        >
          <template #extra
            ><el-button @click="load">重新加载</el-button></template
          >
        </el-result>
        <el-empty
          v-else-if="pageState === 'INITIAL_EMPTY'"
          description="尚无可见的标准事件或渠道映射"
        />
        <el-empty
          v-else-if="pageState === 'FILTER_EMPTY'"
          description="没有符合当前筛选条件的事件配置"
        >
          <el-button @click="resetFilters">清空筛选</el-button>
        </el-empty>

        <el-table
          v-else-if="activeTab === 'definitions'"
          :data="definitions"
          row-key="id"
        >
          <el-table-column label="标准事件" min-width="210">
            <template #default="{ row }">
              <div class="font-medium">{{ row.name }}</div>
              <div class="text-xs text-gray-400">
                {{ row.code }} · rev {{ row.configVersion }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="方向 / 类型" min-width="220">
            <template #default="{ row }">
              <div>{{ row.direction }}</div>
              <div class="text-xs text-gray-400">
                {{ row.standardType }} · {{ row.subjectType }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="计费资格" min-width="100">
            <template #default="{ row }">{{
              row.billingEligible ? '可参与' : '不可参与'
            }}</template>
          </el-table-column>
          <el-table-column label="引用" min-width="190">
            <template #default="{ row }">
              Product {{ row.references.productDefaultCount }} · Task
              {{ row.references.taskBindingCount }} · 快照
              {{ row.references.publishedSnapshotCount }}
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="90" prop="status" />
          <el-table-column fixed="right" label="操作" min-width="180">
            <template #default="{ row }">
              <el-button
                v-if="canDefinitionEdit"
                link
                type="primary"
                @click="openDefinition(row)"
              >
                <el-icon><EditPen /></el-icon>编辑
              </el-button>
              <el-button
                v-if="canDefinitionStatus"
                link
                @click="changeStatus('definition', row)"
              >
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-table
          v-else-if="activeTab === 'mappings'"
          :data="mappings"
          row-key="id"
        >
          <el-table-column label="渠道 / Adapter" min-width="190">
            <template #default="{ row }">
              <div>{{ row.channelName }}</div>
              <div class="text-xs text-gray-400">
                {{ row.adapterCode }} · capability rev
                {{ row.adapterCapabilityRevision }}
              </div>
            </template>
          </el-table-column>
          <el-table-column
            label="externalCode"
            min-width="180"
            prop="externalCode"
          />
          <el-table-column label="标准事件" min-width="180">
            <template #default="{ row }"
              >{{ row.eventDefinitionName }} ·
              {{ row.eventDefinitionCode }}</template
            >
          </el-table-column>
          <el-table-column
            label="受控模板"
            min-width="170"
            prop="parameterTemplateCode"
          />
          <el-table-column label="引用" min-width="160">
            <template #default="{ row }"
              >Task {{ row.references.taskBindingCount }} · 快照
              {{ row.references.publishedSnapshotCount }}</template
            >
          </el-table-column>
          <el-table-column label="状态" min-width="90" prop="status" />
          <el-table-column fixed="right" label="操作" min-width="180">
            <template #default="{ row }">
              <el-button
                v-if="canMappingEdit"
                link
                type="primary"
                @click="openMapping(row)"
              >
                <el-icon><EditPen /></el-icon>编辑
              </el-button>
              <el-button
                v-if="canMappingStatus"
                link
                @click="changeStatus('mapping', row)"
              >
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-else class="grid gap-4 lg:grid-cols-2">
          <el-card shadow="never">
            <template #header>事件定义引用</template>
            <el-empty
              v-if="definitions.length === 0"
              description="无可见引用"
            />
            <div
              v-for="row in definitions"
              :key="row.id"
              class="border-b py-2 last:border-0"
            >
              <div class="font-medium">{{ row.name }} · {{ row.code }}</div>
              <div class="text-sm text-gray-500">
                Product 默认 {{ row.references.productDefaultCount }}，Task 草稿
                {{ row.references.taskBindingCount }}，冻结快照
                {{ row.references.publishedSnapshotCount }}
              </div>
            </div>
          </el-card>
          <el-card shadow="never">
            <template #header>渠道映射引用</template>
            <el-empty
              v-if="mappings.length === 0"
              description="无可见引用或无 Channel 范围"
            />
            <div
              v-for="row in mappings"
              :key="row.id"
              class="border-b py-2 last:border-0"
            >
              <div class="font-medium">
                {{ row.channelName }} · {{ row.externalCode }}
              </div>
              <div class="text-sm text-gray-500">
                Task 草稿 {{ row.references.taskBindingCount }}，冻结快照
                {{ row.references.publishedSnapshotCount }}；旧快照不可修改
              </div>
            </div>
          </el-card>
        </div>
      </div>
    </el-card>

    <el-drawer
      v-model="drawerOpen"
      :before-close="beforeClose"
      size="min(680px, 94vw)"
      :title="`${currentEditor.id ? '编辑' : '新增'}${editorKind === 'definition' ? '标准事件' : '渠道映射'}`"
    >
      <el-alert
        v-if="conflict"
        class="mb-3"
        :closable="false"
        show-icon
        :title="conflict"
        type="error"
      >
        配置已过期，系统不会静默覆盖。关闭后刷新再编辑。
      </el-alert>
      <el-alert
        v-if="formError"
        class="mb-3"
        :closable="false"
        show-icon
        :title="formError"
        type="error"
      />
      <el-form
        ref="formRef"
        label-position="top"
        :model="currentEditor"
        :rules="rules"
      >
        <template v-if="editorKind === 'definition'">
          <div class="grid gap-3 md:grid-cols-2">
            <el-form-item label="标准 code" prop="code">
              <el-input
                v-model="definitionForm.code"
                :disabled="Boolean(definitionForm.id)"
                maxlength="64"
              />
              <div class="mt-1 text-xs text-gray-500">
                有已发布引用时始终不可修改；编辑接口不接收 code
              </div>
            </el-form-item>
            <el-form-item label="名称" prop="name"
              ><el-input v-model="definitionForm.name" maxlength="64"
            /></el-form-item>
            <el-form-item label="方向">
              <el-select v-model="definitionForm.direction">
                <el-option label="上游回调" value="UPSTREAM_CALLBACK" />
                <el-option label="下游投递" value="DOWNSTREAM_DELIVERY" />
                <el-option label="双向标准" value="BIDIRECTIONAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="标准类型" prop="standardType"
              ><el-input v-model="definitionForm.standardType" maxlength="64"
            /></el-form-item>
            <el-form-item label="事实主体">
              <el-select v-model="definitionForm.subjectType">
                <el-option label="设备" value="DEVICE" /><el-option
                  label="账号"
                  value="ACCOUNT"
                />
                <el-option label="订单" value="ORDER" /><el-option
                  label="线索"
                  value="LEAD"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="计费资格"
              ><el-switch v-model="definitionForm.billingEligible"
            /></el-form-item>
            <el-form-item label="排序"
              ><el-input-number v-model="definitionForm.sortOrder" :min="0"
            /></el-form-item>
          </div>
          <el-form-item label="说明"
            ><el-input
              v-model="definitionForm.description"
              maxlength="500"
              type="textarea"
          /></el-form-item>
        </template>
        <template v-else>
          <div class="grid gap-3 md:grid-cols-2">
            <el-form-item label="Channel" required>
              <el-select
                v-model="mappingForm.channelId"
                :disabled="Boolean(mappingForm.id)"
                filterable
                @change="channelChanged"
              >
                <el-option
                  v-for="channel in channels"
                  :key="channel.id"
                  :label="channel.channelName"
                  :value="channel.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="Adapter"
              ><el-input v-model="mappingForm.adapterCode" disabled
            /></el-form-item>
            <el-form-item label="方向">
              <el-select
                v-model="mappingForm.direction"
                @change="directionChanged"
              >
                <el-option label="上游回调" value="UPSTREAM_CALLBACK" />
                <el-option label="下游投递" value="DOWNSTREAM_DELIVERY" />
              </el-select>
            </el-form-item>
            <el-form-item label="标准事件" prop="eventDefinitionId">
              <el-select v-model="mappingForm.eventDefinitionId" filterable>
                <el-option
                  v-for="item in mappingDefinitions"
                  :key="item.id"
                  :label="`${item.name} · ${item.code}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="externalCode" prop="externalCode"
              ><el-input v-model="mappingForm.externalCode" maxlength="128"
            /></el-form-item>
            <el-form-item label="参数模板">
              <el-select v-model="mappingForm.parameterTemplateCode">
                <el-option
                  label="标准事件 V1"
                  value="STANDARD_EVENT_V1"
                /><el-option label="扁平事件 V1" value="FLAT_EVENT_V1" />
              </el-select>
              <div class="mt-1 text-xs text-gray-500">
                只允许受控模板，禁止任意脚本
              </div>
            </el-form-item>
            <el-form-item label="成功规则">
              <el-select v-model="mappingForm.successRuleCode">
                <el-option label="HTTP 2xx" value="HTTP_2XX" /><el-option
                  label="body.code = 0"
                  value="BODY_CODE_ZERO"
                />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="说明"
            ><el-input
              v-model="mappingForm.description"
              maxlength="500"
              type="textarea"
          /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button @click="beforeClose(() => (drawerOpen = false))"
            >取消</el-button
          >
          <el-button
            :disabled="Boolean(conflict)"
            :loading="saving"
            type="primary"
            @click="save"
            >保存配置（不发布）</el-button
          >
        </div>
      </template>
    </el-drawer>
  </div>
</template>
