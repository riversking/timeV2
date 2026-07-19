import axios from "axios";

const http = axios.create({
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true,    // ★ 跨域时自动携带 cookie
});

// ★ 请求拦截器：不再需要手动加 Authorization 头，cookie 自动带上
// （保留空壳，方便以后扩展）

// ★ 响应拦截器：不再需要 401 → refresh → retry 逻辑，网关已处理
// 只保留最终兜底：双 token 全失效 → 跳转登录
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // 会话彻底失效，跳登录
      window.location.href = "/login";
    }
    console.error("API Error:", error);
    return Promise.reject(error);
  },
);

export default http;