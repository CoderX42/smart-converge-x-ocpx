import { ElMessage } from 'element-plus';

export type TableColumn = { key: string; label: string };

export const PAGE_SIZES = [10, 30, 60];

export function allColumnKeys(columns: TableColumn[]) {
  return columns.map(({ key }) => key);
}

export function resetPage<T extends { current: number }>(query: T) {
  query.current = 1;
}

/**
 * 返回一个轻量序号门闩，确保快速切换筛选时只有最后一次请求能回写页面。
 */
export function createLatestRequestGuard() {
  let sequence = 0;
  return {
    begin() {
      sequence += 1;
      return sequence;
    },
    isLatest(requestSequence: number) {
      return requestSequence === sequence;
    },
  };
}

export async function copyLongText(value?: null | string) {
  if (!value) return;

  // Clipboard API 只在安全上下文（HTTPS / localhost）里暴露；线上若走 http，
  // navigator.clipboard 为 undefined，退回 legacy execCommand 方案。
  if (navigator.clipboard) {
    try {
      await navigator.clipboard.writeText(value);
      ElMessage.success('复制成功');
      return;
    } catch {
      // 安全上下文下仍可能失败（如用户拒绝授权），继续降级
    }
  }

  const ok = legacyCopy(value);
  ElMessage[ok ? 'success' : 'error'](ok ? '复制成功' : '复制失败，请手动复制');
}

/** 非安全上下文下的降级复制：textarea + execCommand，http 站点也能用。 */
function legacyCopy(text: string): boolean {
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.setAttribute('readonly', '');
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.append(textarea);
  textarea.select();
  let ok = false;
  try {
    ok = document.execCommand('copy');
  } catch {
    ok = false;
  }
  textarea.remove();
  return ok;
}

export const platformOptions = [
  { label: 'Android', value: 1 },
  { label: 'IOS', value: 2 },
  { label: 'Android+IOS', value: 3 },
  { label: '快应用', value: 4 },
] as const;

export const platformLabel: Record<number, string> = {
  1: 'Android',
  2: 'IOS',
  3: 'Android+IOS',
  4: '快应用',
};
