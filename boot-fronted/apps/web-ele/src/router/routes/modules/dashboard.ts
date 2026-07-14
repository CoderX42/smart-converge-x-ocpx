import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:layout-dashboard',
      order: -1,
      title: $t('page.dashboard.title'),
    },
    name: 'Dashboard',
    path: '/dashboard',
    children: [
      {
        name: 'Analytics',
        path: '/analytics',
        component: () => import('#/views/dashboard/analytics/index.vue'),
        meta: {
          affixTab: true,
          icon: 'lucide:area-chart',
          title: $t('page.dashboard.analytics'),
        },
      },
      {
        name: '用户分析',
        path: '/user-analytics',
        component: () => import('#/views/dashboard/user/index.vue'),
        meta: {
          icon: 'lucide:users',
          title: '用户分析',
        },
      },
      {
        name: '订单详情',
        path: '/order/detail',
        component: () => import('#/views/order/order-info/info/index.vue'),
        meta: {
          icon: 'lucide:area-chart',
          title: '订单详情',
          hideInMenu: true,
        },
      },
      {
        name: '我的信息',
        path: '/user/profile',
        component: () => import('#/views/system/user/info/profile.vue'),
        meta: {
          icon: 'lucide:area-chart',
          title: '个人中心',
          hideInMenu: true,
        },
      },
      {
        name: '代码生成配置',
        path: '/code/gen',
        component: () => import('#/views/gen/gen-table/generate.vue'),
        meta: {
          icon: 'lucide:area-chart',
          title: '代码生成配置',
          hideInMenu: true,
        },
      },
    ],
  },
  {
    name: '官网装修',
    path: '/site/page-design',
    component: () => import('#/views/site/page-design/index.vue'),
    meta: {
      icon: 'lucide:paintbrush',
      title: '官网装修',
      hideInMenu: true,
    },
  },
];

export default routes;
