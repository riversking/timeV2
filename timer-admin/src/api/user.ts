import http from "@/services/http";

const API_PREFIX = "/api/user-server/user";

export async function getUserMenu() {
  return http.post(`${API_PREFIX}/ownedMenuTree`, {}).then((res) => res.data);
}

export async function getUserPage(data: any) {
  return http.post(`${API_PREFIX}/getUserPage`, data).then((res) => res.data);
}

export async function saveUser(data: any) {
  return http.post(`${API_PREFIX}/saveUser`, data).then((res) => res.data);
}

export async function getCurrentUser() {
  return http.post(`${API_PREFIX}/getCurrentUser`, {}).then((res) => res.data);
}

export async function getUserDetail(data: any) {
  return http.post(`${API_PREFIX}/getUserDetail`, data).then((res) => res.data);
}

export async function updateUser(data: any) {
  return http.post(`${API_PREFIX}/updateUser`, data).then((res) => res.data);
}

export async function deleteUser(data: any) {
  return http.post(`${API_PREFIX}/deleteUser`, data).then((res) => res.data);
}

export async function enableUser(data: any) {
  return http.post(`${API_PREFIX}/enableUser`, data).then((res) => res.data);
}

export async function disableUser(data: any) {
  return http.post(`${API_PREFIX}/disableUser`, data).then((res) => res.data);
}

export async function resetPassword(data: any) {
  return http.post(`${API_PREFIX}/resetPassword`, data).then((res) => res.data);
}
