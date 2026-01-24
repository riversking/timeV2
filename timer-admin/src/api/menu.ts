import http from "@/services/http";

const API_PREFIX = "/api/user-server/menu";


export async function getMenuTree(data: any) {
  return http.post(`${API_PREFIX}/getMenuTree`, data).then((res) => res.data);
}

export async function getRoleMenu(data: any) {
  return http.post(`${API_PREFIX}/getRoleMenu`, data).then((res) => res.data);
}