import type { RuntimeReplayPreview } from '#/api/ocpx';

export type RuntimePageState =
  | 'FILTER_EMPTY'
  | 'FORBIDDEN'
  | 'INITIAL_EMPTY'
  | 'LOADING'
  | 'READY'
  | 'REQUEST_ERROR';

export function resolveRuntimePageState(input: {
  error: boolean;
  filtersActive: boolean;
  forbidden: boolean;
  initialized: boolean;
  loading: boolean;
  rowCount: number;
}): RuntimePageState {
  if (input.forbidden) return 'FORBIDDEN';
  if (input.loading && !input.initialized) return 'LOADING';
  if (input.error) return 'REQUEST_ERROR';
  if (input.initialized && input.rowCount === 0) {
    return input.filtersActive ? 'FILTER_EMPTY' : 'INITIAL_EMPTY';
  }
  return 'READY';
}

export function canConfirmRuntimeReplay(input: {
  password: string;
  preview?: RuntimeReplayPreview;
  reason: string;
}) {
  return Boolean(
    input.preview &&
    input.preview.blockers.length === 0 &&
    input.preview.mode === 'ORIGINAL_SNAPSHOT_ONLY' &&
    input.reason.trim().length >= 4 &&
    input.password,
  );
}

export function runtimeValueIsMasked(value?: string) {
  return !value || value.endsWith('…');
}
