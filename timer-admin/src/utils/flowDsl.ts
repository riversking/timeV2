/**
 * 流程定义 DSL 工具：与 approval-server 的 ProcessDefinition / NodeDef / EdgeDef 对齐。
 *
 * <p>职责：
 * <ul>
 *   <li>DSL JSON ⇄ VueFlow 元素（nodes/edges）双向转换</li>
 *   <li>基于边拓扑的自动布局（从左到右分层）</li>
 *   <li>本地结构校验（与创建接口强校验、引擎运行时约束对齐）</li>
 * </ul>
 */
import { MarkerType } from "@vue-flow/core";

// ==================== DSL 类型（对齐后端 record 结构） ====================

export interface DslNode {
  id: string;
  /** START / END / USER_TASK / EXCLUSIVE_GATEWAY / PARALLEL_GATEWAY */
  type: string;
  name: string;
  config?: Record<string, any>;
}

export interface DslEdge {
  id: string;
  source: string;
  target: string;
  /** SpEL 条件表达式，排他网关降级用，非网关边可为空 */
  conditionExpression?: string;
}

export interface DslDefinition {
  key?: string;
  name?: string;
  nodes: DslNode[];
  edges: DslEdge[];
}

// ==================== 节点类型元数据 ====================

export interface NodeTypeMeta {
  type: string;
  label: string;
  color: string;
  glyph: string;
  desc: string;
}

export const NODE_TYPES: NodeTypeMeta[] = [
  {
    type: "START",
    label: "开始",
    color: "#10b981",
    glyph: "▶",
    desc: "流程唯一入口，无入边",
  },
  {
    type: "USER_TASK",
    label: "审批任务",
    color: "#3b82f6",
    glyph: "✎",
    desc: "人工审批，可配审批人/任务模式",
  },
  {
    type: "EXCLUSIVE_GATEWAY",
    label: "排他网关",
    color: "#f59e0b",
    glyph: "◇",
    desc: "条件分支：规则 → 边条件 → 默认边",
  },
  {
    type: "PARALLEL_GATEWAY",
    label: "并行网关",
    color: "#8b5cf6",
    glyph: "‖",
    desc: "出边≥2 分流，入边≥2 汇聚",
  },
  {
    type: "END",
    label: "结束",
    color: "#ef4444",
    glyph: "■",
    desc: "流程终点，无出边",
  },
];

export function nodeTypeMeta(type: string): NodeTypeMeta {
  return (
    NODE_TYPES.find((t) => t.type === type) || {
      type,
      label: type || "未知节点",
      color: "#64748b",
      glyph: "?",
      desc: "未知节点类型",
    }
  );
}

// ==================== id 生成 ====================

const ID_PREFIX: Record<string, string> = {
  START: "start",
  END: "end",
  USER_TASK: "task",
  EXCLUSIVE_GATEWAY: "gw_ex",
  PARALLEL_GATEWAY: "gw_par",
};

export function genNodeId(type: string, existing: Set<string>): string {
  const prefix = ID_PREFIX[type] || "node";
  if (!existing.has(prefix)) {
    return prefix;
  }
  let i = 2;
  while (existing.has(`${prefix}_${i}`)) {
    i++;
  }
  return `${prefix}_${i}`;
}

export function genEdgeId(existing: Set<string>): string {
  let i = 1;
  while (existing.has(`e_${i}`)) {
    i++;
  }
  return `e_${i}`;
}

// ==================== 骨架 / 解析 ====================

export function createDefaultDefinition(key = "", name = ""): DslDefinition {
  return {
    key,
    name,
    nodes: [
      { id: "start", type: "START", name: "开始" },
      { id: "end", type: "END", name: "结束" },
    ],
    edges: [{ id: "e_1", source: "start", target: "end" }],
  };
}

const deepCopy = <T>(obj: T): T => JSON.parse(JSON.stringify(obj));

/**
 * 容错解析 definitionJson 字符串；结构不合法（无 nodes 数组）返回 null。
 */
export function parseDefinition(raw?: string | null): DslDefinition | null {
  if (!raw || !raw.trim()) {
    return null;
  }
  try {
    const obj = JSON.parse(raw);
    if (!obj || typeof obj !== "object" || !Array.isArray(obj.nodes)) {
      return null;
    }
    return {
      key: typeof obj.key === "string" ? obj.key : "",
      name: typeof obj.name === "string" ? obj.name : "",
      nodes: obj.nodes.map((n: any) => ({
        id: String(n?.id ?? ""),
        type: String(n?.type ?? ""),
        name: String(n?.name ?? ""),
        config: n?.config && typeof n.config === "object" ? deepCopy(n.config) : {},
      })),
      edges: (Array.isArray(obj.edges) ? obj.edges : []).map((e: any) => ({
        id: String(e?.id ?? ""),
        source: String(e?.source ?? ""),
        target: String(e?.target ?? ""),
        conditionExpression:
          typeof e?.conditionExpression === "string" ? e.conditionExpression : "",
      })),
    };
  } catch {
    return null;
  }
}

