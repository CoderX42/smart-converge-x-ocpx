<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';

import type { Channel, EventDefinition, EventMapping } from '#/api/ocpx';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  disableEventDefinition,
  disableEventMapping,
  getCatalog,
  getEventDefinitions,
  getEventMappings,
  saveEventDefinition,
  saveEventMapping,
} from '#/api/ocpx';

defineOptions({ name: 'SystemEventManagement' });

type Category = 'CONVERSION' | 'MEDIA' | 'UPSTREAM';

const activeTab = ref<'dictionary' | 'mapping'>('dictionary');
const category = ref<Category>('UPSTREAM');
const channelId = ref('');
const keyword = ref('');
const status = ref('');
const current = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const saving = ref(false);
const channels = ref<Channel[]>([]);
const definitions = ref<EventDefinition[]>([]);
const mappings = ref<EventMapping[]>([]);
const dictionaryDialog = ref(false);
const mappingDialog = ref(false);
const dirty = ref(false);
const definitionFormRef = ref<FormInstance>();
const mappingFormRef = ref<FormInstance>();

const definitionForm = reactive<Partial<EventDefinition>>({});
const mappingForm = reactive<Partial<EventMapping>>({});
const definitionRules = computed<FormRules>(() => ({
  eventCode: [
    { message: '请输入事件码', required: true, trigger: 'blur' },
    {
      message:
        category.value === 'CONVERSION'
          ? '系统事件码须以大写字母开头，仅含大写字母、数字和下划线'
          : '渠道事件码仅支持字母、数字、下划线、点、冒号和短横线',
      pattern:
        category.value === 'CONVERSION'
          ? /^[A-Z][A-Z0-9_]{0,63}$/
          : /^[A-Z0-9][\w.:-]{0,63}$/i,
      trigger: 'blur',
    },
  ],
  eventName: [{ message: '请输入事件名称', required: true, trigger: 'blur' }],
}));
const mappingRules: FormRules = {
  conversionEventId: [
    { message: '请选择系统转化事件', required: true, trigger: 'change' },
  ],
  mediaEventId: [
    { message: '请选择媒体/渠道事件', required: true, trigger: 'change' },
  ],
  upstreamEventId: [
    { message: '请选择上游 OCPX 事件', required: true, trigger: 'change' },
  ],
};

const upstreamChoices = computed(() =>
  definitions.value.filter(
    (item) => item.category === 'UPSTREAM' && item.status === 'ACTIVE',
  ),
);
const conversionChoices = ref<EventDefinition[]>([]);
const mediaChoices = computed(() =>
  definitions.value.filter(
    (item) => item.category === 'MEDIA' && item.status === 'ACTIVE',
  ),
);

const needsChannel = computed(() =>
  activeTab.value === 'mapping'
    ? true
    : category.value === 'UPSTREAM' || category.value === 'MEDIA',
);

function resetFilters() {
  keyword.value = '';
  status.value = '';
  current.value = 1;
  void load();
}

async function load() {
  if (needsChannel.value && !channelId.value) {
    definitions.value = [];
    mappings.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    if (activeTab.value === 'dictionary') {
      const result = await getEventDefinitions({
        category: category.value,
        channelId:
          category.value === 'CONVERSION' ? '' : channelId.value || undefined,
        current: current.value,
        keyword: keyword.value || undefined,
        size: size.value,
        status: status.value || undefined,
      });
      definitions.value = result.records;
      total.value = result.total;
    } else {
      const result = await getEventMappings({
        channelId: channelId.value,
        current: current.value,
        keyword: keyword.value || undefined,
        size: size.value,
        status: status.value || undefined,
      });
      mappings.value = result.records;
      total.value = result.total;
    }
  } finally {
    loading.value = false;
  }
}

