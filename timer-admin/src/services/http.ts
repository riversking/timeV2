import axios from "axios";

const SESSION_KEY = "sessionId";

export const getSessionId = () => sessionStorage.getItem(SESSION_KEY);
export const setSessionId = (sid: string) => sessionStorage.setItem(SESSION_KEY, sid);
export const clearSessionId = () => sessionStorage.removeItem(SESSION_KEY);

const http = axios.create({
  timeout: 10000,
  headers: { "Content-Type": "application/json" },
});

// 请求拦截器：所有请求统一挂 Authorization: Bearer <sessionId>
http.interceptors.request.use((config) => {
  const sid = getSessionId();
  if (sid) {
    config.headers.Authorization = `Bearer ${sid}`;
  }
  return config;
});

// 响应拦截器：
// 1) 网关轮换/宽限补发的新 sid 通过 X-New-Session 响应头下发，收到立即覆盖本地存储
// 2) HTTP 401 = 会话彻底失效 → 清本地 sid 并跳登录
http.interceptors.response.use(
  (response) => {
    const newSid = response.headers["x-new-session"];
    if (newSid) {
      setSessionId(newSid);
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      clearSessionId();
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    console.error("API Error:", error);
    return Promise.reject(error);
  },
);

export default http;