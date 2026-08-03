import type { RouteRecordRaw } from 'vue-router';

// The dashboard is delivered by the permission-backed ADX menu. Keep the old
// template URL as a hidden redirect so bookmarks and historical entry links do
// not fall through to the global 404 page.
const routes: RouteRecordRaw[] = [
  {
    meta: {
      hideInBreadcrumb: true,
      hideInMenu: true,
      hideInTab: true,
      title: '首页',
    },
    name: 'LegacyAnalyticsRedirect',
    path: '/analytics',
    redirect: '/adx/dashboard',
  },
];

export default routes;
