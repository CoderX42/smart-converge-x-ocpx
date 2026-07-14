import { requestClient } from '#/api/request';

export async function getPage(query: any) {
  return requestClient.get('/site/theme/page', { params: query });
}

export async function getById(id: string) {
  return requestClient.get(`/site/theme/${id}`);
}

export async function addObj(data: any) {
  return requestClient.post('/site/theme', data);
}

export async function editObj(data: any) {
  return requestClient.put('/site/theme', data);
}

export async function delObj(id: string) {
  return requestClient.delete(`/site/theme/${id}`);
}
