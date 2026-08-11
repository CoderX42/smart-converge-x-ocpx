import type {
  ComponentRecordType,
  GenerateMenuAndRoutesOptions,
} from '@boot/types';

import { generateAccessible } from '@boot/access';
import { preferences } from '@boot/preferences';

import { ElMessage } from 'element-plus';

import { getAllMenusApi } from '#/api';
import { BasicLayout, IFrameView } from '#/layouts';
import { $t } from '#/locales';

const forbiddenComponent = () => import('#/views/_core/fallback/forbidden.vue');

async function generateAccess(options: GenerateMenuAndRoutesOptions) {
  // Only the compact OCPX loop and shared system pages are resolvable. Retired
  // advertising domains cannot be revived by a stale database menu entry.
  const pageMap: ComponentRecordType = import.meta.glob([
    '../views/_core/**/*.vue',
    '../views/ocpx/**/*.vue',
    '../views/system/**/*.vue',
  ]);

  const layoutMap: ComponentRecordType = {
    BasicLayout,
    IFrameView,
  };

  return await generateAccessible(preferences.app.accessMode, {
    ...options,
    fetchMenuListAsync: async () => {
      ElMessage({
        duration: 1500,
        message: `${$t('common.loadingMenu')}...`,
      });
      return await getAllMenusApi();
    },
    // 可以指定没有权限跳转403页面
    forbiddenComponent,
    // 如果 route.meta.menuVisibleWithForbidden = true
    layoutMap,
    pageMap,
  });
}

export { generateAccess };
