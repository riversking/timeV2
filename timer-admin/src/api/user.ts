import http from "@/services/http";

const API_PREFIX = "/api/user-server/user";

export function getUserMenu() {
  return http.post(`${API_PREFIX}/ownedMenuTree`, {}).then((res) => res.data);
}

export function getUserPage(data: any) {
  return http.post(`${API_PREFIX}/getUserPage`, data).then((res) => res.data);
}
