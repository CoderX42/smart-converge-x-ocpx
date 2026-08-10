import type { MediaAccountDetail } from '#/api/adx';

export type MediaAccountEditor = {
  channelId: string;
  description: string;
  documentId: string;
  name: string;
};

export type MediaCredentialEditor = {
  description: string;
  loginIdentifier: string;
  password: string;
  reason: string;
  secretInput: string;
};

export type MediaAccountEmptyState =
  'ERROR' | 'FILTER_EMPTY' | 'INITIAL_EMPTY' | 'NO_PERMISSION' | 'READY';

export function resolveMediaAccountEmptyState(input: {
  canView: boolean;
  error: boolean;
  filtersActive: boolean;
  total: number;
}): MediaAccountEmptyState {
  if (!input.canView) return 'NO_PERMISSION';
  if (input.error) return 'ERROR';
  if (input.total > 0) return 'READY';
  return input.filtersActive ? 'FILTER_EMPTY' : 'INITIAL_EMPTY';
}

export function buildMediaAccountCreatePayload(editor: MediaAccountEditor) {
  return {
    channelId: editor.channelId,
    description: optional(editor.description),
    documentId: optional(editor.documentId),
    name: editor.name.trim(),
  };
}

export function buildMediaAccountUpdatePayload(
  editor: MediaAccountEditor,
  detail: MediaAccountDetail,
) {
  return {
    description: optional(editor.description),
    documentId: optional(editor.documentId),
    expectedVersion: detail.configVersion,
    name: editor.name.trim(),
  };
}

export function mediaAccountEditorSnapshot(editor: MediaAccountEditor) {
  return JSON.stringify({
    channelId: editor.channelId,
    description: editor.description.trim(),
    documentId: editor.documentId,
    name: editor.name.trim(),
  });
}

export function resetCredentialEditor(editor: MediaCredentialEditor) {
  Object.assign(editor, {
    description: '',
    loginIdentifier: '',
    password: '',
    reason: '',
    secretInput: '',
  });
}

function optional(value: string) {
  const normalized = value.trim();
  return normalized || undefined;
}