// ==================== 自动布局（从左到右分层） ====================

const LAYER_GAP_X = 330;
const NODE_GAP_Y = 150;
const ORIGIN_X = 60;
const ORIGIN_Y = 60;

/**
 * BFS 分层布局：START 为第 0 层，孤立/不可达节点排在最后一层。
 */
export function computeLayout(def: DslDefinition): Map<string, { x: number; y: number }> {
  const result = new Map<string, { x: number; y: number }>();
  const depthOf = new Map<string, number>();
  const adjacency = new Map<string, string[]>();
  for (const e of def.edges) {
    if (!adjacency.has(e.source)) {
      adjacency.set(e.source, []);
    }
    adjacency.get(e.source)!.push(e.target);
  }
  const start = def.nodes.find((n) => n.type === "START");
  if (start) {
    const queue: string[] = [start.id];
    depthOf.set(start.id, 0);
    while (queue.length) {
      const cur = queue.shift()!;
      const depth = depthOf.get(cur)!;
      for (const next of adjacency.get(cur) || []) {
        if (!depthOf.has(next)) {
          depthOf.set(next, depth + 1);
          queue.push(next);
        }
      }
    }
  }
  let maxDepth = 0;
  depthOf.forEach((d) => {
    if (d > maxDepth) {
      maxDepth = d;
    }
  });
  const layers = new Map<number, string[]>();
  for (const n of def.nodes) {
    const depth = depthOf.has(n.id) ? depthOf.get(n.id)! : maxDepth + 1;
    if (!layers.has(depth)) {
      layers.set(depth, []);
    }
    layers.get(depth)!.push(n.id);
  }
  const maxRows = Math.max(1, ...[...layers.values()].map((l) => l.length));
  layers.forEach((ids, depth) => {
    const offsetY = ((maxRows - ids.length) * NODE_GAP_Y) / 2;
    ids.forEach((id, idx) => {
      result.set(id, {
        x: ORIGIN_X + depth * LAYER_GAP_X,
        y: ORIGIN_Y + offsetY + idx * NODE_GAP_Y,
      });
    });
  });
  return result;
}

/**
 * 对 VueFlow 元素数组原地应用自动布局（仅改 position）。
 */
export function autoLayoutElements(nodes: any[], edges: any[]): void {
  const def: DslDefinition = {
    nodes: nodes.map((n) => ({ id: n.id, type: n.data?.dslType || "", name: "" })),
    edges: edges.map((e) => ({ id: e.id, source: e.source, target: e.target })),
  };
  const layout = computeLayout(def);
  for (const n of nodes) {
    const pos = layout.get(n.id);
    if (pos) {
      n.position = { ...pos };
    }
  }
}

// ==================== DSL ⇄ VueFlow 元素转换 ====================

/**
 * DSL 网关规则（UI 编辑形态）：outputMapping 以 JSON 字符串编辑，导出时转对象。
 */
function toRuleUi(rule: any): any {
  return {
    condition: rule?.condition ?? "",
    targetNodeId: rule?.targetNodeId ?? "",
    priority: rule?.priority ?? 0,
    outputMappingJson:
      rule?.outputMapping && typeof rule.outputMapping === "object"
        ? JSON.stringify(rule.outputMapping)
        : "",
  };
}

export function toElements(def: DslDefinition): { nodes: any[]; edges: any[] } {
  const layout = computeLayout(def);
  const nodes = def.nodes.map((n) => {
    const config = deepCopy(n.config || {});
    if (Array.isArray(config.rules)) {
      config.rules = config.rules.map(toRuleUi);
    }
    // 节点级 outputMapping 同样以 JSON 字符串编辑，导出时转回对象
    if (config.outputMapping && typeof config.outputMapping === "object") {
      config.outputMappingJson = JSON.stringify(config.outputMapping);
      delete config.outputMapping;
    }
    return {
      id: n.id,
      type: "process",
      position: layout.get(n.id) || { x: 0, y: 0 },
      data: { dslType: n.type, name: n.name || "", config },
    };
  });
  const edges = def.edges.map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    type: "condition",
    markerEnd: MarkerType.ArrowClosed,
    data: { conditionExpression: e.conditionExpression || "" },
  }));
  return { nodes, edges };
}

/**
 * 清理节点 config：剔除空值/空数组；rules 白名单挑选并转回 outputMapping 对象。
 */
