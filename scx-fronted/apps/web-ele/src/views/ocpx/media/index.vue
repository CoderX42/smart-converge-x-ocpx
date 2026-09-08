<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { EntityOption, Media } from '#/api/ocpx';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@boot/access';
import { Page } from '@boot/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createMedia,
  deleteMedia,
  getMediaMacros,
  listMedia,
  mediaOptions,
  updateMedia,
} from '#/api/ocpx';
import RightToolbar from '#/components/right-toolbar/index.vue';

import LongText from '../long-text.vue';
import RtaConfigurationDrawer from '../rta/configuration-drawer.vue';
import {
  allColumnKeys,
  createLatestRequestGuard,
  PAGE_SIZES,
  resetPage,
} from '../table';

import '../table.scss';

defineOptions({ name: 'OcpxMediaManagement' });
const router = useRouter();

const MACRO_GROUPS = ref([
  {
    name: '设备标识',
    items: [
      { key: 'imei', macro: '__IMEI__', label: 'IMEI' },
      { key: 'imei_md5', macro: '__IMEI_MD5__', label: 'IMEI MD5' },
      { key: 'oaid', macro: '__OAID__', label: 'OAID' },
      { key: 'oaid_md5', macro: '__OAID_MD5__', label: 'OAID MD5' },
      { key: 'idfa', macro: '__IDFA__', label: 'IDFA' },
      { key: 'idfa_md5', macro: '__IDFA_MD5__', label: 'IDFA MD5' },
      { key: 'aaid', macro: '__AAID__', label: 'AAID' },
      { key: 'androidid', macro: '__ANDROIDID__', label: 'Android ID' },
      { key: 'mac', macro: '__MAC__', label: 'MAC' },
    ],
  },
  {
    name: '网络/终端',
    items: [
      { key: 'ip', macro: '__IP__', label: 'IP' },
      { key: 'ua', macro: '__UA__', label: 'User-Agent' },
      { key: 'os', macro: '__OS__', label: '操作系统' },
      { key: 'model', macro: '__MODEL__', label: '机型' },
      { key: 'ts', macro: '__TS__', label: '时间戳' },
    ],
  },
  {
    name: '平台路由',
    items: [
      { key: 'request_id', macro: '__REQUEST_ID__', label: '请求 ID' },
      { key: 'callback_url', macro: '__CALLBACK_URL__', label: '回调地址' },
      { key: 'clickid', macro: '__CLICKID__', label: '点击 ID' },
    ],
  },
  {
    name: '广告维度',
    items: [
      { key: 'plan_id', macro: '__ORDER_PLAN_ID__', label: '广告计划' },
      { key: 'group_id', macro: '__ORDER_GROUP_ID__', label: '广告组' },
      { key: 'creative_id', macro: '__CREATIVE_ID__', label: '创意' },
      { key: 'adv_id', macro: '__ADVERTISER_ID__', label: '广告主' },
    ],
  },
]);

const COMMON_MACROS = computed(() =>
  MACRO_GROUPS.value.flatMap((group) => group.items),
);
const ALL_MACRO_KEYS = computed(() =>
  COMMON_MACROS.value.map((item) => item.key),
);

const columns = [
  { key: 'id', label: '渠道 ID' },
  { key: 'name', label: '渠道名称' },
  { key: 'uri', label: 'URI' },
  { key: 'credential', label: '凭据引用' },
  { key: 'macro', label: '宏参数' },
  { key: 'createTime', label: '创建时间' },
  { key: 'updateTime', label: '修改时间' },
];
const visibleColumns = ref(allColumnKeys(columns));
const rows = ref<Media[]>([]);
const mediaSelects = ref<EntityOption[]>([]);
const loading = ref(false);
const saving = ref(false);
const deletingId = ref('');
const total = ref(0);
const showSearch = ref(true);
const query = reactive({ current: 1, mediaId: '', size: 30 });
const dialogOpen = ref(false);
const editing = ref<Media>();
const formRef = ref<FormInstance>();
const form = reactive({
  credentialConfigKey: '',
  customMacros: '',
  mediaName: '',
  selectedMacros: [] as string[],
  uri: '/feedback',
});
const rules: FormRules = {
  mediaName: [{ message: '请输入渠道名称', required: true, trigger: 'blur' }],
  uri: [{ message: '请输入 URI', required: true, trigger: 'blur' }],
};
const { hasAccessByCodes } = useAccess();
const canOperate = computed(() =>
  hasAccessByCodes(['ocpx:media:edit', 'ocpx:media:delete']),
);
const loadGuard = createLatestRequestGuard();

const macroParams = computed(() => {
  const selected = new Set(form.selectedMacros);
  const pairs = COMMON_MACROS.value
    .filter((item) => selected.has(item.key))
    .map((item) => `${item.key}=${item.macro}`);
  const custom = form.customMacros.trim();
  if (custom) pairs.push(custom);
  return pairs.join('&');
});

