import http from "@/services/http";

const API_PREFIX = "/api/batch-server/jobMonitor";

export async function getJobExecutionCounts() {
  return http.post(`${API_PREFIX}/getJobExecutionCounts`, {}).then(res => res.data)
}