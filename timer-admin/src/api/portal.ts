import clientHttp from "@/services/clientHttp";

const API_PREFIX = "/api/nba-server";

/**
 * 球员分页查询（公开）
 */
export async function getPlayerPage(data: any) {
  return clientHttp
    .post(`${API_PREFIX}/player/getPlayerPage`, data)
    .then((res) => res.data);
}

/**
 * 球队分页查询（公开）
 */
export async function getTeamPage(data: any) {
  return clientHttp
    .post(`${API_PREFIX}/team/getTeamPage`, data)
    .then((res) => res.data);
}

/**
 * 赛程分页查询（公开，支持 season/date/teamId 筛选，按比赛日正序）
 */
export async function getGamePage(data: any) {
  return clientHttp
    .post(`${API_PREFIX}/game/getGamePage`, data)
    .then((res) => res.data);
}

/**
 * 最近的比赛（公开，比赛日不早于当前的最近几场）
 */
export async function getRecentGames() {
  return clientHttp
    .post(`${API_PREFIX}/game/getRecentGames`)
    .then((res) => res.data);
}

/**
 * 比赛日历（公开，按月汇总每天场次，支持 season/teamId 筛选）
 */
export async function getGameCalendar(data: any) {
  return clientHttp
    .post(`${API_PREFIX}/game/getGameCalendar`, data)
    .then((res) => res.data);
}

/**
 * NBA 新闻资讯（公开，含配图）
 */
export async function getNews(data: any) {
  return clientHttp
    .post(`${API_PREFIX}/news/getNews`, data)
    .then((res) => res.data);
}

/**
 * 球员详情（需登录）
 */
export async function getPlayerDetail(data: any) {
  return clientHttp
    .post(`${API_PREFIX}/player/getPlayerDetail`, data)
    .then((res) => res.data);
}

/**
 * 球队详情（需登录）
 */
export async function getTeamDetail(data: any) {
  return clientHttp
    .post(`${API_PREFIX}/team/getTeamDetail`, data)
    .then((res) => res.data);
}