function parseMacroParams(raw?: null | string) {
  const commonKeys = new Set(COMMON_MACROS.value.map((item) => item.key));
  const selected: string[] = [];
  const custom: string[] = [];
  if (raw) {
    for (const segment of raw.split('&')) {
      const pair = segment.trim();
      if (!pair) continue;
      const eq = pair.indexOf('=');
      const key = (eq === -1 ? pair : pair.slice(0, eq)).trim();
      if (!key) continue;
      if (commonKeys.has(key)) {
        if (!selected.includes(key)) selected.push(key);
      } else {
        custom.push(pair);
      }
    }
  }
  return { custom: custom.join('&'), selected };
}

const allMacroKeys = () => [...ALL_MACRO_KEYS.value];

function hasColumn(key: string) {
  return visibleColumns.value.includes(key);
}

async function load() {
  const requestSequence = loadGuard.begin();
  loading.value = true;
  try {
    const result = await listMedia({
      current: query.current,
      mediaId: query.mediaId || undefined,
      size: query.size,
    });
    if (loadGuard.isLatest(requestSequence)) {
      rows.value = result.records;
      total.value = result.total;
    }
  } finally {
    if (loadGuard.isLatest(requestSequence)) loading.value = false;
  }
}

function search() {
  resetPage(query);
  void load();
}

function edit(row?: Media) {
  editing.value = row;
  const parsed = parseMacroParams(row?.macroParams);
  Object.assign(form, {
    credentialConfigKey: row?.credentialConfigKey || '',
    customMacros: row ? parsed.custom : '',
    mediaName: row?.mediaName || '',
    selectedMacros: row ? parsed.selected : allMacroKeys(),
    uri: row?.uri || '/feedback',
  });
  dialogOpen.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  if (!macroParams.value) {
    ElMessage.warning('请至少选择一个宏参数，或填写自定义宏参数');
    return;
  }
  saving.value = true;
  try {
    const editingId = editing.value?.id;
    const payload = {
      credentialConfigKey: form.credentialConfigKey || undefined,
      macroParams: macroParams.value,
      mediaName: form.mediaName,
      uri: form.uri,
    };
    await (editingId ? updateMedia(editingId, payload) : createMedia(payload));
    ElMessage.success(editingId ? '编辑成功' : '新增成功');
    dialogOpen.value = false;
    mediaSelects.value = await mediaOptions();
    await load();
  } catch {
    // 请求拦截器已统一提示错误，这里吞掉避免未捕获的 Promise 拒绝
  } finally {
    saving.value = false;
  }
}

function handleDialogClose() {
  editing.value = undefined;
  formRef.value?.resetFields();
  Object.assign(form, {
    credentialConfigKey: '',
    customMacros: '',
    mediaName: '',
    selectedMacros: allMacroKeys(),
    uri: '/feedback',
  });
}

async function remove(row: Media) {
  await ElMessageBox.confirm(
    '仅未被监测任务、扣量规则或历史数据引用的媒体可删除。确认继续？',
    '删除媒体',
    {
      confirmButtonText: '确认删除',
      type: 'warning',
    },
  );
  deletingId.value = row.id;
  try {
    await deleteMedia(row.id);
    ElMessage.success('删除成功');
    mediaSelects.value = await mediaOptions();
    if (query.mediaId === row.id) query.mediaId = '';
    await load();
  } finally {
    deletingId.value = '';
  }
}

onMounted(async () => {
  const macros = await getMediaMacros();
  const groups = new Map<string, (typeof MACRO_GROUPS.value)[number]>();
  for (const item of macros) {
    const group = groups.get(item.group) || { items: [], name: item.group };
    group.items.push({ key: item.key, label: item.label, macro: item.macro });
    groups.set(item.group, group);
  }
  MACRO_GROUPS.value = [...groups.values()];
  mediaSelects.value = await mediaOptions();
  await load();
});
</script>

