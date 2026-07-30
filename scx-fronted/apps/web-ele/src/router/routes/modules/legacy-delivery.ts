import type { RouteLocationGeneric, RouteRecordRaw } from 'vue-router';

type LegacyTarget = {
  path: string;
  tab?: string;
};

export const legacyWorkspaceTargets: Record<string, LegacyTarget> = {
  '/campaign/reviews': { path: '/campaign/plans', tab: 'review' },
  '/commercial/advertisers': {
    path: '/commercial/entities',
    tab: 'advertisers',
  },
  '/commercial/agencies': { path: '/commercial/entities', tab: 'agencies' },
  '/commercial/agreements': {
    path: '/commercial/compliance',
    tab: 'instances',
  },
  '/commercial/organizations': {
    path: '/commercial/entities',
    tab: 'organizations',
  },
  '/commercial/qualifications': {
    path: '/commercial/compliance',
    tab: 'qualifications',
  },
  '/delivery/channel-users': {
    path: '/inventory/channel-access',
    tab: 'users',
  },
  '/delivery/customers': {
    path: '/commercial/catalog',
    tab: 'partners',
  },
  '/delivery/documents': {
    path: '/inventory/channel-access',
    tab: 'documents',
  },
  '/delivery/master-data': { path: '/inventory/media-access' },
  '/delivery/media-channels': {
    path: '/inventory/channel-access',
    tab: 'channels',
  },
  '/delivery/media-scopes': { path: '/inventory/media-access' },
  '/delivery/partners': {
    path: '/commercial/catalog',
    tab: 'partners',
  },
  '/delivery/products': {
    path: '/commercial/catalog',
    tab: 'products',
  },
  '/inventory/media-accounts': {
    path: '/inventory/supply',
    tab: 'accounts',
  },
  '/inventory/placements': {
    path: '/inventory/supply',
    tab: 'placements',
  },
};

function redirectLegacyPath(path: string, to: RouteLocationGeneric) {
  const target = legacyWorkspaceTargets[path];
  if (!target) return { path: '/' };
  return {
    hash: to.hash,
    path: target.path,
    query: {
      ...to.query,
      ...(target.tab ? { tab: target.tab } : {}),
    },
  };
}

export function legacyWorkspaceRedirect(to: RouteLocationGeneric) {
  return redirectLegacyPath(to.path, to);
}

const routes = Object.keys(legacyWorkspaceTargets).map<RouteRecordRaw>(
  (path, index) => ({
    meta: {
      hideInBreadcrumb: true,
      hideInMenu: true,
      hideInTab: true,
      title: '兼容跳转',
    },
    name: `LegacyWorkspaceRedirect${index + 1}`,
    path,
    redirect: (to) => redirectLegacyPath(path, to),
  }),
);

function requireLegacyRoute(path: string) {
  const route = routes.find((item) => item.path === path);
  if (!route) throw new Error(`Missing legacy route: ${path}`);
  return route;
}

export const legacyMasterDataRedirect = requireLegacyRoute(
  '/delivery/master-data',
);
export const legacyCustomerRedirect = requireLegacyRoute('/delivery/customers');

export default routes;
