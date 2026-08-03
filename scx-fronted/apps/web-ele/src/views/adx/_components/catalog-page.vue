<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { Advertiser, AppLink, CatalogKind, Channel } from '#/api/adx';

import { computed, onMounted, reactive, ref } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteCatalog,
  getCatalog,
  rechargeAdvertiser,
  saveCatalog,
} from '#/api/adx';

defineOptions({ name: 'AdxCatalogPage' });

const props = defineProps<{ kind: CatalogKind }>();

type Row = Record<string, any> & { id: string };
type Column = {
  label: string;
  minWidth?: number;
  prop: string;
  width?: number;
};

const current = ref(1);
const size = ref(20);
const total = ref(0);
const keyword = ref('');
const loading = ref(false);
const saving = ref(false);
const rows = ref<Row[]>([]);
const dialog = ref(false);
const rechargeDialog = ref(false);
const rechargeAmount = ref<number>();
const rechargeTarget = ref<Row>();
const formRef = ref<FormInstance>();
const form = reactive<Record<string, any>>({});
const channels = ref<Channel[]>([]);
const advertisers = ref<Advertiser[]>([]);
const appLinks = ref<AppLink[]>([]);

const config = computed(() => {
  const values = {
    advertiser: {
      add: '新增广告主',
      description: '维护竞价接口、登录账号和账户余额。密钥与密码只写不回显。',
      permission: 'adx:advertiser:write',
      title: '广告主管理',
    },
    appLink: {
      add: '新增应用链接',
      description: '配置应用下载、落地页、DeepLink 与扩展参数。',
      permission: 'adx:app-link:write',
      title: '应用链接管理',
    },
    channel: {
      add: '新增渠道',
      description: '维护渠道入口、签名密钥与宏参数，供监测链接自动生成。',
      permission: 'adx:channel:write',
      title: '渠道管理',
    },
    deduction: {
      add: '新增扣量规则',
      description: '按渠道与应用链接配置数据保留比例，范围为 0 到 1。',
      permission: 'adx:deduction:write',
      title: '扣量管理',
    },
    monitor: {
      add: '新增监测链接',
      description: '关联渠道、广告主和应用链接，自动生成展示与点击监测地址。',
      permission: 'adx:monitor:write',
      title: '监测链接管理',
    },
  } as const;
  return values[props.kind];
});

const columns = computed<Column[]>(() => {
  const values: Record<CatalogKind, Column[]> = {
    advertiser: [
      { label: '广告主ID', minWidth: 170, prop: 'id' },
      { label: '广告主名称', minWidth: 150, prop: 'advertiserName' },
      { label: '登录账号', minWidth: 140, prop: 'account' },
      { label: '余额', minWidth: 120, prop: 'balance' },
      { label: '竞价地址', minWidth: 240, prop: 'bidUrl' },
      { label: '密钥', minWidth: 90, prop: 'secretConfigured' },
      { label: '宏参数', minWidth: 220, prop: 'macroParams' },
      { label: '修改时间', minWidth: 170, prop: 'updateTime' },
    ],
    appLink: [
      { label: '链接ID', minWidth: 170, prop: 'id' },
      { label: '广告主', minWidth: 150, prop: 'advertiserName' },
      { label: '链接名称', minWidth: 160, prop: 'linkName' },
      { label: '平台', minWidth: 110, prop: 'linkPlatform' },
      { label: '渠道标识', minWidth: 130, prop: 'linkChannel' },
      { label: '应用链接', minWidth: 260, prop: 'linkUrl' },
      { label: 'DeepLink', minWidth: 240, prop: 'deeplink' },
      { label: '修改时间', minWidth: 170, prop: 'updateTime' },
    ],
    channel: [
      { label: '渠道ID', minWidth: 170, prop: 'id' },
      { label: '渠道名称', minWidth: 160, prop: 'channelName' },
      { label: 'URI', minWidth: 280, prop: 'uri' },
      { label: '密钥', minWidth: 90, prop: 'secretConfigured' },
      { label: '宏参数', minWidth: 240, prop: 'macroParams' },
      { label: '创建时间', minWidth: 170, prop: 'createTime' },
      { label: '修改时间', minWidth: 170, prop: 'updateTime' },
    ],
    deduction: [
      { label: '规则ID', minWidth: 170, prop: 'id' },
      { label: '渠道', minWidth: 160, prop: 'channelName' },
      { label: '应用链接', minWidth: 180, prop: 'appLinkName' },
      { label: '保留比例', minWidth: 120, prop: 'ratio' },
      { label: '创建时间', minWidth: 170, prop: 'createTime' },
      { label: '修改时间', minWidth: 170, prop: 'updateTime' },
    ],
    monitor: [
      { label: '监测ID', minWidth: 170, prop: 'id' },
      { label: '监测名称', minWidth: 160, prop: 'monitorName' },
      { label: '渠道', minWidth: 140, prop: 'channelName' },
      { label: '广告主', minWidth: 140, prop: 'advertiserName' },
      { label: '应用链接', minWidth: 160, prop: 'appLinkName' },
      { label: '业务ID', minWidth: 130, prop: 'bizId' },
      { label: '单价', minWidth: 110, prop: 'unitPrice' },
      { label: '展示链接', minWidth: 260, prop: 'showUrl' },
      { label: '点击链接', minWidth: 260, prop: 'clickUrl' },
      { label: '修改时间', minWidth: 170, prop: 'updateTime' },
    ],
  };
  return values[props.kind];
});