function cleanConfig(raw: Record<string, any>): Record<string, any> | undefined {
  const out: Record<string, any> = {};
  for (const [k, v] of Object.entries(raw || {})) {
    if (k === "rules") {
      const rules = (Array.isArray(v) ? v : [])
        .filter((r: any) => r && (r.condition || r.targetNodeId))
        .map((r: any) => {
          const rule: Record<string, any> = {
            condition: r.condition || "",
            targetNodeId: r.targetNodeId || "",
          };
          if (r.priority !== undefined && r.priority !== null && r.priority !== "") {
            rule.priority = r.priority;
          }
          const mapping = parseOutputMapping(r.outputMappingJson);
          if (mapping) {
            rule.outputMapping = mapping;
          }
          return rule;
        });
      if (rules.length) {
        out.rules = rules;
      }
      continue;
    }
    if (k === "outputMappingJson") {
      const mapping = parseOutputMapping(v);
      if (mapping) {
        out.outputMapping = mapping;
      }
      continue;
    }
    if (v === undefined || v === null || v === "") {
      continue;
    }
    if (Array.isArray(v) && v.length === 0) {
      continue;
    }
    out[k] = v;
  }
  return Object.keys(out).length ? out : undefined;
}

export function parseOutputMapping(text?: string): Record<string, any> | null {
  if (!text || !text.trim()) {
    return null;
  }
  try {
    const v = JSON.parse(text);
    return v && typeof v === "object" && !Array.isArray(v) ? v : null;
  } catch {
    return null;
  }
}

/**
 * VueFlow 元素 → DSL 定义（导出时调用，meta 提供 key/name）。
 */
export function toDefinition(
  nodes: any[],
  edges: any[],
  meta: { key?: string; name?: string },
): DslDefinition {
  const nodeIds = new Set(nodes.map((n) => n.id));
  return {
    key: meta.key || "",
    name: meta.name || "",
    nodes: nodes.map((n) => {
      const config = cleanConfig(n.data?.config || {});
      const node: DslNode = {
        id: n.id,
        type: n.data?.dslType || "",
        name: n.data?.name || "",
      };
      if (config) {
        node.config = config;
      }
      return node;
    }),
    edges: edges
      .filter((e) => nodeIds.has(e.source) && nodeIds.has(e.target))
      .map((e) => {
        const edge: DslEdge = { id: e.id, source: e.source, target: e.target };
        const cond = e.data?.conditionExpression;
        if (typeof cond === "string" && cond.trim()) {
          edge.conditionExpression = cond.trim();
        }
        return edge;
      }),
  };
}

// ==================== 本地结构校验 ====================

export interface ValidateResult {
  errors: string[];
  warnings: string[];
}

/**
 * 与创建接口强校验、引擎运行时约束对齐的本地结构校验：
 * 唯一 START、END 无出边、边引用存在、网关规则目标属于出边目标集合等。
 */
