import type { AccessScope } from '#/api/adx';

export type ScopeValidation = { message?: string; valid: boolean };

export function normalizedScopes(scopes: AccessScope[]): AccessScope[] {
  return scopes.map((scope) => ({
    permissionLevel: scope.permissionLevel,
    scopeId: scope.scopeType === 'ALL' ? undefined : scope.scopeId?.trim(),
    scopeType: scope.scopeType,
    validFrom: scope.validFrom || undefined,
    validTo: scope.validTo || undefined,
  }));
}

export function validateScopes(scopes: AccessScope[]): ScopeValidation {
  const keys = new Set<string>();
  let hasAll = false;
  for (const scope of scopes) {
    if (scope.scopeType === 'ALL') {
      hasAll = true;
    } else if (!scope.scopeId?.trim()) {
      return { message: '请选择具体的授权对象', valid: false };
    }
    const key = `${scope.scopeType}:${scope.scopeId || ''}`;
    if (keys.has(key)) {
      return { message: '同一对象不能重复授权', valid: false };
    }
    keys.add(key);
    if (
      scope.validFrom &&
      scope.validTo &&
      new Date(scope.validFrom).getTime() >= new Date(scope.validTo).getTime()
    ) {
      return { message: '授权开始时间必须早于结束时间', valid: false };
    }
  }
  if (hasAll && scopes.length > 1) {
    return { message: '全部对象授权不能与具体对象授权同时存在', valid: false };
  }
  return { valid: true };
}
