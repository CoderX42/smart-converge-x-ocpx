<script lang="ts" setup>
import type { DocumentPreviewKind } from './document-utils';

import type { IntegrationDocument } from '#/api/ad';

import {
  computed,
  defineAsyncComponent,
  nextTick,
  onUnmounted,
  ref,
  watch,
} from 'vue';

import { Download, RefreshRight, WarningFilled } from '@element-plus/icons-vue';
import DOMPurify from 'dompurify';
import { ElMessage } from 'element-plus';
import MarkdownIt from 'markdown-it';

import { getIntegrationDocumentContent } from '#/api/ad';

import { documentExtension, parseCsv, previewKind } from './document-utils';

import '@vue-office/docx/lib/index.css';
import '@vue-office/excel/lib/index.css';

defineOptions({ name: 'IntegrationDocumentPreview' });

const props = defineProps<{
  document?: IntegrationDocument;
  modelValue: boolean;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const PdfPreview = defineAsyncComponent(() => import('@vue-office/pdf'));
const DocxPreview = defineAsyncComponent(() => import('@vue-office/docx'));
const ExcelPreview = defineAsyncComponent(() => import('@vue-office/excel'));

const loading = ref(false);
const errorMessage = ref('');
const blob = ref<Blob>();
const blobUrl = ref('');
const textContent = ref('');
const renderedMarkdown = ref('');
const csvRows = ref<string[][]>([]);
const previewContainer = ref<HTMLElement>();
const markdown = new MarkdownIt({
  breaks: true,
  html: false,
  linkify: true,
  typographer: true,
});
const vSafeHtml = {
  mounted(element: HTMLElement, binding: { value: string }) {
    element.innerHTML = binding.value;
  },
  updated(element: HTMLElement, binding: { value: string }) {
    element.innerHTML = binding.value;
  },
};

const kind = computed<DocumentPreviewKind>(() =>
  props.document ? previewKind(props.document) : 'fallback',
);
const fileName = computed(
  () => props.document?.originalFileName || props.document?.name || '文档',
);
const csvHeaders = computed(() => csvRows.value[0] || []);
const csvBody = computed(() => csvRows.value.slice(1, 501));
const isOffice = computed(() => ['docx', 'excel', 'pdf'].includes(kind.value));

function releaseBlobUrl() {
  if (blobUrl.value) URL.revokeObjectURL(blobUrl.value);
  blobUrl.value = '';
}

function resetContent() {
  releaseBlobUrl();
  blob.value = undefined;
  textContent.value = '';
  renderedMarkdown.value = '';
  csvRows.value = [];
  errorMessage.value = '';
}

function readableError(error: unknown) {
  const failure = error as {
    message?: string;
    response?: { data?: { msg?: string } };
  };
  return failure.response?.data?.msg || failure.message || '文档内容读取失败';
}

async function loadContent() {
  if (!props.document || props.document.storageType !== 'OBJECT') return;
  resetContent();
  loading.value = true;
  try {
    const content = await getIntegrationDocumentContent(props.document.id);
    blob.value = content;
    blobUrl.value = URL.createObjectURL(content);
    if (!isOffice.value && kind.value !== 'fallback') {
      const source = await content.text();
      if (kind.value === 'json') {
        try {
          textContent.value = JSON.stringify(JSON.parse(source), null, 2);
        } catch {
          textContent.value = source;
        }
      } else {
        textContent.value = source;
      }
      if (kind.value === 'markdown') {
        renderedMarkdown.value = DOMPurify.sanitize(markdown.render(source), {
          USE_PROFILES: { html: true },
        });
      }
      if (kind.value === 'csv') csvRows.value = parseCsv(source);
    }
    await nextTick();
    previewContainer.value?.focus();
  } catch (error) {
    errorMessage.value = readableError(error);
  } finally {
    loading.value = false;
  }
}

function close() {
  emit('update:modelValue', false);
}

function handleRenderError() {
  errorMessage.value = '当前文件无法完成在线渲染，你仍可下载原文件后查看。';
}

async function download() {
  if (!blobUrl.value) {
    await loadContent();
    if (!blobUrl.value) {
      ElMessage.error('文档尚未加载，暂时无法下载');
      return;
    }
  }
  const anchor = window.document.createElement('a');
  anchor.href = blobUrl.value;
  anchor.download = fileName.value;
  anchor.rel = 'noopener';
  anchor.click();
  ElMessage.success('已开始下载');
}

watch(
  () => [props.modelValue, props.document?.id] as const,
  ([visible]) => {
    if (visible) void loadContent();
    else resetContent();
  },
  { immediate: true },
);
onUnmounted(resetContent);
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    append-to-body
    class="document-preview-drawer"
    direction="rtl"
    size="100%"
    :show-close="false"
    @close="close"
  >
    <template #header>
      <div class="preview-header">
        <div class="preview-title">
          <span class="preview-kicker">安全在线预览</span>
          <strong>{{ document?.name }}</strong>
          <span>{{ fileName }}</span>
        </div>
        <div class="preview-actions">
          <el-button
            :icon="Download"
            :disabled="loading || !blob"
            @click="download"
          >
            下载
          </el-button>
          <el-button type="primary" plain @click="close">关闭预览</el-button>
        </div>
      </div>
    </template>

    <section
      ref="previewContainer"
      v-loading="loading"
      class="preview-stage"
      tabindex="-1"
      :aria-busy="loading"
      aria-live="polite"
    >
      <el-empty
        v-if="errorMessage"
        :description="errorMessage"
        :image-size="96"
      >
        <template #image>
          <el-icon class="preview-warning"><WarningFilled /></el-icon>
        </template>
        <el-button :icon="RefreshRight" type="primary" @click="loadContent">
          重新加载
        </el-button>
        <el-button v-if="blobUrl" @click="download">改为下载</el-button>
      </el-empty>

      <template v-else-if="blob">
        <Suspense v-if="kind === 'pdf'">
          <PdfPreview
            :src="blob"
            class="office-preview"
            @error="handleRenderError"
          />
          <template #fallback><el-skeleton :rows="12" animated /></template>
        </Suspense>
        <Suspense v-else-if="kind === 'docx'">
          <DocxPreview
            :src="blob"
            class="office-preview"
            @error="handleRenderError"
          />
          <template #fallback><el-skeleton :rows="12" animated /></template>
        </Suspense>
        <Suspense v-else-if="kind === 'excel'">
          <ExcelPreview
            :src="blob"
            class="office-preview"
            :options="{ showContextmenu: false }"
            @error="handleRenderError"
          />
          <template #fallback><el-skeleton :rows="12" animated /></template>
        </Suspense>

        <article
          v-else-if="kind === 'markdown'"
          v-safe-html="renderedMarkdown"
          class="markdown-preview"
        ></article>

        <div v-else-if="kind === 'csv'" class="csv-preview">
          <el-alert
            v-if="csvRows.length > 501"
            title="为保证浏览性能，仅展示前 500 行；下载可查看完整文档。"
            type="info"
            :closable="false"
          />
          <el-table :data="csvBody" border height="calc(100vh - 210px)">
            <el-table-column
              v-for="(header, index) in csvHeaders"
              :key="`${header}-${index}`"
              :label="header || `第 ${index + 1} 列`"
              min-width="160"
            >
              <template #default="{ row }">{{ row[index] }}</template>
            </el-table-column>
          </el-table>
        </div>

        <pre
          v-else-if="['json', 'text', 'yaml'].includes(kind)"
          class="code-preview"
        ><code>{{ textContent }}</code></pre>

        <el-empty
          v-else
          :description="`.${documentExtension(fileName) || '未知'} 格式无法安全在线解析`"
        >
          <el-button type="primary" :icon="Download" @click="download">
            下载后查看
          </el-button>
        </el-empty>
      </template>
    </section>
  </el-drawer>
</template>

<style scoped>
.preview-header {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.preview-title {
  display: grid;
  min-width: 0;
}

.preview-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 18px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.preview-title > span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.preview-kicker {
  font-size: 11px !important;
  font-weight: 700;
  color: var(--el-color-primary) !important;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.preview-actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.preview-stage {
  min-height: calc(100vh - 96px);
  padding: 20px;
  overflow: auto;
  outline: none;
  background: var(--el-fill-color-light);
}

.office-preview,
.markdown-preview,
.code-preview,
.csv-preview {
  min-height: calc(100vh - 136px);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  box-shadow: var(--el-box-shadow-light);
}

.markdown-preview {
  max-width: 1040px;
  padding: 48px clamp(24px, 6vw, 88px);
  margin: 0 auto;
  line-height: 1.75;
}

.markdown-preview :deep(img) {
  max-width: 100%;
}

.markdown-preview :deep(pre),
.code-preview {
  font-family: SFMono-Regular, Consolas, 'Liberation Mono', monospace;
}

.code-preview {
  padding: 28px;
  margin: 0;
  overflow: auto;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.csv-preview {
  padding: 16px;
}

.csv-preview .el-alert {
  margin-bottom: 12px;
}

.preview-warning {
  font-size: 72px;
  color: var(--el-color-warning);
}

@media (max-width: 640px) {
  .preview-header {
    align-items: flex-start;
  }

  .preview-actions .el-button:first-child {
    display: none;
  }

  .preview-stage {
    padding: 8px;
  }

  .markdown-preview {
    padding: 24px 18px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .preview-stage *,
  .preview-stage *::before,
  .preview-stage *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
