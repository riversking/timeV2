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
  response => {
    // 检查响应体中的code字段是否为401
    if (response.data && response.data.code === 401) {
      // 清除本地存储的token
      localStorage.removeItem('token');
      // 抛出错误，让业务代码处理跳转
      window.location.href = '/login';
      return Promise.reject(new Error('Unauthorized'));
    }
    return response;
  },
  error => {
    // 检查HTTP状态码是否为401
    if (error.response && error.response.status === 401) {
      // 清除本地存储的token
      localStorage.removeItem('token');
      // 直接跳转到登录页
      window.location.href = '/login';
    }
    
    // 可以在这里统一弹出错误提示（如使用 ElMessage、Toast 等）
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export default http