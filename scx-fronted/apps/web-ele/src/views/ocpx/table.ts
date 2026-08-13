import { ElMessage } from 'element-plus';

export type TableColumn = { key: string; label: string };

export const PAGE_SIZES = [10, 30, 60];

export function allColumnKeys(columns: TableColumn[]) {
  return columns.map(({ key }) => key);
}

export function resetPage<T extends { current: number }>(query: T) {
  query.current = 1;
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

export const platformLabel: Record<number, string> = {
  1: 'Android',
  2: 'iOS',
  3: 'Android+iOS',
  4: '快应用',
};
