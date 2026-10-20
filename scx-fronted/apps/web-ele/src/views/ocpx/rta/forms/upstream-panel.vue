<script setup lang="ts">
import type { RtaProtocol, RtaRow } from '#/api/ocpx/rta';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';

import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteRta, queryRta, rtaProtocols, saveRta } from '#/api/ocpx/rta';

import { decisionModes, protocols as protocolLabels, tagOf } from '../labels';
import { upstreamErrors } from './validation';

const props = defineProps<{
  advertiserId: string;
  advertiserName?: string;
  selectId?: string;
}>();
const emit = defineEmits<{ saved: [] }>();
const { hasAccessByCodes } = useAccess();
const canEdit = computed(() => hasAccessByCodes(['ocpx:rta:upstreams:edit']));
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const rows = ref<RtaRow[]>([]);
const protocols = ref<RtaProtocol[]>([]);
const selectedId = ref<string>();
const form = reactive<RtaRow>({});
const editing = ref(false);
const selectedProtocol = computed(() =>
  protocols.value.find((item) => item.code === form.protocol),
);

function fill(row?: RtaRow) {
  for (const key of Object.keys(form)) delete form[key as keyof RtaRow];
  const protocol = row?.protocol || protocols.value[0]?.code;
  Object.assign(
    form,
    {
      admissionSeconds: 300,
      advertiserId: props.advertiserId,
      enabled: false,
      protocol,
      timeoutMs: 5000,
      version: 0,
    },
    row,
    {
      enabled: Boolean(row?.enabled),
      endpoint:
        row?.endpoint ||
        protocols.value.find((item) => item.code === protocol)?.fixedEndpoint ||
        '',
      protocolConfig: { ...row?.protocolConfig },
    },
  );
  selectedId.value = row?.id;
  editing.value = true;
}

async function load(selectId?: string) {
  loading.value = true;
  error.value = '';
  try {
    const page = await queryRta('upstreams', {
      advertiserId: props.advertiserId,
      current: 1,
      size: 100,
    });
    rows.value = page.records;
    const target = rows.value.find((row) => row.id === selectId);
    if (target) fill(target);
    else if (rows.value.length === 0) fill();
    else fill(rows.value[0]);
  } catch (error_) {
    error.value =
      error_ instanceof Error ? error_.message : '加载失败，请重新打开';
  } finally {
    loading.value = false;
  }
}

function chooseProtocol(code: string) {
  if (!canEdit.value || form.id || form.protocol === code) return;
  form.protocol = code;
  form.protocolConfig = {};
  form.credentialRef = '';
  form.endpoint = selectedProtocol.value?.fixedEndpoint || '';
}

async function save() {
  const errors = upstreamErrors(form, selectedProtocol.value);
  if (errors.length > 0) {
    ElMessage.warning(errors[0]!);
    return;
  }
  saving.value = true;
  try {
    await saveRta('upstreams', {
      ...form,
      credentialRef: selectedProtocol.value?.credentialRequired
        ? form.credentialRef
        : '',
    });
    ElMessage.success('广告主 RTA 接入已保存');
    await load(form.id);
    emit('saved');
  } finally {
    saving.value = false;
  }
}

