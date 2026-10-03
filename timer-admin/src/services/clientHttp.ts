import axios from "axios";

// C 端（门户）独立登录态：与后台管理的 sessionId 完全隔离
const TOKEN_KEY = "nbaClientToken";
const USER_KEY = "nbaClientUser";

export const getClientToken = () => localStorage.getItem(TOKEN_KEY);
export const setClientToken = (token: string) => localStorage.setItem(TOKEN_KEY, token);
export const getClientUser = () => localStorage.getItem(USER_KEY);
export const setClientUser = (username: string) => localStorage.setItem(USER_KEY, username);
export const clearClientAuth = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
};

const clientHttp = axios.create({
  timeout: 15000,
  headers: { "Content-Type": "application/json" },
});

// 请求拦截器：挂 C 端自己的 Bearer token
clientHttp.interceptors.request.use((config) => {
  const token = getClientToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器：
// 1) 网关轮换/宽限补发的新 token 通过 X-New-Session 下发，收到立即覆盖
// 2) 401 = C 端会话失效 → 清本地凭证并广播事件（门户自行处理，不跳后台登录页）
clientHttp.interceptors.response.use(
  (response) => {
    const newSid = response.headers["x-new-session"];
    if (newSid) {
      setClientToken(newSid);
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      clearClientAuth();
      window.dispatchEvent(new CustomEvent("client-unauthorized"));
    }
    return Promise.reject(error);
  },
);

export default clientHttp;
