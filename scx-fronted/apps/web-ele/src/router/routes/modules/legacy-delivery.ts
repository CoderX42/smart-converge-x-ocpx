import type { RouteRecordRaw } from 'vue-router';

export const legacyMasterDataRedirect: RouteRecordRaw = {
  meta: {
    hideInBreadcrumb: true,
    hideInMenu: true,
    hideInTab: true,
    title: '媒体与授权',
  },
  name: 'LegacyMasterDataRedirect',
  path: '/delivery/master-data',
  redirect: (to) => ({
    hash: to.hash,
    path: '/delivery/media-scopes',
    query: to.query,
  }),
};

export const legacyCustomerRedirect: RouteRecordRaw = {
  meta: {
    hideInBreadcrumb: true,
    hideInMenu: true,
    hideInTab: true,
    title: '合作方',
  },
  name: 'LegacyCustomerRedirect',
  path: '/delivery/customers',
  redirect: (to) => ({
    hash: to.hash,
    path: '/delivery/partners',
    query: to.query,
  }),
};

export default [legacyMasterDataRedirect, legacyCustomerRedirect];
