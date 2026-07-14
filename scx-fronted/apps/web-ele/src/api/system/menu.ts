import { requestClient } from '#/api/request';

export namespace SystemMenuApi {
  /** 徽标颜色集合 */
  export const BadgeVariants = [
    'default',
    'destructive',
    'primary',
    'success',
    'warning',
  ] as const;
  /** 徽标类型集合 */
  export const BadgeTypes = ['dot', 'normal'] as const;
  /** 菜单类型集合 */
  export const MenuTypes = [
    'catalog',
    'menu',
    'embedded',
    'link',
    'button',
  ] as const;
  /** 系统菜单 */
  export interface SystemMenu {
    [key: string]: any;
    /** 后端权限标识 */
    authCode: string;
    /** 子级 */
    children?: SystemMenu[];
    /** 组件 */
    component?: string;
    /** 菜单ID */
    id: string;
    /** 菜单图标 */
    icon?: string;
    /** 菜单名称 */
    name: string;
    /** 路由路径 */
    path: string;
    /** 父级ID */
    parentId: string;
    /** 重定向 */
    redirect?: string;
    /** 菜单类型 */
    type: (typeof MenuTypes)[number];
  }
}
/**
 * 获取菜单
 */
export async function getMenu() {
  return requestClient.get('/system/menu', {
    headers: {
    },
  });
}

/**
 * 获取菜单树
 */
export async function getList() {
  return requestClient.get('/system/menu/tree', {
    headers: {
    },
  });
}

/**
 * 根据ID获取菜单
 */
export async function getById(id: string) {
  return requestClient.get(`/system/menu/${id}`, {
    headers: {
    },
  });
}

/**
 * 添加菜单
 */
export async function addObj(data: any) {
  return requestClient.post('/system/menu', data, {
    headers: {
    },
  });
}

/**
 * 编辑菜单
 */
export async function editObj(data: any) {
  return requestClient.put('/system/menu', data, {
    headers: {
    },
  });
}

/**
 * 删除菜单
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/menu/${id}`, {
    headers: {
    },
  });
}

/**
 * 查询角色菜单
 */
export async function getMenusByRole(roleId: string) {
  return requestClient.get(`/system/menu/role/${roleId}`, {});
}