async function remove() {
  if (!form.id) return;
  try {
    await ElMessageBox.confirm(
      `确定删除接入“${form.name}”？仍有任务绑定时无法删除。`,
      '删除广告主 RTA 接入',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteRta('upstreams', form.id, Number(form.version || 0));
  ElMessage.success('已删除广告主 RTA 接入');
  await load();
  emit('saved');
}

async function copy(text?: string) {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制');
  } catch {
    ElMessage.error('复制失败，请手动复制');
  }
}

onMounted(async () => {
  try {
    protocols.value = await rtaProtocols();
  } catch {
    error.value = '协议加载失败，请关闭后重试';
    return;
  }
  await load(props.selectId);
});
</script>

<template>
  <div v-loading="loading" class="rta-upstream">
    <aside class="rta-upstream__list">
      <div class="rta-upstream__list-head">
        <strong>{{ advertiserName || advertiserId }}</strong>
        <el-button
          v-if="canEdit"
          size="small"
          type="primary"
          plain
          :disabled="protocols.length === 0"
          @click="fill()"
          >新增接入</el-button
        >
      </div>
      <el-empty
        v-if="rows.length === 0 && !loading"
        :image-size="60"
        description="该广告主还没有 RTA 接入"
      />
      <button
        v-for="row in rows"
        :key="row.id"
        type="button"
        class="rta-card"
        :class="[{ 'rta-card--active': editing && selectedId === row.id }]"
        @click="fill(row)"
      >
        <strong>{{ row.name }}</strong>
        <span>
          <el-tag size="small" :type="tagOf(protocolLabels, row.protocol).type">
            {{ tagOf(protocolLabels, row.protocol).text }}
          </el-tag>
          <el-tag
            size="small"
            class="ml-1"
            :type="row.enabled ? 'success' : 'info'"
            >{{ row.enabled ? '已启用' : '已停用' }}</el-tag
          >
        </span>
      </button>
    </aside>
    <section class="rta-upstream__form rta-form">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template v-if="editing">
        <p class="rta-section-title">
          {{ form.id ? `编辑：${form.name}` : '新增广告主 RTA 接入' }}
        </p>
        <el-form
          label-position="top"
          :disabled="!canEdit"
          @submit.prevent="save"
        >
          <el-form-item label="接入名称" required>
            <el-input v-model="form.name" maxlength="128" />
          </el-form-item>
          <el-form-item label="RTA 协议" required>
            <div class="rta-cards" role="radiogroup">
              <button
                v-for="item in protocols"
                :key="item.code"
                type="button"
                role="radio"
                :aria-checked="form.protocol === item.code"
                :disabled="!!form.id && form.protocol !== item.code"
                class="rta-card"
                :class="[{ 'rta-card--active': form.protocol === item.code }]"
                @click="chooseProtocol(item.code)"
              >
                <strong>{{ item.name }}</strong>
                <span
                  >{{ decisionModes[item.mode] || item.mode }} · 每次最多
                  {{ item.maxCandidates }} 个任务 ·
                  {{ item.credentialRequired ? '需要凭据' : '无需凭据' }}</span
                >
              </button>
            </div>
            <p v-if="form.id" class="rta-hint">已保存的接入不可更换协议。</p>
          </el-form-item>
          <el-form-item
            v-for="field in selectedProtocol?.configFields || []"
            :key="field.key"
            :label="field.label"
            required
          >
            <el-input
              v-if="form.protocolConfig"
              v-model="form.protocolConfig[field.key]"
              maxlength="128"
            />
          </el-form-item>
          <el-form-item label="广告主请求地址" required>
            <el-input
              v-model="form.endpoint"
              :readonly="!!selectedProtocol?.fixedEndpoint"
              placeholder="https://允许的域名/路径"
            />
            <p v-if="selectedProtocol?.fixedEndpoint" class="rta-hint">
              该协议使用固定地址。
            </p>
          </el-form-item>
          <el-form-item
            v-if="selectedProtocol?.credentialRequired"
            label="服务端凭据引用"
            required
          >
            <el-input
              v-model="form.credentialRef"
              maxlength="64"
              placeholder="如 advertiser_a_key"
            />
            <p class="rta-hint">
              填 Engine 配置 app.rta-relay.credentials
              中的名称，不是密钥本身；SCX 用它签名请求并校验回调。
            </p>
          </el-form-item>
          <div class="rta-upstream__numbers">
            <el-form-item label="等待时限 / 毫秒">
              <el-input-number
                v-model="form.timeoutMs"
                :min="100"
                :max="30000"
              />
            </el-form-item>
            <el-form-item label="放行有效期 / 秒">
              <el-input-number
                v-model="form.admissionSeconds"
                :min="1"
                :max="86400"
              />
            </el-form-item>
          </div>
          <el-form-item
            v-if="
              form.callbackUrl && selectedProtocol?.mode === 'ASYNC_CALLBACK'
            "
            label="SCX 决策回调地址（交给广告主）"
          >
            <el-input :model-value="form.callbackUrl" readonly>
              <template #append>
                <el-button @click="copy(form.callbackUrl)">复制</el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="状态">
            <el-switch
              v-model="form.enabled"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
        </el-form>
        <div v-if="canEdit" class="rta-actions">
          <el-button
            v-if="form.id"
            type="danger"
            plain
            :disabled="saving"
            @click="remove"
            >删除接入</el-button
          >
          <el-button type="primary" :loading="saving" @click="save"
            >保存配置</el-button
          >
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped src="./form.css"></style>
<style scoped>
.rta-upstream {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 20px;
  min-height: 100%;
}

.rta-upstream__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 16px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.rta-upstream__list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.rta-upstream__numbers {
  display: flex;
  gap: 16px;
}

@media (width <= 768px) {
  .rta-upstream {
    grid-template-columns: 1fr;
  }

  .rta-upstream__list {
    padding-right: 0;
    border-right: none;
  }
}
</style>
