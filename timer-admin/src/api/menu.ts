import http from "@/services/http";

const API_PREFIX = "/api/user-server/menu";

export async function getMenuTree(data: any) {
  return http.post(`${API_PREFIX}/getMenuTree`, data).then((res) => res.data);
}

export async function getRoleMenu(data: any) {
  return http.post(`${API_PREFIX}/getRoleMenu`, data).then((res) => res.data);
}

export async function saveRoleMenu(data: any) {
  return http.post(`${API_PREFIX}/saveRoleMenu`, data).then((res) => res.data);
}

export async function saveMenu(data: any) {
  return http.post(`${API_PREFIX}/saveMenu`, data).then((res) => res.data);
}

export async function updateMenu(data: any) {
  return http.post(`${API_PREFIX}/updateMenu`, data).then((res) => res.data);
}

export async function deleteMenu(data: any) {
  return http.post(`${API_PREFIX}/deleteMenu`, data).then((res) => res.data);
}

export async function getMenuDetail(data: any) {
  return http.post(`${API_PREFIX}/getMenuDetail`, data).then((res) => res.data);
}

export async function deleteMenus(data: any) {
  return http.post(`${API_PREFIX}/deleteMenus`, data).then((res) => res.data);
}
