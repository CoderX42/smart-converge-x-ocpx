import { requestClient } from '#/api/request';

/**
 * 生成随机len位数字
 */
export const randomLenNum = (len: any, date: any) => {
  let random = '';
  random = Math.ceil(Math.random() * 100_000_000_000_000)
    .toString()
    .slice(0, Math.max(0, len || 4));
  if (date) random = random + Date.now();
  return random;
};

/**
 * 判断数组是否相等
 */
export const arrEquals = (arr1: any, arr2: any) => {
  if (!arr1 || !arr2) {
    return false;
  }

  if (arr1.length !== arr2.length) {
    return false;
  }

  for (const element of arr2) {
    if (!arr1.includes(element)) {
      return false;
    }
  }

  for (const element of arr1) {
    if (!arr2.includes(element)) {
      return false;
    }
  }
  return true;
};
/**
 * 笛卡儿积
 */
export const descartes = (array: any) => {
  if (array.length < 2) return array[0] || [];
  return Array.prototype.reduce.call(array, (col: any, set: any) => {
    const res: any = [];
    col.forEach((c: any) => {
      set.forEach((s: any) => {
        const t = [Array.isArray(c) ? c : [c]].flat();
        t.push(s);
        res.push(t);
      });
    });
    return res;
  });
};

export const findArray = (array: any, value: string) => {
  return array.findIndex((item: any) => {
    return item.dictValue === value;
  });
};
/**
 * 时间问候语
 * @param param 当前时间，new Date() 格式
 * @description param 调用 `formatAxis(new Date())` 输出 `上午好`
 * @returns 返回拼接后的时间字符串
 */
export function formatAxis(param: Date): string {
  const hour: number = new Date(param).getHours();
  if (hour < 6) return '凌晨好';
  else if (hour < 9) return '早上好';
  else if (hour < 12) return '上午好';
  else if (hour < 14) return '中午好';
  else if (hour < 17) return '下午好';
  else if (hour < 19) return '傍晚好';
  else if (hour < 22) return '晚上好';
  else return '夜里好';
}
/**
 * 深合并对象
 * @param target 目标对象（会被修改）
 * @param source 源对象
 * @param options 配置项
 */
export function deepMerge<
  T extends Record<string, any>,
  S extends Record<string, any>,
>(target: T, source: S, options: { skipEmpty?: boolean } = {}): S & T {
  const { skipEmpty = true } = options;

  if (!source || typeof source !== 'object') return target;

  for (const key in source) {
    const val = source[key];

    // 是否跳过空值
    if (skipEmpty && (val === null || val === undefined || val === '')) {
      continue;
    }

    // 如果是对象，递归合并
    if (val && typeof val === 'object' && !Array.isArray(val)) {
      if (!target[key] || typeof target[key] !== 'object') {
        target[key] = {} as any;
      }
      deepMerge(target[key], val, options);
    } else {
      target[key] = val as any;
    }
  }

  return target as S & T;
}

export function downloadBlobFile(url: string, query: any, fileName: string) {
  return requestClient
    .download(url, { params: query, responseReturn: 'raw' })
    .then((response) => {
      // 如果 data 不是 Blob，则转换为 Blob
      const blob =
        response.data instanceof Blob
          ? response.data
          : new Blob([response.data], { type: 'application/octet-stream' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName;
      document.body.append(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    })
    .catch((error) => {
      console.error('下载文件失败:', error);
    });
}
