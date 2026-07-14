import { requestClient } from '#/api/request';

/**
 * 文件上传
 */
export async function uploadFile(data: any) {
  return requestClient.post('/system/file/upload', data, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}
