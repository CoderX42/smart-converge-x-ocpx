<script setup lang="ts">
import type { MediaProtocol } from './validation';

import type { RtaRow } from '#/api/ocpx/rta';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@boot/access';

import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteRta, queryRta, saveRta } from '#/api/ocpx/rta';

import AccessDoc from '../access-doc.vue';
import { defaultMediaConfig, mediaErrors } from './validation';

const props = defineProps<{ mediaId: string; mediaName?: string }>();
const emit = defineEmits<{ saved: [] }>();
const { hasAccessByCodes } = useAccess();
const canEdit = computed(() => hasAccessByCodes(['ocpx:rta:media:edit']));
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const saved = ref<RtaRow>();
const form = reactive<RtaRow>({});
const protocolCards: Array<{
  code: MediaProtocol;
  description: string;
  title: string;
}> = [
  {
    code: 'GENERIC_ASYNC',
    description: '202 受理后，通过决策通知地址异步推送结果',
    title: '通用异步',
  },
  {
    code: 'OPPO',
    description: '同一请求内同步返回允许投放的策略 ID',
    title: 'OPPO 同步',
  },
];

function fill(row?: RtaRow) {
  for (const key of Object.keys(form)) delete form[key as keyof RtaRow];
  Object.assign(
    form,
    {
      enabled: false,
      mediaId: props.mediaId,
      notifyUrl: '',
      protocol: 'GENERIC_ASYNC',
      version: 0,
    },
    row,
    {
      enabled: Boolean(row?.enabled),
      protocolConfig: row
        ? { ...row.protocolConfig }
        : defaultMediaConfig('GENERIC_ASYNC'),
    },
  );
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const page = await queryRta('media', {
      current: 1,
      mediaId: props.mediaId,
      size: 1,
    });
    saved.value = page.records.find((row) => row.mediaId === props.mediaId);
    fill(saved.value);
  } catch (error_) {
    error.value =
      error_ instanceof Error ? error_.message : '加载失败，请重新打开';
  } finally {
    loading.value = false;
  }
}

function chooseProtocol(code: MediaProtocol) {
  if (!canEdit.value || form.protocol === code) return;
  form.protocol = code;
  form.protocolConfig = defaultMediaConfig(code);
  if (code === 'OPPO') form.notifyUrl = '';
}

async function save() {
  const errors = mediaErrors(form);
  if (errors.length > 0) {
    ElMessage.warning(errors[0]!);
    return;
  }
  saving.value = true;
  try {
    await saveRta('media', {
      ...form,
      notifyUrl: form.protocol === 'OPPO' ? '' : form.notifyUrl,
    });
    ElMessage.success('媒体 RTA 接入已保存');
    await load();
    emit('saved');
  } finally {
    saving.value = false;
  }
}

async function remove() {
  if (!saved.value) return;
  try {
    await ElMessageBox.confirm(
      '删除后该媒体不再受理 RTA 请求；仍有任务绑定时无法删除。',
      '删除媒体 RTA 接入',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteRta('media', props.mediaId, Number(saved.value.version || 0));
  ElMessage.success('已删除媒体 RTA 接入');
  await load();
  emit('saved');
}

onMounted(load);
</script>

<template>
  <div v-loading="loading" class="rta-form">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-position="top" :disabled="!canEdit" @submit.prevent="save">
      <el-form-item label="媒体"
        >{{ mediaName || saved?.mediaName || mediaId }} ·
        {{ mediaId }}</el-form-item
      >
      <el-form-item label="媒体 RTA 协议" required>
        <div class="rta-cards" role="radiogroup">
          <button
            v-for="card in protocolCards"
            :key="card.code"
            type="button"
            role="radio"
            :aria-checked="form.protocol === card.code"
            class="rta-card"
            :class="[{ 'rta-card--active': form.protocol === card.code }]"
            @click="chooseProtocol(card.code)"
          >
            <strong>{{ card.title }}</strong>
            <span>{{ card.description }}</span>
          </button>
        </div>
      </el-form-item>
      <el-form-item label="服务端凭据引用" required>
        <el-input
          v-model="form.credentialRef"
          maxlength="64"
          placeholder="如 media_a_key"
        />
        <p class="rta-hint">
          填 Engine 配置 app.rta-relay.credentials 中的名称，不是密钥本身。{{
            form.protocol === 'OPPO'
              ? '它同时作为 OPPO RTA 地址中的接入编号。'
              : '媒体请求时作为 X-Rta-Key 请求头。'
          }}
        </p>
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
            placeholder="Engine 配置中的 OPPO 上传凭据名称"
          />
        </el-form-item>
      </template>
      <el-form-item v-else label="决策通知地址" required>
        <el-input
          v-model="form.notifyUrl"
          placeholder="https://允许的域名/路径"
        />
        <p class="rta-hint">
          必须是 HTTPS、不含查询参数，且域名在 app.rta-relay.allowed-hosts 中。
        </p>
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
        v-if="saved"
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
    <AccessDoc
      v-if="saved"
      :protocol="saved.protocol"
      :request-url="saved.requestUrl"
      :credential-ref="saved.credentialRef"
      :notify-url="saved.notifyUrl"
    />
    <el-empty
      v-else-if="!loading"
      :image-size="60"
      description="保存后生成对接资料"
    />
  </div>
</template>

<style scoped src="./form.css"></style>
