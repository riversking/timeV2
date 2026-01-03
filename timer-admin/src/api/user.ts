import http from '@/services/http'

const API_PREFIX = '/api/user-server'


export function getUserMenu() {
  return http.post(`${API_PREFIX}/user/ownedMenuTree`, {}).then(res => res.data)
}