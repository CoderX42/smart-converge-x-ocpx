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
  // SCX only exposes pages that belong to the active platform boundary. Legacy
  // commerce, mini-app and website pages remain in the repository for staged
  // migration but cannot be resolved into runtime routes.
  const pageMap: ComponentRecordType = import.meta.glob([
    '../views/_core/**/*.vue',
    '../views/dashboard/**/*.vue',
    '../views/campaign/**/*.vue',
    '../views/commercial/**/*.vue',
    '../views/creative/**/*.vue',
    '../views/delivery/**/*.vue',
    '../views/finance/**/*.vue',
    '../views/inventory/**/*.vue',
    '../views/toolbox/**/*.vue',
    '../views/reporting/**/*.vue',
    '../views/operations/**/*.vue',
    '../views/pay/**/*.vue',
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
