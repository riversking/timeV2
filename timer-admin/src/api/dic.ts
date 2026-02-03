import http from "@/services/http";

const API_PREFIX = "/api/user-server/dic";

export async function getDicTree(data: any) {
  return http.post(`${API_PREFIX}/getDicTree`, data).then((res) => res.data);
}