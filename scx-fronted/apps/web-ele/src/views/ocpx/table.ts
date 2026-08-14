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
  try {
    await navigator.clipboard.writeText(value);
    ElMessage.success('复制成功');
  } catch {
    ElMessage.error('复制失败，请手动复制');
  }
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
