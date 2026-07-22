import type { RouteRecordRaw } from 'vue-router';

import type {
  ComponentRecordType,
  GenerateMenuAndRoutesOptions,
  RouteRecordStringComponent,
} from '@boot-core/typings';

import { mapTree } from '@boot-core/shared/utils';

/**
 * 动态生成路由 - 后端方式
 */
async function generateRoutesByBackend(
  options: GenerateMenuAndRoutesOptions,
): Promise<RouteRecordRaw[]> {
  const { fetchMenuListAsync, layoutMap = {}, pageMap = {} } = options;

  try {
    const menuRoutes = await fetchMenuListAsync?.();
    if (!menuRoutes) {
      return [];
    }

    const normalizePageMap: ComponentRecordType = {};

    for (const [key, value] of Object.entries(pageMap)) {
      normalizePageMap[normalizeViewPath(key)] = value;
    }

    const routes = convertRoutes(menuRoutes, layoutMap, normalizePageMap);

    return routes;
  } catch (error) {
    console.error(error);
    throw error;
  }
}

function convertRoutes(
  routes: RouteRecordStringComponent[],
  layoutMap: ComponentRecordType,
  pageMap: ComponentRecordType,
): RouteRecordRaw[] {
  const resultRoutes = mapTree(routes, (node) => {
    const route = node as unknown as RouteRecordRaw;
    const { component, name } = node;

    if (!name) {
      console.error('route name is required', route);
    }

    // layout转换
    if (component && layoutMap[component]) {
      route.component = layoutMap[component];
      // 页面组件转换
    } else if (component) {
      const normalizePath = normalizeViewPath(component);
      const pageKey = normalizePath.endsWith('.vue')
        ? normalizePath
        : `${normalizePath}.vue`;
      if (pageMap[pageKey]) {
        route.component = pageMap[pageKey];
      } else {
        console.error(`route component is invalid: ${pageKey}`, route);
        route.component = pageMap['/_core/fallback/not-found.vue'];
      }
    }

    return route;
  });

  ensureRouteNameUnique(resultRoutes, new Set());
  return resultRoutes;
}

function ensureRouteNameUnique(
  routes: RouteRecordRaw[],
  usedNames: Set<string>,
  ancestorNames: Set<string> = new Set(),
) {
  routes.forEach((route) => {
    const rawComponent = (route as { component?: unknown }).component;
    const currentName = typeof route.name === 'string' ? route.name : '';
    const baseName =
      currentName || (route.meta?.title as string) || route.path || '';
    const pathForSuffix =
      (route.path as string) ||
      (rawComponent && typeof rawComponent === 'string'
        ? rawComponent
        : `${(route as { id?: string }).id || 'route'}`);
    const routeNameSuffix = normalizePathToNameSuffix(pathForSuffix);
    const defaultName =
      typeof baseName === 'string' && baseName.trim()
        ? baseName.trim()
        : 'Route';

    let normalizedName = defaultName;
    if (
      !currentName ||
      usedNames.has(currentName) ||
      ancestorNames.has(currentName)
    ) {
      normalizedName = `${defaultName}-${routeNameSuffix}`;
      let duplicatedIndex = 1;
      while (
        usedNames.has(normalizedName) ||
        ancestorNames.has(normalizedName)
      ) {
        duplicatedIndex += 1;
        normalizedName = `${defaultName}-${routeNameSuffix}-${duplicatedIndex}`;
      }
    }

    route.name = normalizedName;
    usedNames.add(normalizedName);

    const childAncestorNames = new Set<string>(ancestorNames);
    childAncestorNames.add(normalizedName);
    if (route.children && route.children.length > 0) {
      ensureRouteNameUnique(
        route.children as RouteRecordRaw[],
        usedNames,
        childAncestorNames,
      );
    }
  });
}

function normalizePathToNameSuffix(pathLike: string | undefined): string {
  if (!pathLike) return 'route';
  return (
    pathLike
      .toString()
      .replaceAll(/^\/+|\/+$/g, '')
      .replaceAll(/[^\w\u4E00-\u9FFF-]+/g, '-')
      .replaceAll(/-+/g, '-')
      .replaceAll(/^-+|-+$/g, '') || 'route'
  );
}

function normalizeViewPath(path: string): string {
  // 去除相对路径前缀
  const normalizedPath = path.replace(/^(\.\/|\.\.\/)+/, '');

  // 确保路径以 '/' 开头
  const viewPath = normalizedPath.startsWith('/')
    ? normalizedPath
    : `/${normalizedPath}`;

  // 这里耦合了smart-converge-x的目录结构
  return viewPath.replace(/^\/views/, '');
}
export { generateRoutesByBackend };
