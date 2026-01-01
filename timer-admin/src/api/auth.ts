import http from '@/services/http'

const API_PREFIX = '/api/user-server'

// 获取当前用户信息
export function getCurrentUser() {
  return http.get(`${API_PREFIX}/me`)
}   

// 登录：接收任意对象，返回任意响应
export function login(data: any) {
  return http.post(`${API_PREFIX}/login`, data).then(res => res.data)
}