import http from '@/services/http'

const API_PREFIX = '/api/user-server'

// 获取当前用户信息
export async function getCurrentUser() {
  return http.get(`${API_PREFIX}/me`)
}   

// 登录：接收任意对象，返回任意响应
export async function login(data: any) {
  return http.post(`${API_PREFIX}/login`, data).then(res => res.data)
}

// 刷新token
export async function refresh(data: any) {
  return http.post(`${API_PREFIX}/refresh`, data).then((res) => res.data);
}

// 自动登录
export async function autoLogin() {
  return http.post(`${API_PREFIX}/autoLogin`).then((res) => res.data);
}