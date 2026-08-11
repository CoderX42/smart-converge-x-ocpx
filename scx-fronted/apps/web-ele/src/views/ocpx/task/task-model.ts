import type {
  TaskDetail,
  TaskEventBindingInput,
  TaskEventBindingOptions,
  TaskInput,
  TaskOptions,
} from '#/api/ocpx';

export type TaskEventBindingEditor = TaskEventBindingInput & {
  rowKey: string;
};

export type TaskEditor = {
  clickAppLinkId: string;
  dedupWindowSeconds: number;
  description: string;
  eventBindings: TaskEventBindingEditor[];
  exposureAppLinkId: string;
  exposureAsClick: boolean;
  exposureEnabled: boolean;
  mediaAccountId: string;
  mediaCredentialId: string;
  operatorUserId: string;
  productId: string;
  rtaMode: 'OFF';
  taskName: string;
};

export type TaskEmptyState =
  'ERROR' | 'FILTER_EMPTY' | 'INITIAL_EMPTY' | 'NO_PERMISSION' | 'READY';

export type RoutePublicationState =
  | 'BLOCKED'
  | 'CONFLICT'
  | 'DISTRIBUTING'
  | 'ERROR'
  | 'FAILED'
  | 'NO_PERMISSION'
  | 'READY'
  | 'STALE';

export function resolveRoutePublicationState(input: {
  checkStatus?: 'FAIL' | 'PASS' | 'WARN';
  conflict: boolean;
  distributionStatus?: string;
  error: boolean;
  expiresAt?: string;
  forbidden: boolean;
  now?: number;
}): RoutePublicationState {
  if (input.forbidden) return 'NO_PERMISSION';
  if (input.conflict) return 'CONFLICT';
  if (input.error) return 'ERROR';
  if (
    input.expiresAt &&
    Date.parse(input.expiresAt) <= (input.now ?? Date.now())
  ) {
    return 'STALE';
  }
  if (input.distributionStatus === 'FAILED') return 'FAILED';
  if (
    ['DEGRADED', 'DISTRIBUTING', 'PENDING'].includes(
      input.distributionStatus || '',
    )
  ) {
    return 'DISTRIBUTING';
  }
  return input.checkStatus === 'PASS' || input.checkStatus === 'WARN'
    ? 'READY'
    : 'BLOCKED';
}

export function resolveTaskEmptyState(input: {
  canView: boolean;
  error: boolean;
  filtersActive: boolean;
  total: number;
}): TaskEmptyState {
  if (!input.canView) return 'NO_PERMISSION';
  if (input.error) return 'ERROR';
  if (input.total > 0) return 'READY';
  return input.filtersActive ? 'FILTER_EMPTY' : 'INITIAL_EMPTY';
}

export function normalizeTaskRelations(
  editor: TaskEditor,
  options: TaskOptions,
  changed: 'exposure' | 'mediaAccount' | 'product',
) {
  if (changed === 'product') {
    const product = options.products.find(
      (item) => item.id === editor.productId,
    );
    const clickCompatible = options.appLinks.some(
      (item) =>
        item.id === editor.clickAppLinkId &&
        item.productId === editor.productId,
    );
    const exposureCompatible = options.appLinks.some(
      (item) =>
        item.id === editor.exposureAppLinkId &&
        item.productId === editor.productId,
    );
    if (!clickCompatible) editor.clickAppLinkId = '';
    if (!exposureCompatible) editor.exposureAppLinkId = '';
    if (
      product &&
      !options.appLinks.some(
        (item) =>
          item.productId === product.id &&
          item.advertiserId === product.advertiserId,
      )
    ) {
      editor.clickAppLinkId = '';
    }
    editor.eventBindings = [];
  }
  if (changed === 'mediaAccount') {
    editor.eventBindings = [];
    const account = options.mediaAccounts.find(
      (item) => item.id === editor.mediaAccountId,
    );
    const compatible = options.credentials.some(
      (item) =>
        item.id === editor.mediaCredentialId &&
        item.mediaAccountId === editor.mediaAccountId,
    );
    if (!compatible) {
      editor.mediaCredentialId =
        (account?.defaultCredentialId &&
          options.credentials.some(
            (item) => item.id === account.defaultCredentialId,
          ) &&
          account.defaultCredentialId) ||
        options.credentials.find(
          (item) => item.mediaAccountId === editor.mediaAccountId,
        )?.id ||
        '';
    }
  }
  if (changed === 'exposure' && !editor.exposureEnabled) {
    editor.exposureAppLinkId = '';
    editor.exposureAsClick = false;
  }
}