const rules = computed<FormRules>(() => {
  const required: Record<CatalogKind, string[]> = {
    advertiser: ['advertiserName', 'bidUrl', 'account'],
    appLink: ['advertiserId', 'linkPlatform', 'linkName', 'linkUrl'],
    channel: ['channelName', 'uri', 'macroParams'],
    deduction: ['channelId', 'appLinkId', 'ratio'],
    monitor: [
      'channelId',
      'advertiserId',
      'appLinkId',
      'monitorName',
      'unitPrice',
    ],
  };
  return Object.fromEntries(
    required[props.kind].map((field) => [
      field,
      { message: '此项为必填项', required: true, trigger: 'blur' },
    ]),
  );
});

const filteredAppLinks = computed(() => {
  if (!form.advertiserId) return appLinks.value;
  return appLinks.value.filter(
    (item) => item.advertiserId === form.advertiserId,
  );
});

function display(row: Row, prop: string) {
  const value = row[prop];
  if (prop === 'secretConfigured') return value ? '已配置' : '未配置';
  if (prop === 'linkPlatform') {
    return { 1: 'Android', 2: 'iOS', 3: 'Android + iOS', 4: '快应用' }[
      value as 1 | 2 | 3 | 4
    ];
  }
  if (prop === 'ratio') return `${(Number(value || 0) * 100).toFixed(2)}%`;
  if (prop === 'balance' || prop === 'unitPrice') {
    return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`;
  }
  return value ?? '—';
}

async function loadDependencies() {
  const [channelResult, advertiserResult, linkResult] = await Promise.all([
    getCatalog('channel', { current: 1, size: 500 }),
    getCatalog('advertiser', { current: 1, size: 500 }),
    getCatalog('appLink', { current: 1, size: 1000 }),
  ]);
  channels.value = channelResult.records;
  advertisers.value = advertiserResult.records;
  appLinks.value = linkResult.records;
}

async function load() {
  loading.value = true;
  try {
    const result = await getCatalog(props.kind, {
      current: current.value,
      keyword: keyword.value || undefined,
      size: size.value,
    });
    rows.value = result.records as Row[];
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key]);
  const defaults: Record<CatalogKind, Record<string, any>> = {
    advertiser: { balance: 0 },
    appLink: { linkPlatform: 1 },
    channel: {},
    deduction: { ratio: 1 },
    monitor: { unitPrice: 0 },
  };
  Object.assign(form, defaults[props.kind]);
}

function openEditor(row?: Row) {
  resetForm();
  if (row) Object.assign(form, row);
  dialog.value = true;
}

async function save() {
  if (!(await formRef.value?.validate())) return;
  if (props.kind === 'advertiser' && !form.id && !form.password) {
    ElMessage.warning('新增广告主时密码必填，至少 8 位');
    return;
  }
  saving.value = true;
  try {
    await saveCatalog(props.kind, { ...form } as never);
    ElMessage.success('保存成功');
    dialog.value = false;
    await Promise.all([load(), loadDependencies()]);
  } finally {
    saving.value = false;
  }
}

async function remove(row: Row) {
  await ElMessageBox.confirm('删除后不可在页面恢复，确认继续？', '确认删除', {
    type: 'warning',
  });
  await deleteCatalog(props.kind, row.id);
  ElMessage.success('已删除');
  await Promise.all([load(), loadDependencies()]);
}

function openRecharge(row: Row) {
  rechargeTarget.value = row;
  rechargeAmount.value = undefined;
  rechargeDialog.value = true;
}

async function recharge() {
  if (
    !rechargeTarget.value ||
    !rechargeAmount.value ||
    rechargeAmount.value <= 0
  ) {
    ElMessage.warning('请输入大于 0 的充值金额');
    return;
  }
  await rechargeAdvertiser(rechargeTarget.value.id, rechargeAmount.value);
  ElMessage.success('充值成功');
  rechargeDialog.value = false;
  await load();
}

function copy(value?: string) {
  if (!value) return;
  navigator.clipboard.writeText(value);
  ElMessage.success('链接已复制');
}

onMounted(async () => {
  await Promise.all([load(), loadDependencies()]);
});
</script>

<template>
  <div class="adx-page">
    <section class="page-heading">
      <div>
        <p class="eyebrow">ADX · MANAGEMENT</p>
        <h1>{{ config.title }}</h1>
        <p>{{ config.description }}</p>
      </div>
      <el-button
        v-access:code="config.permission"
        type="primary"
        @click="openEditor()"
      >
        {{ config.add }}
      </el-button>
    </section>

    <el-card shadow="never" class="data-card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="输入名称、账号或地址搜索"
          @keyup.enter="load"
        />
        <el-button type="primary" :loading="loading" @click="load">
          查询
        </el-button>
        <el-button
          @click="
            keyword = '';
            load();
          "
        >
          重置
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        border
        height="calc(100vh - 310px)"
      >
        <el-table-column
          v-for="column in columns"
          :key="column.prop"
          :label="column.label"
          :min-width="column.minWidth"
          :prop="column.prop"
          :width="column.width"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <el-tag
              v-if="column.prop === 'secretConfigured'"
              :type="row.secretConfigured ? 'success' : 'info'"
              size="small"
            >
              {{ display(row, column.prop) }}
            </el-tag>
            <span v-else>{{ display(row, column.prop) }}</span>
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="210">
          <template #default="{ row }">
            <el-button
              v-access:code="config.permission"
              link
              type="primary"
              @click="openEditor(row)"
              >编辑</el-button
            >
            <el-button
              v-if="kind === 'advertiser'"
              v-access:code="config.permission"
              link
              type="success"
              @click="openRecharge(row)"
            >
              充值
            </el-button>
            <el-button
              v-if="kind === 'monitor'"
              link
              @click="copy(row.clickUrl)"
            >
              复制
            </el-button>
            <el-button
              v-access:code="config.permission"
              link
              type="danger"
              @click="remove(row)"
              >删除</el-button
            >
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
      v-model="dialog"
      :close-on-click-modal="false"
      :title="form.id ? `编辑${config.title}` : config.add"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="118px">
        <template v-if="kind === 'channel'">
          <el-form-item label="渠道名称" prop="channelName">
            <el-input v-model="form.channelName" maxlength="100" />
          </el-form-item>
          <el-form-item label="URI" prop="uri">
            <el-input v-model="form.uri" placeholder="https://..." />
          </el-form-item>
          <el-form-item label="渠道密钥">
            <el-input
              v-model="form.secretKey"
              show-password
              :placeholder="form.id ? '留空表示不修改' : '可选'"
            />
          </el-form-item>
          <el-form-item label="宏参数" prop="macroParams">
            <el-input v-model="form.macroParams" :rows="3" type="textarea" />
          </el-form-item>
        </template>

        <template v-else-if="kind === 'advertiser'">
          <el-form-item label="广告主名称" prop="advertiserName">
            <el-input v-model="form.advertiserName" maxlength="100" />
          </el-form-item>
          <el-form-item label="竞价地址" prop="bidUrl">
            <el-input v-model="form.bidUrl" placeholder="https://..." />
          </el-form-item>
          <el-form-item label="广告主密钥">
            <el-input
              v-model="form.secretKey"
              show-password
              :placeholder="form.id ? '留空表示不修改' : '可选'"
            />
          </el-form-item>
          <el-form-item label="宏参数">
            <el-input v-model="form.macroParams" :rows="3" type="textarea" />
          </el-form-item>
          <el-form-item label="登录账号" prop="account">
            <el-input v-model="form.account" />
          </el-form-item>
          <el-form-item label="登录密码" :required="!form.id">
            <el-input
              v-model="form.password"
              minlength="8"
              show-password
              :placeholder="form.id ? '留空表示不修改' : '至少 8 位'"
            />
          </el-form-item>
        </template>

        <template v-else-if="kind === 'appLink'">
          <el-form-item label="所属广告主" prop="advertiserId">
            <el-select v-model="form.advertiserId" filterable>
              <el-option
                v-for="item in advertisers"
                :key="item.id"
                :label="item.advertiserName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="应用平台" prop="linkPlatform">
            <el-radio-group v-model="form.linkPlatform">
              <el-radio-button :value="1">Android</el-radio-button>
              <el-radio-button :value="2">iOS</el-radio-button>
              <el-radio-button :value="3">双端</el-radio-button>
              <el-radio-button :value="4">快应用</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="链接名称" prop="linkName">
            <el-input v-model="form.linkName" />
          </el-form-item>
          <el-form-item label="渠道标识">
            <el-input v-model="form.linkChannel" />
          </el-form-item>
          <el-form-item label="应用链接" prop="linkUrl">
            <el-input v-model="form.linkUrl" />
          </el-form-item>
          <el-form-item label="DeepLink">
            <el-input v-model="form.deeplink" />
          </el-form-item>
          <el-form-item label="扩展参数">
            <el-input v-model="form.linkExt" :rows="3" type="textarea" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.linkRemark" :rows="2" type="textarea" />
          </el-form-item>
        </template>

        <template v-else-if="kind === 'monitor'">
          <el-form-item label="所属渠道" prop="channelId">
            <el-select v-model="form.channelId" filterable>
              <el-option
                v-for="item in channels"
                :key="item.id"
                :label="item.channelName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="所属广告主" prop="advertiserId">
            <el-select v-model="form.advertiserId" filterable>
              <el-option
                v-for="item in advertisers"
                :key="item.id"
                :label="item.advertiserName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="投放链接" prop="appLinkId">
            <el-select v-model="form.appLinkId" filterable>
              <el-option
                v-for="item in filteredAppLinks"
                :key="item.id"
                :label="item.linkName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="监测名称" prop="monitorName">
            <el-input v-model="form.monitorName" />
          </el-form-item>
          <el-form-item label="业务ID">
            <el-input v-model="form.bizId" />
          </el-form-item>
          <el-form-item label="单价" prop="unitPrice">
            <el-input-number v-model="form.unitPrice" :min="0" :precision="6" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" :rows="3" type="textarea" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="所属渠道" prop="channelId">
            <el-select v-model="form.channelId" filterable>
              <el-option
                v-for="item in channels"
                :key="item.id"
                :label="item.channelName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="应用链接" prop="appLinkId">
            <el-select v-model="form.appLinkId" filterable>
              <el-option
                v-for="item in appLinks"
                :key="item.id"
                :label="`${item.advertiserName} · ${item.linkName}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="保留比例" prop="ratio">
            <el-input-number
              v-model="form.ratio"
              :max="1"
              :min="0"
              :precision="4"
              :step="0.01"
            />
            <span class="field-hint">1.0 表示不扣量，0.8 表示保留 80%</span>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          确认
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rechargeDialog" title="广告主充值" width="440px">
      <el-form label-width="100px">
        <el-form-item label="广告主">
          {{ rechargeTarget?.advertiserName }}
        </el-form-item>
        <el-form-item label="充值金额" required>
          <el-input-number
            v-model="rechargeAmount"
            :min="0.01"
            :precision="2"
            controls-position="right"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeDialog = false">取消</el-button>
        <el-button type="primary" @click="recharge">确认充值</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.adx-page {
  min-height: 100%;
  padding: 20px;
  background: #f5f7fa;
}

.page-heading {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-heading h1 {
  margin: 2px 0 6px;
  font-size: 24px;
  font-weight: 650;
  color: #172033;
}

.page-heading p {
  margin: 0;
  color: #667085;
}

.eyebrow {
  font-size: 11px;
  font-weight: 700;
  color: #2563eb !important;
  letter-spacing: 0.14em;
}

.data-card {
  border-color: #e6eaf0;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.toolbar .el-input {
  width: 360px;
}

.el-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.el-select {
  width: 100%;
}

.field-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #98a2b3;
}

@media (max-width: 768px) {
  .adx-page {
    padding: 12px;
  }

  .page-heading {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar {
    flex-wrap: wrap;
  }

  .toolbar .el-input {
    width: 100%;
  }
}
</style>
