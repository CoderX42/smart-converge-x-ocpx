import type {
  ProductCreateInput,
  ProductDetail,
  ProductEventPolicyInput,
  ProductUpdateInput,
} from '#/api/ocpx';

export type ProductEditorPolicy = ProductEventPolicyInput;

export type ProductEditor = {
  advertiserId: string;
  code: string;
  description: string;
  eventPolicies: ProductEditorPolicy[];
  logoFile?: File;
  name: string;
  packageName: string;
  removeLogo: boolean;
  status: 'ACTIVE' | 'DISABLED';
};

export type ProductEmptyState =
  'ERROR' | 'FILTER_EMPTY' | 'INITIAL_EMPTY' | 'NO_PERMISSION' | 'READY';

type ClientCrypto = {
  getRandomValues?: (array: Uint8Array) => Uint8Array;
  randomUUID?: () => string;
};

/**
 * Creates a request idempotency key without assuming randomUUID is available.
 * The key identifies a create request and is not an authentication credential.
 */
export function createProductIdempotencyKey(cryptoApi?: ClientCrypto | null) {
  const source = cryptoApi === undefined ? globalThis.crypto : cryptoApi;
  if (typeof source?.randomUUID === 'function') {
    try {
      return source.randomUUID().replaceAll('-', '');
    } catch {
      // Continue with getRandomValues in restricted browser/WebView contexts.
    }
  }
  if (typeof source?.getRandomValues === 'function') {
    try {
      const bytes = new Uint8Array(16);
      source.getRandomValues(bytes);
      return Array.from(bytes, (value) =>
        value.toString(16).padStart(2, '0'),
      ).join('');
    } catch {
      // The final fallback is sufficient for a non-security idempotency key.
    }
  }
  const timestamp = Date.now().toString(36).padStart(10, '0');
  const random = Math.random()
    .toString(36)
    .slice(2)
    .padEnd(16, '0')
    .slice(0, 16);
  return `${timestamp}${random}`;
}

export function resolveProductEmptyState(input: {
  canView: boolean;
  error: boolean;
  filtersActive: boolean;
  total: number;
}): ProductEmptyState {
  if (!input.canView) return 'NO_PERMISSION';
  if (input.error) return 'ERROR';
  if (input.total > 0) return 'READY';
  return input.filtersActive ? 'FILTER_EMPTY' : 'INITIAL_EMPTY';
}

export function duplicateProductPolicyIndexes(policies: ProductEditorPolicy[]) {
  const seen = new Map<string, number>();
  const duplicates = new Set<number>();
  policies.forEach((policy, index) => {
    if (!policy.eventDefinitionId) return;
    const previous = seen.get(policy.eventDefinitionId);
    if (previous === undefined) {
      seen.set(policy.eventDefinitionId, index);
      return;
    }
    duplicates.add(previous);
    duplicates.add(index);
  });
  return [...duplicates].sort((left, right) => left - right);
}

export function buildProductCreatePayload(
  editor: ProductEditor,
): ProductCreateInput {
  return {
    advertiserId: editor.advertiserId,
    code: editor.code.trim(),
    description: optional(editor.description),
    eventPolicies: normalizedPolicies(editor.eventPolicies),
    name: editor.name.trim(),
    packageName: optional(editor.packageName),
    status: editor.status,
  };
}

export function buildProductUpdatePayload(
  editor: ProductEditor,
  detail: ProductDetail,
): ProductUpdateInput {
  return {
    description: optional(editor.description),
    eventPolicies: normalizedPolicies(editor.eventPolicies),
    expectedVersion: detail.configVersion,
    name: editor.name.trim(),
    packageName: optional(editor.packageName),
    status: editor.status,
  };
}

export function productEditorSnapshot(editor: ProductEditor) {
  return JSON.stringify({
    ...editor,
    eventPolicies: normalizedPolicies(editor.eventPolicies),
    logoFile: editor.logoFile
      ? {
          lastModified: editor.logoFile.lastModified,
          name: editor.logoFile.name,
          size: editor.logoFile.size,
          type: editor.logoFile.type,
        }
      : undefined,
  });
}

function normalizedPolicies(policies: ProductEditorPolicy[]) {
  return policies.map((policy) => ({
    dailyLimit: Number(policy.dailyLimit || 0),
    eventDefinitionId: policy.eventDefinitionId,
  }));
}

function optional(value: string) {
  const normalized = value.trim();
  return normalized || undefined;
}
