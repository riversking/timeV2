import http from "@/services/http";

const API_PREFIX = "/api/user-server/dic";

export async function getDicTree(data: any) {
  return http.post(`${API_PREFIX}/getDicTree`, data).then((res) => res.data);
}

export async function getDicData(data: any) {
  return http.post(`${API_PREFIX}/getDicData`, data).then((res) => res.data);
}

export async function getDicDataDetail(data: any) {
  return http
    .post(`${API_PREFIX}/getDicDataDetail`, data)
    .then((res) => res.data);
}

export async function saveDic(data: any) {
  return http.post(`${API_PREFIX}/saveDic`, data).then((res) => res.data);
}

export async function updateDic(data: any) {
  return http.post(`${API_PREFIX}/updateDic`, data).then((res) => res.data);
}