async function loadMappingChoices() {
  if (!channelId.value) return;
  const [upstream, conversion, media] = await Promise.all([
    getEventDefinitions({
      category: 'UPSTREAM',
      channelId: channelId.value,
      current: 1,
      size: 200,
      status: 'ACTIVE',
    }),
    getEventDefinitions({
      category: 'CONVERSION',
      current: 1,
      size: 200,
      status: 'ACTIVE',
    }),
    getEventDefinitions({
      category: 'MEDIA',
      channelId: channelId.value,
      current: 1,
      size: 200,
      status: 'ACTIVE',
    }),
  ]);
  definitions.value = [...upstream.records, ...media.records];
  conversionChoices.value = conversion.records;
}

function openDefinition(row?: EventDefinition) {
  Object.keys(definitionForm).forEach(
    (key) => delete definitionForm[key as keyof EventDefinition],
  );
  Object.assign(
    definitionForm,
    row || {
      category: category.value,
      channelId: category.value === 'CONVERSION' ? undefined : channelId.value,
      eventCode: '',
      eventName: '',
      remark: '',
      sortOrder: 100,
      status: 'ACTIVE',
    },
  );
  dirty.value = false;
  dictionaryDialog.value = true;
}

async function openMapping(row?: EventMapping) {
  await loadMappingChoices();
  Object.keys(mappingForm).forEach(
    (key) => delete mappingForm[key as keyof EventMapping],
  );
  Object.assign(
    mappingForm,
    row || {
      channelId: channelId.value,
      remark: '',
      status: 'ACTIVE',
    },
  );
  dirty.value = false;
  mappingDialog.value = true;
}

