import { requestClient } from '#/api/request';

// 小程序备案查询
export const getLastIcp = (appId: string) => {
  return requestClient.get(`/open-platform/open-mini-icp/${appId}`);
};

// 发起小程序管理员人脸核身
export const startFaceVerify = (appId: string) => {
  return requestClient.post(`/open-platform/open-mini-icp/create/${appId}`);
};

// 查询人脸核身任务状态
export const getFaceVerifyStatus = (taskId: string) => {
  return requestClient.get(
    `/open-platform/open-mini-icp/verify-task/${taskId}`,
  );
};

// 提交备案申请
export const addIcp = (data: any) => {
  return requestClient.post('/open-platform/open-mini-icp/apply', data);
};

// 更新备案申请
export const editIcp = (data: any) => {
  return requestClient.put('/open-platform/open-mini-icp', data);
};

// 获取区域信息
export const getRegionTree = (appId: string) => {
  return requestClient.get(`/open-platform/open-mini-icp/district/${appId}`);
};

// 获取证件类型
export const getCertificateTypes = (appId: string) => {
  return requestClient.get(
    `/open-platform/open-mini-icp/certificate-types/${appId}`,
  );
};

// 获取服务类型
export const getServiceTypes = (appId: string) => {
  return requestClient.get(
    `/open-platform/open-mini-icp/service-types/${appId}`,
  );
};

// 获取前置审批类型
export const getPreApprovalTypes = (appId: string) => {
  return requestClient.get(`/open-platform/open-mini-icp/nrlx-types/${appId}`);
};
// 获取主体性质
export const getSubjectTypes = (appId: string) => {
  return requestClient.get(
    `/open-platform/open-mini-icp/subject-types/${appId}`,
  );
};
// 获取小程序备案状态
export async function getIcpEntrance(appId: string) {
  return requestClient.get(
    `/open-platform/open-mini-icp/icp-entrance/${appId}`,
  );
}