export function taskEditorIssue(
  editor: TaskEditor,
  options: TaskOptions,
  eventOptions?: TaskEventBindingOptions,
) {
  if (editor.dedupWindowSeconds < 0 || editor.dedupWindowSeconds > 86_400) {
    return '去重窗口必须在 0 到 86400 秒之间。';
  }
  if (editor.rtaMode !== 'OFF') return '当前阶段 RTA 固定为 OFF。';
  if (editor.exposureEnabled && !editor.exposureAppLinkId) {
    return '开启曝光时必须选择同一产品下的曝光 AppLink。';
  }
  if (
    !editor.exposureEnabled &&
    (editor.exposureAppLinkId || editor.exposureAsClick)
  ) {
    return '关闭曝光时必须清空曝光 AppLink 和大点击。';
  }
  const product = options.products.find((item) => item.id === editor.productId);
  const click = options.appLinks.find(
    (item) => item.id === editor.clickAppLinkId,
  );
  const exposure = options.appLinks.find(
    (item) => item.id === editor.exposureAppLinkId,
  );
  if (!product || !click || click.productId !== product.id) {
    return '点击 AppLink 与 Product 不兼容。';
  }
  if (exposure && exposure.productId !== product.id) {
    return '曝光 AppLink 与 Product 不兼容。';
  }
  const credential = options.credentials.find(
    (item) => item.id === editor.mediaCredentialId,
  );
  if (!credential || credential.mediaAccountId !== editor.mediaAccountId) {
    return '媒体凭据与媒体账号不兼容。';
  }
  const eventIssue = taskEventBindingIssue(editor.eventBindings, eventOptions);
  if (eventIssue) return eventIssue;
  return '';
}

export function taskEventBindingIssue(
  bindings: TaskEventBindingEditor[],
  options?: TaskEventBindingOptions,
) {
  const definitions = new Set<string>();
  for (const binding of bindings) {
    if (!binding.eventDefinitionId || !binding.channelEventMappingId) {
      return '每条事件绑定都必须选择标准事件和渠道映射。';
    }
    if (definitions.has(binding.eventDefinitionId)) {
      return '同一 Task 的标准事件不能重复绑定。';
    }
    definitions.add(binding.eventDefinitionId);
    if (
      binding.callbackRatioBasisPoints < 0 ||
      binding.callbackRatioBasisPoints > 10_000
    ) {
      return 'Callback 比例必须在 0 到 10000 basis points 之间。';
    }
    if (binding.limitValue !== undefined && binding.limitValue < 0) {
      return '自然日限额必须为非负整数；留空表示不限额，0 表示全部超限。';
    }
    if (options) {
      const mapping = options.mappings.find(
        (item) => item.id === binding.channelEventMappingId,
      );
      if (
        !mapping ||
        mapping.eventDefinitionId !== binding.eventDefinitionId ||
        mapping.channelId !== options.channelId ||
        mapping.adapterCode !== options.adapterCode
      ) {
        return '事件绑定与当前媒体账号对应的 Channel / Adapter 不兼容。';
      }
    }
  }
  return '';
}

export function buildTaskPayload(editor: TaskEditor): TaskInput {
  return {
    clickAppLinkId: editor.clickAppLinkId,
    dedupWindowSeconds: editor.dedupWindowSeconds,
    description: optional(editor.description),
    exposureAppLinkId: editor.exposureEnabled
      ? editor.exposureAppLinkId
      : undefined,
    exposureAsClick: editor.exposureEnabled && editor.exposureAsClick,
    exposureEnabled: editor.exposureEnabled,
    eventBindings: editor.eventBindings.map(
      ({ rowKey: _rowKey, ...binding }) => ({
        ...binding,
        limitValue:
          binding.limitValue === undefined
            ? undefined
            : Number(binding.limitValue),
      }),
    ),
    mediaAccountId: editor.mediaAccountId,
    mediaCredentialId: editor.mediaCredentialId,
    operatorUserId: editor.operatorUserId,
    productId: editor.productId,
    rtaMode: 'OFF',
    taskName: editor.taskName.trim(),
  };
}

export function taskEditorSnapshot(editor: TaskEditor) {
  return JSON.stringify(buildTaskPayload(editor));
}

export function taskEditorFromDetail(detail: TaskDetail): TaskEditor {
  return {
    clickAppLinkId: detail.clickAppLinkId,
    dedupWindowSeconds: detail.dedupWindowSeconds,
    description: detail.description || '',
    exposureAppLinkId: detail.exposureAppLinkId || '',
    exposureAsClick: detail.exposureAsClick,
    exposureEnabled: detail.exposureEnabled,
    eventBindings: [],
    mediaAccountId: detail.mediaAccountId,
    mediaCredentialId: detail.mediaCredentialId,
    operatorUserId: detail.operatorUserId,
    productId: detail.productId,
    rtaMode: 'OFF',
    taskName: detail.taskName,
  };
}

function optional(value: string) {
  return value.trim() || undefined;
}
