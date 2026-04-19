import http from "@/services/http";

const API_PREFIX = "/api/batch-server/jobMonitor";

export async function getJobExecutionCounts() {
  return http
    .post(`${API_PREFIX}/getJobExecutionCounts`, {})
    .then((res) => res.data);
}

export async function getJobExecutionByDate(data: any) {
  return http
    .post(`${API_PREFIX}/getJobExecutionByDate`, data)
    .then((res) => res.data);
}

export async function getSchedules() {
  return http.post(`${API_PREFIX}/getSchedules`, {}).then((res) => res.data);
}