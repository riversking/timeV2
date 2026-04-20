import http from "@/services/http";

const API_PREFIX = "/api/batch-server/task";

export async function getJobPage(data: any) {
  return http.post(`${API_PREFIX}/getJobPage`, data).then((res) => res.data);
}

export async function saveTaskInfo(data: any) {
  return http.post(`${API_PREFIX}/saveTaskInfo`, data).then((res) => res.data);
}

export async function updateTaskInfo(data: any) {
  return http
    .post(`${API_PREFIX}/updateTaskInfo`, data)
    .then((res) => res.data);
}

export async function pauseTask(data: any) {
  return http.post(`${API_PREFIX}/pauseTask`, data).then((res) => res.data);
}

export async function resumeTask(data: any) {
  return http.post(`${API_PREFIX}/resumeTask`, data).then((res) => res.data);
}

export async function deleteTask(data: any) {
  return http.post(`${API_PREFIX}/deleteTask`, data).then((res) => res.data);
}

export async function runTask(data: any) {
  return http.post(`${API_PREFIX}/runTask`, data).then((res) => res.data);
}
