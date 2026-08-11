import type { RouteRecordRaw } from 'vue-router';

// The dashboard is delivered by the permission-backed OCPX menu. Keep the old
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
    redirect: '/ocpx/dashboard',
  },
];

export default routes;
