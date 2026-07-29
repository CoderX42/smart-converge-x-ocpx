export type PageQuery = {
  current: number;
  keyword?: string;
  size: number;
  status?: string;
};

export type PageResult<T> = {
  current?: number;
  pages?: number;
  records: T[];
  size?: number;
  total: number;
};

export type BaseRecord = {
  createTime?: string;
  id: string;
  status?: string;
  updateTime?: string;
  version?: number;
};

export type SelectOption = {
  id: string;
  name: string;
};
