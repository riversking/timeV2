// src/api/role.ts
import http from "@/services/http";

const API_PREFIX = "/api/user-server/role";

export async function getRolePage(data: any) {
  return http.post(`${API_PREFIX}/getRolePage`, data).then((res) => res.data);
}

export async function saveRole(data: any) {
  return http.post(`${API_PREFIX}/saveRole`, data).then((res) => res.data);
}

export async function getRoleDetail(data: any) {
  return http.post(`${API_PREFIX}/getRoleDetail`, data).then((res) => res.data);
}

export async function updateRole(data: any) {
  return http.post(`${API_PREFIX}/updateRole`, data).then((res) => res.data);
}

export async function deleteRole(data: any) {
  return http.post(`${API_PREFIX}/deleteRole`, data).then((res) => res.data);
}

export async function getUserRolePage(data: any) {
  return http.post(`${API_PREFIX}/getUserRolePage`, data).then((res) => res.data);
}