import type {
  AdvertiserCreateInput,
  AdvertiserDetail,
  AdvertiserUpdateInput,
} from '#/api/ocpx';

export type AdvertiserEmptyState =
  'ERROR' | 'FILTER_EMPTY' | 'INITIAL_EMPTY' | 'NO_PERMISSION' | 'READY';

export function resolveAdvertiserEmptyState(input: {
  canView: boolean;
  error: boolean;
  filtersActive: boolean;
  total: number;
}): AdvertiserEmptyState {
  if (!input.canView) return 'NO_PERMISSION';
  if (input.error) return 'ERROR';
  if (input.total > 0) return 'READY';
  return input.filtersActive ? 'FILTER_EMPTY' : 'INITIAL_EMPTY';
}

export function buildAdvertiserCreatePayload(form: Record<string, unknown>) {
  return {
    account: String(form.account || '').trim(),
    adapterKey: String(form.adapterKey || '').trim(),
    bidUrl: optional(form.bidUrl),
    code: String(form.code || '').trim(),
    credentialInput: optional(form.credentialInput),
    macroParams: optional(form.macroParams),
    name: String(form.advertiserName || '').trim(),
    password: String(form.password || ''),
    upstreamHostAllowlist: optional(form.upstreamHostAllowlist),
  } satisfies AdvertiserCreateInput;
}

export function buildAdvertiserUpdatePayload(
  form: Record<string, unknown>,
  detail: AdvertiserDetail,
) {
  return {
    adapterKey: String(form.adapterKey || '').trim(),
    bidUrl: optional(form.bidUrl),
    expectedVersion: detail.configVersion,
    macroParams: optional(form.macroParams),
    name: String(form.advertiserName || '').trim(),
    upstreamHostAllowlist: optional(form.upstreamHostAllowlist),
  } satisfies AdvertiserUpdateInput;
}

export function editorSnapshot(form: Record<string, unknown>) {
  return JSON.stringify({
    ...form,
    credentialInput: Boolean(form.credentialInput),
    password: Boolean(form.password),
  });
}

function optional(value: unknown) {
  const normalized = String(value || '').trim();
  return normalized || undefined;
}
