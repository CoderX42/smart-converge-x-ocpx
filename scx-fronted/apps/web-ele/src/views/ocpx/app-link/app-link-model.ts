import type {
  AppLinkCreateInput,
  AppLinkDetail,
  AppLinkInput,
  AppLinkType,
  AppLinkUpdateInput,
} from '#/api/ocpx';

export type AppLinkEditor = {
  clearDeeplink: boolean;
  clearDownload: boolean;
  deeplinkUrl: string;
  description: string;
  documentId: string;
  downloadUrl: string;
  linkName: string;
  linkType: AppLinkType;
  linkUrlTemplate: string;
  productId: string;
  redirectEnabled: boolean;
};

export type AppLinkEmptyState =
  'ERROR' | 'FILTER_EMPTY' | 'INITIAL_EMPTY' | 'NO_PERMISSION' | 'READY';

export function resolveAppLinkEmptyState(input: {
  canView: boolean;
  error: boolean;
  filtersActive: boolean;
  total: number;
}): AppLinkEmptyState {
  if (!input.canView) return 'NO_PERMISSION';
  if (input.error) return 'ERROR';
  if (input.total > 0) return 'READY';
  return input.filtersActive ? 'FILTER_EMPTY' : 'INITIAL_EMPTY';
}

export function buildAppLinkCreatePayload(
  editor: AppLinkEditor,
): AppLinkCreateInput {
  return commonPayload(editor);
}

export function buildAppLinkUpdatePayload(
  editor: AppLinkEditor,
  detail: AppLinkDetail,
): AppLinkUpdateInput {
  return {
    ...commonPayload(editor),
    clearDeeplink: editor.clearDeeplink,
    clearDownload: editor.clearDownload,
    expectedVersion: detail.configVersion,
  };
}

export function buildLegacyMappingPayload(
  editor: AppLinkEditor,
  detail: AppLinkDetail,
): AppLinkInput & { expectedVersion: number } {
  return { ...commonPayload(editor), expectedVersion: detail.configVersion };
}

export function appLinkEditorSnapshot(editor: AppLinkEditor) {
  return JSON.stringify({
    ...editor,
    deeplinkUrl: editor.deeplinkUrl.trim(),
    description: editor.description.trim(),
    downloadUrl: editor.downloadUrl.trim(),
    linkName: editor.linkName.trim(),
    linkUrlTemplate: editor.linkUrlTemplate.trim(),
  });
}

export function appLinkRedirectIssue(editor: AppLinkEditor) {
  if (!editor.redirectEnabled) return '';
  const hasDeeplink =
    Boolean(editor.deeplinkUrl.trim()) && !editor.clearDeeplink;
  const hasDownload =
    Boolean(editor.downloadUrl.trim()) && !editor.clearDownload;
  return hasDeeplink || hasDownload
    ? ''
    : '开启重定向时至少填写一个直达或下载地址。';
}

function commonPayload(editor: AppLinkEditor): AppLinkInput {
  return {
    deeplinkUrl: optional(editor.deeplinkUrl),
    description: optional(editor.description),
    documentId: editor.documentId,
    downloadUrl: optional(editor.downloadUrl),
    linkName: editor.linkName.trim(),
    linkType: editor.linkType,
    linkUrlTemplate: editor.linkUrlTemplate.trim(),
    productId: editor.productId,
    redirectEnabled: editor.redirectEnabled,
  };
}

function optional(value: string) {
  const normalized = value.trim();
  return normalized || undefined;
}
