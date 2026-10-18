import clientHttp from "@/services/clientHttp";

const API_PREFIX = "/api/nba-server";
const MOMENT_PREFIX = "/api/moment-server";

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
 * 比分实时推送流（SSE 长连接，公开；后台发现比分变动主动推送，前台无需轮询）
 */
export function openScoreStream(): EventSource {
  return new EventSource(`${API_PREFIX}/game/scoreStream`);
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
 * 比赛详情（公开，含每节比分与双方球员数据，实时取数据源）
 */
export async function getGameDetail(data: any) {
  return clientHttp
    .post(`${API_PREFIX}/game/getGameDetail`, data)
    .then((res) => res.data);
}

/**
 * 球队排名（公开，东西部与分区名次由服务端按胜率计算）
 */
export async function getTeamStandings(data: any) {
  return clientHttp
    .post(`${API_PREFIX}/ranking/getTeamStandings`, data)
    .then((res) => res.data);
}

/**
 * 球员数据排名（公开，value 为场均数值，命中率为百分比）
 */
export async function getPlayerStatLeaders(data: any) {
  return clientHttp
    .post(`${API_PREFIX}/ranking/getPlayerStatLeaders`, data)
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
 * NBA 新闻详情（公开，返回清洗后的正文 HTML）
 */
export async function getNewsDetail(data: any) {
  return clientHttp
    .post(`${API_PREFIX}/news/getNewsDetail`, data)
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

/**
 * 发布动态（需登录，作者身份由网关在报文中注入 loginUser；teamId=0 表示不归属球队社区）
 */
export async function publishMoment(data: {
  content: string;
  images: string[];
  teamId?: number;
}) {
  return clientHttp
    .post(`${MOMENT_PREFIX}/moment/publishMoment`, data)
    .then((res) => res.data);
}

/**
 * 动态分页查询（公开，按 id 倒序；teamId=0 仅主动态，>0 仅该社区文章，两类严格隔离）
 */
export async function getMomentPage(data: {
  currentPage: number;
  pageSize: number;
  userId?: string;
  teamId?: number;
  viewerId?: string;
}) {
  return clientHttp
    .post(`${MOMENT_PREFIX}/moment/getMomentPage`, data)
    .then((res) => res.data);
}

/**
 * 点赞 / 取消点赞（需登录，返回切换后的状态与点赞数）
 */
export async function toggleMomentLike(data: { momentId: number }) {
  return clientHttp
    .post(`${MOMENT_PREFIX}/moment/toggleLike`, data)
    .then((res) => res.data);
}

/**
 * 评论分页（公开，按 id 倒序）
 */
export async function getMomentComments(data: {
  momentId: number;
  currentPage: number;
  pageSize: number;
}) {
  return clientHttp
    .post(`${MOMENT_PREFIX}/moment/getCommentPage`, data)
    .then((res) => res.data);
}

/**
 * 发表评论（需登录，返回落库评论与最新评论数）
 */
export async function addMomentComment(data: { momentId: number; content: string }) {
  return clientHttp
    .post(`${MOMENT_PREFIX}/moment/addComment`, data)
    .then((res) => res.data);
}

/**
 * 删除动态（需登录，仅能删除本人发布的动态）
 */
export async function deleteMoment(data: { momentId: number }) {
  return clientHttp
    .post(`${MOMENT_PREFIX}/moment/deleteMoment`, data)
    .then((res) => res.data);
}

/**
 * 加入球队社区（需登录）
 */
export async function joinCommunity(data: { teamId: number }) {
  return clientHttp
    .post(`${MOMENT_PREFIX}/moment/joinCommunity`, data)
    .then((res) => res.data);
}

/**
 * 退出球队社区（需登录）
 */
export async function leaveCommunity(data: { teamId: number }) {
  return clientHttp
    .post(`${MOMENT_PREFIX}/moment/leaveCommunity`, data)
    .then((res) => res.data);
}

/**
 * 我的社交数据：加入的社区、我关注的人、我的粉丝（需登录）
 */
export async function getMySocial() {
  return clientHttp
    .post(`${MOMENT_PREFIX}/moment/getMySocial`, {})
    .then((res) => res.data);
}

/**
 * 关注用户（需登录）
 */
export async function followUser(data: { targetUserId: string; targetUserName: string }) {
  return clientHttp
    .post(`${MOMENT_PREFIX}/moment/followUser`, data)
    .then((res) => res.data);
}

/**
 * 取消关注（需登录）
 */
export async function unfollowUser(data: { targetUserId: string }) {
  return clientHttp
    .post(`${MOMENT_PREFIX}/moment/unfollowUser`, data)
    .then((res) => res.data);
}
