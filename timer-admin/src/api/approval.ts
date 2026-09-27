import http from "@/services/http";

const API_PREFIX = "/api/approval-server";

// ==================== 流程定义 ====================

/** 查询最新版本流程定义 */
export async function getLatestDefinition(data: { definitionKey: string }) {
  return http
    .post(`${API_PREFIX}/flowDefinition/getLatest`, data)
    .then((res) => res.data);
}

/** 按 key + version 查询流程定义 */
export async function getDefinitionByKeyAndVersion(data: {
  definitionKey: string;
  version: number;
}) {
  return http
    .post(`${API_PREFIX}/flowDefinition/getByKeyAndVersion`, data)
    .then((res) => res.data);
}

/** 创建流程定义（初始 DRAFT 状态） */
export async function createDefinition(data: {
  definitionKey: string;
  name: string;
  description?: string;
  category?: string;
  icon?: string;
  definitionJson: string;
}) {
  return http
    .post(`${API_PREFIX}/flowDefinition/create`, data)
    .then((res) => res.data);
}

/** 发布流程定义（DRAFT → PUBLISHED） */
export async function publishDefinition(data: { id: number }) {
  return http
    .post(`${API_PREFIX}/flowDefinition/publish`, data)
    .then((res) => res.data);
}

/** 停用流程定义（PUBLISHED → DISABLED） */
export async function disableDefinition(data: { id: number }) {
  return http
    .post(`${API_PREFIX}/flowDefinition/disable`, data)
    .then((res) => res.data);
}

// ==================== 流程实例 ====================

/** 发起流程 */
export async function startProcess(data: {
  definitionKey: string;
  title: string;
  businessKey?: string;
  initiator?: string;
  initiatorName?: string;
  variables?: string;
}) {
  return http.post(`${API_PREFIX}/flow/startProcess`, data).then((res) => res.data);
}

/** 终止流程实例 */
export async function terminateProcess(data: {
  instanceId: number;
  operator?: string;
}) {
  return http
    .post(`${API_PREFIX}/flow/terminateProcess`, data)
    .then((res) => res.data);
}

/** 按实例编号查询 */
export async function getInstanceByNo(data: { instanceNo: string }) {
  return http
    .post(`${API_PREFIX}/flowInstance/getByNo`, data)
    .then((res) => res.data);
}

/** 按业务键查询实例列表 */
export async function listInstancesByBusinessKey(data: { businessKey: string }) {
  return http
    .post(`${API_PREFIX}/flowInstance/listByBusinessKey`, data)
    .then((res) => res.data);
}

/** 我发起的实例（分页） */
export async function listMyInitiated(data: {
  currentPage: number;
  pageSize: number;
}) {
  return http
    .post(`${API_PREFIX}/flowInstance/listMyInitiated`, data)
    .then((res) => res.data);
}

/** 按状态查询实例（分页，RUNNING / COMPLETED / TERMINATED） */
export async function listInstancesByStatus(data: {
  status: string;
  currentPage: number;
  pageSize: number;
}) {
  return http
    .post(`${API_PREFIX}/flowInstance/listByStatus`, data)
    .then((res) => res.data);
}

// ==================== 任务 ====================

/** 我的待办任务（分页） */
export async function listTodoTasks(data: {
  currentPage: number;
  pageSize: number;
}) {
  return http.post(`${API_PREFIX}/task/listTodo`, data).then((res) => res.data);
}

/** 可认领任务（分页） */
export async function listClaimableTasks(data: {
  currentPage: number;
  pageSize: number;
}) {
  return http.post(`${API_PREFIX}/task/listClaimable`, data).then((res) => res.data);
}

/** 按任务编号查询任务详情 */
export async function getTaskByTaskNo(data: { taskNo: string }) {
  return http
    .post(`${API_PREFIX}/task/getByTaskNo`, data)
    .then((res) => res.data);
}

/** 认领任务 */
export async function claimTask(data: {
  taskNo: string;
  instanceId?: string;
  userId?: string;
}) {
  return http.post(`${API_PREFIX}/task/claim`, data).then((res) => res.data);
}

/** 审批通过（继续推进后续节点） */
export async function approveTask(data: {
  taskNo: string;
  comment?: string;
  instanceId?: string;
  userId?: string;
}) {
  return http.post(`${API_PREFIX}/task/approve`, data).then((res) => res.data);
}

/** 拒绝（实例直接结束为 COMPLETED） */
export async function rejectTask(data: {
  taskNo: string;
  comment?: string;
  instanceId?: string;
  userId?: string;
}) {
  return http.post(`${API_PREFIX}/task/reject`, data).then((res) => res.data);
}

/** 退回（实例直接结束为 COMPLETED） */
export async function returnTask(data: {
  taskNo: string;
  comment?: string;
  instanceId?: string;
  userId?: string;
}) {
  return http.post(`${API_PREFIX}/task/return`, data).then((res) => res.data);
}

/** 取消任务 */
export async function cancelTask(data: {
  taskNo: string;
  operator?: string;
  instanceId?: string;
  userId?: string;
}) {
  return http.post(`${API_PREFIX}/task/cancel`, data).then((res) => res.data);
}

/** 转交任务（仅已认领任务，operator 必须为当前办理人） */
export async function transferTask(data: {
  taskNo: string;
  targetUser: string;
  operator?: string;
  instanceId?: string;
}) {
  return http.post(`${API_PREFIX}/task/transfer`, data).then((res) => res.data);
}

// ==================== 历史 / 跟踪 ====================

/** 按实例查询历史流水 */
export async function listHistoryByInstance(data: { instanceId: number }) {
  return http
    .post(`${API_PREFIX}/flowHistory/listByInstance`, data)
    .then((res) => res.data);
}

/** 按任务查询历史流水 */
export async function listHistoryByTask(data: { taskId: number }) {
  return http
    .post(`${API_PREFIX}/flowHistory/listByTask`, data)
    .then((res) => res.data);
}

/** 我的操作历史（分页） */
export async function listHistoryByOperator(data: {
  currentPage: number;
  pageSize: number;
  operatorId?: string;
}) {
  return http
    .post(`${API_PREFIX}/flowHistory/listByOperator`, data)
    .then((res) => res.data);
}

/** 流程跟踪链（发起 → 各审批环节 → 结束） */
export async function trackByInstance(data: { instanceId: number }) {
  return http
    .post(`${API_PREFIX}/flowHistory/trackByInstance`, data)
    .then((res) => res.data);
}

// ==================== 通用工具 ====================

/** 从本地缓存的当前用户信息中取 userId / username（Header 下拉登录时写入） */
export function getCurrentUserInfo(): { userId: string; username: string } {
  try {
    const raw = localStorage.getItem("user");
    if (raw) {
      const u = JSON.parse(raw);
      return { userId: u.userId || "", username: u.username || "" };
    }
  } catch {
    // ignore
  }
  return { userId: "", username: "" };
}
