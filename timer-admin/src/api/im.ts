import http from "@/services/http";

const API_PREFIX = "/api/im-server";

export async function createTicket() {
  return http.post(`${API_PREFIX}/wsTicket/createTicket`, {}).then((res) => res.data);
}

export async function getFriendRequestPage(data?: any) {
  return http
    .post(`${API_PREFIX}/friend/getFriendRequestPage`, data || {})
    .then((res) => res.data);
}

export async function getFriendPage(data?: any) {
  return http
    .post(`${API_PREFIX}/friend/getFriendList`, data || {})
    .then((res) => res.data);
}
