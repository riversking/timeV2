import http from "@/services/http";

const API_PREFIX = "/api/nba-server";

/**
 * 球员分页查询（支持姓名模糊）
 */
export async function getPlayerPage(data: any) {
  return http
    .post(`${API_PREFIX}/player/getPlayerPage`, data)
    .then((res) => res.data);
}

/**
 * 球馆分页查询（支持名称模糊）
 */
export async function getStadiumPage(data: any) {
  return http
    .post(`${API_PREFIX}/stadium/getStadiumPage`, data)
    .then((res) => res.data);
}

/**
 * 球队分页查询（支持名称/城市模糊）
 */
export async function getTeamPage(data: any) {
  return http.post(`${API_PREFIX}/team/getTeamPage`, data).then((res) => res.data);
}

/**
 * 球员详情
 */
export async function getPlayerDetail(data: any) {
  return http
    .post(`${API_PREFIX}/player/getPlayerDetail`, data)
    .then((res) => res.data);
}

/**
 * 球馆详情
 */
export async function getStadiumDetail(data: any) {
  return http
    .post(`${API_PREFIX}/stadium/getStadiumDetail`, data)
    .then((res) => res.data);
}

/**
 * 球队详情
 */
export async function getTeamDetail(data: any) {
  return http
    .post(`${API_PREFIX}/team/getTeamDetail`, data)
    .then((res) => res.data);
}
