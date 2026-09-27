import http from "@/services/http";

const API_PREFIX = "/api/rule-engine-server/rule";

/**
 * 定义规则校验：JSON 可解析 + 排他网关内嵌规则逐条校验。
 * 返回 { code, message, data: { errors: [{ nodeId, ruleIndex, message }] } }
 */
export async function validateDefinitionRule(data: { definitionJson: string }) {
  return http
    .post(`${API_PREFIX}/validateDefinition`, data)
    .then((res) => res.data);
}

/**
 * 审批人表达式解析：$leader 游标 / ${name} 变量通道 / 静态直写，双语法。
 * 未解析 token 保留字面原文。
 */
export async function resolveAssignees(data: {
  expr: string;
  initiator?: string;
  variablesJson?: string;
}) {
  return http
    .post(`${API_PREFIX}/resolveAssignees`, data)
    .then((res) => res.data);
}

/**
 * 排他网关路由（多出边三级降级：内嵌规则 → 边条件 → 默认边）。
 * 返回 { code, message, data: { targetNodeId, outputVariablesJson, hitSource } }
 * hitSource: EMBEDDED_RULE / EDGE_CONDITION / DEFAULT_EDGE
 */
export async function routeGateway(data: {
  nodeId: string;
  rules?: Array<{
    condition: string;
    targetNodeId: string;
    priority: number;
    outputMappingJson?: string;
  }>;
  edges?: Array<{ target: string; conditionExpression?: string }>;
  variablesJson?: string;
}) {
  return http.post(`${API_PREFIX}/routeGateway`, data).then((res) => res.data);
}