async function saveDefinition() {
  if (!(await definitionFormRef.value?.validate())) return;
  saving.value = true;
  try {
    await saveEventDefinition({
      category: category.value,
      channelId: category.value === 'CONVERSION' ? undefined : channelId.value,
      eventCode: definitionForm.eventCode!.trim(),
      eventName: definitionForm.eventName!.trim(),
      id: definitionForm.id,
      remark: definitionForm.remark,
      sortOrder: definitionForm.sortOrder ?? 100,
      status: definitionForm.status ?? 'ACTIVE',
    });
    ElMessage.success('事件已保存');
    dirty.value = false;
    dictionaryDialog.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function saveMapping() {
  if (!(await mappingFormRef.value?.validate())) return;
  saving.value = true;
  try {
    await saveEventMapping({
      channelId: channelId.value,
      conversionEventId: mappingForm.conversionEventId,
      id: mappingForm.id,
      mediaEventId: mappingForm.mediaEventId,
      remark: mappingForm.remark,
      status: mappingForm.status ?? 'ACTIVE',
      upstreamEventId: mappingForm.upstreamEventId,
    });
    ElMessage.success('映射规则已保存');
    dirty.value = false;
    mappingDialog.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function disableDefinition(row: EventDefinition) {
  await ElMessageBox.confirm(
    '停用后不再允许新选择；已发布版本继续使用原快照。确认停用？',
    '停用事件',
    { type: 'warning' },
  );
  await disableEventDefinition(row.id);
  ElMessage.success('事件已停用');
  await load();
}

async function disableMapping(row: EventMapping) {
  await ElMessageBox.confirm(
    '停用后不再允许新绑定；已发布版本继续使用原快照。确认停用？',
    '停用映射',
    { type: 'warning' },
  );
  await disableEventMapping(row.id);
  ElMessage.success('映射已停用');
  await load();
}

async function beforeClose(done: () => void) {
  if (!dirty.value) return done();
  try {
    await ElMessageBox.confirm('尚有未保存修改，确定关闭？', '未保存修改', {
      type: 'warning',
    });
    done();
  } catch {
    // Keep editing.
  }
}

function closeDictionaryDialog() {
  void beforeClose(() => {
    dictionaryDialog.value = false;
  });
}

function closeMappingDialog() {
  void beforeClose(() => {
    mappingDialog.value = false;
  });
}

watch(
  definitionForm,
  () => {
    if (dictionaryDialog.value) dirty.value = true;
  },
  { deep: true },
);

watch(
  mappingForm,
  () => {
    if (mappingDialog.value) dirty.value = true;
  },
  { deep: true },
);

watch([activeTab, category, channelId], () => {
  current.value = 1;
  void load();
});

onMounted(async () => {
  const result = await getCatalog('channel', { current: 1, size: 500 });
  channels.value = result.records;
  channelId.value = result.records[0]?.id || '';
  await load();
});
</script>

<template>
  <Page
    title="事件管理"
    description="维护渠道事件字典与三段映射；已发布路由使用不可变快照。"
  >
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="事件字典" name="dictionary">
          <el-segmented
            v-model="category"
            :options="[
              { label: '上游 OCPX 事件', value: 'UPSTREAM' },
              { label: '系统转化事件', value: 'CONVERSION' },
              { label: '媒体/渠道事件', value: 'MEDIA' },
            ]"
          />
        </el-tab-pane>
        <el-tab-pane label="映射规则" name="mapping" />
      </el-tabs>

      <div class="toolbar">
        <el-select
          v-if="needsChannel"
          v-model="channelId"
          aria-label="选择渠道"
          filterable
          placeholder="请先选择渠道"
        >
          <el-option
            v-for="item in channels"
            :key="item.id"
            :label="item.channelName"
            :value="item.id"
          />
        </el-select>
        <el-input
          v-model="keyword"
          aria-label="搜索事件"
          clearable
          placeholder="搜索事件码或名称"
          @keyup.enter="load"
        />
        <el-select
          v-model="status"
          aria-label="筛选状态"
          clearable
          placeholder="全部状态"
        >
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load"
          >查询</el-button
        >
        <el-button @click="resetFilters">重置</el-button>
        <span class="toolbar__spacer"></span>
        <el-button
          v-access:code="'ocpx:event:write'"
          type="primary"
          :disabled="needsChannel && !channelId"
          @click="activeTab === 'dictionary' ? openDefinition() : openMapping()"
        >
          {{ activeTab === 'dictionary' ? '新增事件' : '新增映射' }}
        </el-button>
      </div>

      <el-alert
        v-if="needsChannel && !channelId"
        :closable="false"
        show-icon
        title="请先选择渠道后维护事件。"
        type="info"
      />

      <el-table
        v-if="activeTab === 'dictionary'"
        v-loading="loading"
        :data="definitions"
        border
        empty-text="暂无事件"
      >
        <el-table-column label="事件码" min-width="190" prop="eventCode" />
        <el-table-column label="事件名称" min-width="210" prop="eventName" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="排序" width="90" prop="sortOrder" />
        <el-table-column label="草稿引用" width="110" prop="draftReferences" />
        <el-table-column
          label="发布引用"
          width="110"
          prop="publishedReferences"
        />
        <el-table-column
          label="备注"
          min-width="180"
          prop="remark"
          show-overflow-tooltip
        />
        <el-table-column fixed="right" label="操作" width="150">
          <template #default="{ row }">
            <el-button
              v-access:code="'ocpx:event:write'"
              link
              type="primary"
              @click="openDefinition(row)"
            >
              编辑
            </el-button>
            <el-button
              v-access:code="'ocpx:event:write'"
              :aria-label="`停用事件 ${row.eventName}`"
              link
              type="danger"
              @click="disableDefinition(row)"
            >
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else
        v-loading="loading"
        :data="mappings"
        border
        empty-text="暂无映射规则"
      >
        <el-table-column label="上游 OCPX 事件" min-width="210">
          <template #default="{ row }"
            >{{ row.upstreamEventName }} · {{ row.upstreamEventCode }}</template
          >
        </el-table-column>
        <el-table-column label="系统转化事件" min-width="210">
          <template #default="{ row }"
            >{{ row.conversionEventName }} ·
            {{ row.conversionEventCode }}</template
          >
        </el-table-column>
        <el-table-column label="媒体/渠道事件" min-width="210">
          <template #default="{ row }"
            >{{ row.mediaEventName }} · {{ row.mediaEventCode }}</template
          >
        </el-table-column>
        <el-table-column label="草稿引用" width="110" prop="draftReferences" />
        <el-table-column
          label="发布引用"
          width="110"
          prop="publishedReferences"
        />
        <el-table-column label="状态" width="90" prop="status" />
        <el-table-column fixed="right" label="操作" width="150">
          <template #default="{ row }">
            <el-button
              v-access:code="'ocpx:event:write'"
              link
              type="primary"
              @click="openMapping(row)"
            >
              编辑
            </el-button>
            <el-button
              v-access:code="'ocpx:event:write'"
              :aria-label="`停用映射 ${row.upstreamEventName}`"
              link
              type="danger"
              @click="disableMapping(row)"
            >
              停用
            </el-button>
          </template>
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
      v-model="dictionaryDialog"
      :before-close="beforeClose"
      :close-on-click-modal="false"
      :title="definitionForm.id ? '编辑事件' : '新增事件'"
      width="560px"
    >
      <el-form
        ref="definitionFormRef"
        :model="definitionForm"
        :rules="definitionRules"
        label-position="top"
        @change="dirty = true"
      >
        <el-form-item label="事件码" prop="eventCode" required>
          <el-input
            v-model="definitionForm.eventCode"
            :disabled="Boolean(definitionForm.id && definitionForm.referenced)"
            maxlength="64"
            :placeholder="
              category === 'CONVERSION'
                ? '大写字母、数字和下划线'
                : '渠道原始事件码（区分展示大小写）'
            "
          />
        </el-form-item>
        <el-form-item label="事件名称" prop="eventName" required>
          <el-input v-model="definitionForm.eventName" maxlength="100" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="definitionForm.sortOrder"
            :min="0"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="definitionForm.status">
            <el-radio-button value="ACTIVE">启用</el-radio-button>
            <el-radio-button value="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="definitionForm.remark"
            :rows="3"
            maxlength="500"
            show-word-limit
            type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDictionaryDialog">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDefinition"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="mappingDialog"
      :before-close="beforeClose"
      :close-on-click-modal="false"
      :title="mappingForm.id ? '编辑映射规则' : '新增映射规则'"
      width="620px"
    >
      <el-form
        ref="mappingFormRef"
        :model="mappingForm"
        :rules="mappingRules"
        label-position="top"
        @change="dirty = true"
      >
        <el-form-item label="上游 OCPX 事件" prop="upstreamEventId" required>
          <el-select
            v-model="mappingForm.upstreamEventId"
            :disabled="Boolean(mappingForm.id && mappingForm.draftReferences)"
            filterable
          >
            <el-option
              v-for="item in upstreamChoices"
              :key="item.id"
              :label="`${item.eventName} · ${item.eventCode}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="系统转化事件" prop="conversionEventId" required>
          <el-select v-model="mappingForm.conversionEventId" filterable>
            <el-option
              v-for="item in conversionChoices"
              :key="item.id"
              :label="`${item.eventName} · ${item.eventCode}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="媒体/渠道事件" prop="mediaEventId" required>
          <el-select v-model="mappingForm.mediaEventId" filterable>
            <el-option
              v-for="item in mediaChoices"
              :key="item.id"
              :label="`${item.eventName} · ${item.eventCode}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="mappingForm.status">
            <el-radio-button value="ACTIVE">启用</el-radio-button>
            <el-radio-button value="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="mappingForm.remark"
            :rows="3"
            maxlength="500"
            type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeMappingDialog">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMapping"
          >保存</el-button
        >
      </template>
    </el-dialog>
  </Page>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 16px 0;
}

.toolbar :deep(.el-input),
.toolbar :deep(.el-select) {
  width: 220px;
}

.toolbar__spacer {
  flex: 1;
}

:deep(.el-pagination) {
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .toolbar {
    flex-wrap: wrap;
  }

  .toolbar__spacer {
    display: none;
  }
}
</style>
