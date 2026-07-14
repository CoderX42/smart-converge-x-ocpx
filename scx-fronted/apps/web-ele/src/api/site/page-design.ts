import { requestClient } from '#/api/request';

export async function getPage(query: any) {
  return requestClient.get('/site/page/page', { params: query });
}

export async function getById(id: string) {
  return requestClient.get(`/site/page/${id}`);
}

export async function addObj(data: any) {
  return requestClient.post('/site/page', data);
}

export async function editObj(data: any) {
  return requestClient.put('/site/page', data);
}

export async function delObj(id: string) {
  return requestClient.delete(`/site/page/${id}`);
}