<template>
  <Page auto-content-height title="媒体管理">
    <el-card shadow="never">
      <div v-show="showSearch" class="ocpx-query">
        <el-select
          v-model="query.mediaId"
          aria-label="按媒体查询"
          clearable
          filterable
          placeholder="请选择渠道"
        >
          <el-option
            v-for="item in mediaSelects"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-button :disabled="loading" type="primary" @click="search"
          >搜索</el-button
        >
      </div>
      <div class="ocpx-actions">
        <el-button
          v-access:code="'ocpx:media:add'"
          :disabled="loading || saving || Boolean(deletingId)"
          type="primary"
          @click="edit()"
          >新增媒体</el-button
        >
        <RightToolbar
          v-model:visible-columns="visibleColumns"
          :columns="columns"
          :loading="loading"
          refresh-btn
          search-btn
          @refresh="load"
          @search="showSearch = !showSearch"
        />
      </div>
      <el-alert
        class="mb-4"
        :closable="false"
        title="管理端只维护服务端凭据引用，不显示或提交明文密钥。"
        type="info"
      />
      <el-table
        v-loading="loading"
        class="ocpx-table"
        :data="rows"
        highlight-current-row
        max-height="560"
        size="small"
      >
        <el-table-column v-if="hasColumn('id')" label="渠道 ID" min-width="170"
          ><template #default="{ row }"><LongText :value="row.id" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('name')"
          label="渠道名称"
          min-width="150"
          prop="mediaName"
        />
        <el-table-column v-if="hasColumn('uri')" label="URI" min-width="200"
          ><template #default="{ row }"><LongText :value="row.uri" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('credential')"
          label="凭据引用"
          min-width="180"
          ><template #default="{ row }"
            ><LongText :value="row.credentialConfigKey" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('macro')"
          label="宏参数"
          min-width="220"
          ><template #default="{ row }"
            ><LongText :value="row.macroParams" /></template
        ></el-table-column>
        <el-table-column
          v-if="hasColumn('createTime')"
          label="创建时间"
          min-width="170"
          prop="createTime"
        />
        <el-table-column
          v-if="hasColumn('updateTime')"
          label="修改时间"
          min-width="170"
          prop="updateTime"
        />
        <el-table-column
          v-if="canOperate || hasAccessByCodes(['ocpx:rta:media:view'])"
          fixed="right"
          label="操作"
          width="130"
        >
          <template #default="{ row }">
            <el-button
              v-if="hasAccessByCodes(['ocpx:rta:media:view'])"
              link
              type="primary"
              @click="
                router.push({
                  query: { rtaMediaId: row.id },
                })
              "
              >RTA 配置</el-button
            >
            <el-button
              v-access:code="'ocpx:media:edit'"
              :disabled="saving || Boolean(deletingId)"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            >
            <el-button
              v-access:code="'ocpx:media:delete'"
              :disabled="saving || Boolean(deletingId)"
              :loading="deletingId === row.id"
              link
              type="danger"
              @click="remove(row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="ocpx-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="PAGE_SIZES"
        :total="total"
        @change="load"
      />
    </el-card>

    <el-dialog
      v-model="dialogOpen"
      :close-on-click-modal="false"
      :title="editing ? '编辑媒体' : '新增媒体'"
      width="720px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        class="ocpx-dialog-form"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item label="渠道名称" prop="mediaName"
          ><el-input v-model="form.mediaName" placeholder="请输入渠道名称"
        /></el-form-item>
        <el-form-item label="URI" prop="uri"
          ><el-input v-model="form.uri" placeholder="请输入URI"
        /></el-form-item>
        <el-form-item label="凭据引用" prop="credentialConfigKey"
          ><el-input
            v-model="form.credentialConfigKey"
            placeholder="请输入凭据引用"
        /></el-form-item>
        <el-form-item label="宏参数">
          <div class="macro-picker">
            <el-checkbox-group v-model="form.selectedMacros">
              <template v-for="group in MACRO_GROUPS" :key="group.name">
                <div class="macro-group-title">{{ group.name }}</div>
                <div class="macro-group">
                  <el-checkbox
                    v-for="item in group.items"
                    :key="item.key"
                    :value="item.key"
                  >
                    <span class="macro-label">{{ item.label }}</span>
                    <code class="macro-value"
                      >{{ item.key }}={{ item.macro }}</code
                    >
                  </el-checkbox>
                </div>
              </template>
            </el-checkbox-group>
          </div>
          <div class="macro-preview">
            预览：<code class="macro-preview-text">{{
              macroParams || '（未选择任何宏参数）'
            }}</code>
          </div>
          <el-input
            v-model="form.customMacros"
            autosize
            placeholder="自定义宏参数（可选）：key=__MACRO__ 形式，多个用 & 连接"
            type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer
        ><el-button :disabled="saving" @click="dialogOpen = false"
          >取消</el-button
        ><el-button :loading="saving" type="primary" @click="save"
          >确定</el-button
        ></template
      >
    </el-dialog>
    <RtaConfigurationDrawer kind="media" @saved="load" />
  </Page>
</template>

<style scoped>
.macro-picker {
  width: 100%;
}

.macro-group-title {
  margin: 10px 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.macro-group-title:first-child {
  margin-top: 0;
}

.macro-group {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 2px 16px;
}

.macro-label {
  margin-right: 4px;
}

.macro-value {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.macro-preview {
  padding: 6px 8px;
  margin: 10px 0;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.macro-preview-text {
  font-size: 11px;
}
</style>
