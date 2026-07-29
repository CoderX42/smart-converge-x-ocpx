import type {
  IntegrationDocument,
  IntegrationDocumentStorageType,
} from '#/api/ad';

export const DOCUMENT_MAX_SIZE = 20 * 1024 * 1024;
export const DOCUMENT_EXTENSIONS = [
  'csv',
  'doc',
  'docx',
  'json',
  'md',
  'pdf',
  'txt',
  'xlsx',
  'yaml',
  'yml',
] as const;

export type DocumentPreviewKind =
  | 'csv'
  | 'docx'
  | 'excel'
  | 'fallback'
  | 'json'
  | 'markdown'
  | 'pdf'
  | 'text'
  | 'yaml';

export function documentExtension(fileName?: string) {
  const extension = fileName?.split('.').pop()?.trim().toLowerCase();
  return extension && extension !== fileName ? extension : '';
}

export function validateDocumentFile(file: Pick<File, 'name' | 'size'>) {
  if (file.size <= 0) return '不能上传空文件';
  if (file.size > DOCUMENT_MAX_SIZE) return '文档大小不能超过 20MB';
  const extension = documentExtension(file.name);
  if (
    !extension ||
    !DOCUMENT_EXTENSIONS.includes(
      extension as (typeof DOCUMENT_EXTENSIONS)[number],
    )
  ) {
    return '仅支持 PDF、DOC/DOCX、XLSX、CSV、TXT、MD、JSON、YAML/YML';
  }
  return '';
}

export function validateExternalUrl(value?: string) {
  if (!value?.trim()) return '请粘贴外部文档链接';
  try {
    const url = new URL(value.trim());
    if (!['http:', 'https:'].includes(url.protocol)) {
      return '仅支持 HTTP 或 HTTPS 链接';
    }
    if (!url.hostname) return '链接必须包含有效域名';
    if (url.username || url.password) return '链接不能包含用户名或密码';
    return '';
  } catch {
    return '请输入完整、合法的 HTTP/HTTPS 链接';
  }
}

export function previewKind(
  document: IntegrationDocument,
): DocumentPreviewKind {
  const extension = documentExtension(document.originalFileName);
  if (extension === 'pdf' || document.mimeType === 'application/pdf') {
    return 'pdf';
  }
  if (extension === 'docx') return 'docx';
  if (extension === 'xlsx') return 'excel';
  if (extension === 'md') return 'markdown';
  if (extension === 'csv') return 'csv';
  if (extension === 'json') return 'json';
  if (extension === 'yaml' || extension === 'yml') return 'yaml';
  if (extension === 'txt' || document.mimeType?.startsWith('text/')) {
    return 'text';
  }
  return 'fallback';
}

export function formatFileSize(size?: number) {
  if (size === undefined || size === null) return '—';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function externalDomain(url?: string) {
  if (!url) return '外部链接';
  try {
    return new URL(url).host;
  } catch {
    return '外部链接';
  }
}

export function parseCsv(text: string) {
  const rows: string[][] = [];
  let cell = '';
  let row: string[] = [];
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    const next = text[index + 1];
    if (char === '"' && quoted && next === '"') {
      cell += '"';
      index += 1;
    } else if (char === '"') {
      quoted = !quoted;
    } else if (char === ',' && !quoted) {
      row.push(cell);
      cell = '';
    } else if ((char === '\n' || char === '\r') && !quoted) {
      if (char === '\r' && next === '\n') index += 1;
      row.push(cell);
      if (row.some((value) => value.length > 0)) rows.push(row);
      cell = '';
      row = [];
    } else {
      cell += char;
    }
  }
  row.push(cell);
  if (row.some((value) => value.length > 0)) rows.push(row);
  return rows;
}

export function normalizeStorageType(
  value?: string,
): IntegrationDocumentStorageType {
  return value === 'OBJECT' ? 'OBJECT' : 'EXTERNAL';
}
