// src/services/http.ts
import axios from 'axios'

// 创建 axios 实例
const http = axios.create({
//   baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8006/',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器：自动添加 token（如果已登录）
http.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一处理错误
http.interceptors.response.use(
  response => response,
  error => {
    // 可以在这里统一弹出错误提示（如使用 ElMessage、Toast 等）
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export default http