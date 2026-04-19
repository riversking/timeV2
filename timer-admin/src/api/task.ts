import http from "@/services/http";

const API_PREFIX = "/api/batch-server/task";

export async function getJobPage(data: any) {
  return http
    .post(`${API_PREFIX}/getJobPage`, data)
    .then((res) => res.data);
}

export async function saveJob(data: any) {
  return http
    .post(`${API_PREFIX}/saveJob`, data)
    .then((res) => res.data);
}

export async function updateJob(data: any) {
  return http
    .post(`${API_PREFIX}/updateJob`, data)
    .then((res) => res.data);
}

export async function deleteJob(data: any) {
  return http
    .post(`${API_PREFIX}/deleteJob`, data)
    .then((res) => res.data);
}

export async function startJob(data: any) {
  return http.post(`${API_PREFIX}/startJob`, data).then((res) => res.data);
} 

export async function pauseJob(data: any) {
  return http.post(`${API_PREFIX}/stopJob`, pauseJob).then((res) => res.data);
}