export function validateDefinition(def: DslDefinition): ValidateResult {
  const errors: string[] = [];
  const warnings: string[] = [];
  if (!def.nodes.length) {
    errors.push("流程为空：至少需要 START 与 END 节点");
    return { errors, warnings };
  }
  const starts = def.nodes.filter((n) => n.type === "START");
  const ends = def.nodes.filter((n) => n.type === "END");
  if (starts.length === 0) {
    errors.push("缺少 START 节点（流程入口）");
  } else if (starts.length > 1) {
    errors.push(`存在 ${starts.length} 个 START 节点，运行时只会使用第一个`);
  }
  if (ends.length === 0) {
    errors.push("缺少 END 节点（流程终点）");
  }

  const nodeIds = new Set<string>();
  for (const n of def.nodes) {
    if (!n.id) {
      errors.push("存在 id 为空的节点");
    } else {
      if (nodeIds.has(n.id)) {
        errors.push(`节点 id 重复: ${n.id}`);
      }
      if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(n.id)) {
        warnings.push(`节点 id "${n.id}" 含非常规字符（建议字母开头，仅字母/数字/下划线）`);
      }
      nodeIds.add(n.id);
    }
  }

  const outDegree = new Map<string, number>();
  const inDegree = new Map<string, number>();
  const edgePairs = new Set<string>();
  for (const e of def.edges) {
    if (!nodeIds.has(e.source)) {
      errors.push(`连线 ${e.id} 的起点节点不存在: ${e.source}`);
    }
    if (!nodeIds.has(e.target)) {
      errors.push(`连线 ${e.id} 的终点节点不存在: ${e.target}`);
    }
    if (e.source && e.source === e.target) {
      errors.push(`存在自环连线: ${e.id}（${e.source}）`);
    }
    const pair = `${e.source} -> ${e.target}`;
    if (edgePairs.has(pair)) {
      warnings.push(`存在重复连线: ${pair}`);
    }
    edgePairs.add(pair);
    outDegree.set(e.source, (outDegree.get(e.source) || 0) + 1);
    inDegree.set(e.target, (inDegree.get(e.target) || 0) + 1);
  }

  for (const s of starts) {
    if ((inDegree.get(s.id) || 0) > 0) {
      errors.push(`START 节点 ${s.id} 不允许有入边`);
    }
  }
  for (const en of ends) {
    if ((outDegree.get(en.id) || 0) > 0) {
      errors.push(`END 节点 ${en.id} 不允许有出边`);
    }
  }

  for (const n of def.nodes) {
    const label = `"${n.name || n.id}"`;
    const config: Record<string, any> = n.config || {};
    if (n.type === "USER_TASK") {
      const hasExpr = typeof config.candidateExpr === "string" && config.candidateExpr.trim();
      const hasUsers = Array.isArray(config.candidateUsers) && config.candidateUsers.length > 0;
      const hasAssignee = typeof config.assignee === "string" && config.assignee.trim();
      if (!hasExpr && !hasUsers && !hasAssignee) {
        warnings.push(`审批任务 ${label} 未配置审批人（表达式/候选/兜底均为空，将生成无办理人的任务）`);
      }
      const resultVar = typeof config.resultVar === "string" ? config.resultVar.trim() : "";
      if (resultVar && !/^[A-Za-z][A-Za-z0-9_]*$/.test(resultVar)) {
        errors.push(`审批任务 ${label}: resultVar "${config.resultVar}" 非法（需字母开头，仅字母/数字/下划线）`);
      }
      const returnMode =
        typeof config.returnMode === "string" ? config.returnMode.trim().toUpperCase() : "";
      if (returnMode && returnMode !== "TERMINATE" && returnMode !== "REWORK") {
        errors.push(`审批任务 ${label}: returnMode "${config.returnMode}" 仅支持 TERMINATE / REWORK`);
      }
      const outputMappingRaw = config.outputMappingJson;
      if (outputMappingRaw && String(outputMappingRaw).trim() && !parseOutputMapping(String(outputMappingRaw))) {
        errors.push(`审批任务 ${label}: outputMapping 不是合法的 JSON 对象`);
      }
    }
    if (n.type === "EXCLUSIVE_GATEWAY") {
      const targets = def.edges.filter((e) => e.source === n.id).map((e) => e.target);
      const rules: any[] = Array.isArray(config.rules) ? config.rules : [];
      rules.forEach((r, i) => {
        const ruleLabel = `网关 ${label} 规则#${i + 1}`;
        if (!r?.condition || !String(r.condition).trim()) {
          errors.push(`${ruleLabel}: condition 不能为空`);
        }
        if (!r?.targetNodeId || !String(r.targetNodeId).trim()) {
          errors.push(`${ruleLabel}: targetNodeId 不能为空`);
        } else if (!targets.includes(String(r.targetNodeId))) {
          errors.push(
            `${ruleLabel}: targetNodeId "${r.targetNodeId}" 不在该网关出边目标中 [${targets.join(", ") || "无出边"}]`,
          );
        }
        if (r?.outputMappingJson && String(r.outputMappingJson).trim() && !parseOutputMapping(r.outputMappingJson)) {
          errors.push(`${ruleLabel}: outputMapping 不是合法的 JSON 对象`);
        }
      });
      if (targets.length === 0) {
        warnings.push(`网关 ${label} 没有出边（流程无法继续）`);
      } else if (targets.length >= 2) {
        const hasDefaultEdge = def.edges.some(
          (e) => e.source === n.id && !(e.conditionExpression && e.conditionExpression.trim()),
        );
        if (!hasDefaultEdge && rules.length === 0) {
          warnings.push(`网关 ${label} 有多个出边但无规则/边条件，且缺少默认边（建议留一条无条件出边兜底）`);
        }
      }
    }
  }

  for (const n of def.nodes) {
    if (n.type === "END") {
      continue;
    }
    if ((outDegree.get(n.id) || 0) === 0) {
      warnings.push(`节点 "${n.name || n.id}" 没有出边（流程在此中断）`);
    }
  }

  // 可达性检查（从 START BFS）
  const start = starts[0];
  if (start) {
    const adjacency = new Map<string, string[]>();
    for (const e of def.edges) {
      if (!adjacency.has(e.source)) {
        adjacency.set(e.source, []);
      }
      adjacency.get(e.source)!.push(e.target);
    }
    const visited = new Set<string>([start.id]);
    const queue: string[] = [start.id];
    while (queue.length) {
      const cur = queue.shift()!;
      for (const next of adjacency.get(cur) || []) {
        if (!visited.has(next)) {
          visited.add(next);
          queue.push(next);
        }
      }
    }
    for (const n of def.nodes) {
      if (!visited.has(n.id)) {
        warnings.push(`节点 "${n.name || n.id}" 从 START 不可达`);
      }
    }
  }

  return { errors, warnings };
}
