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
