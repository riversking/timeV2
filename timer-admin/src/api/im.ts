import http from "@/services/http";

const API_PREFIX = "/api/im-server/wsTicket";

export async function createTicket() {
  return http.post(`${API_PREFIX}/createTicket`, {}).then((res) => res.data);
}
