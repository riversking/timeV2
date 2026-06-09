import http from "@/services/http";

const API_PREFIX = "/api/im-server/wsTicket";

export async function createTicket() {
  return http.post(`${API_PREFIX}/createTicket`, {}).then((res) => res.data);
}

export async function getFriendRequestPage(data?: any) {
  return http
    .post("/friend/getFriendRequestPage", data || {})
    .then((res) => res.data);
}

export async function getFriendPage(data?: any) {
  return http
    .post("/friend/getFriendPage", data || {})
    .then((res) => res.data);
}