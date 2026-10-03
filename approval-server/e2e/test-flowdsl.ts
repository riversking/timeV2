/**
 * flowDsl 往返测试：DSL → UI 形态 → DSL 导出，验证 resultVar/returnMode/outputMapping
 * 不丢失、UI 专属字段不泄漏、校验规则生效。
 *
 * 运行（在 timer-admin/ 下执行，依赖其 node_modules；产物输出到 /tmp 避免污染仓库）：
 *   npx esbuild ../approval-server/e2e/test-flowdsl.ts --bundle --format=esm \
 *     --platform=node --outfile=/tmp/test-flowdsl.mjs && node /tmp/test-flowdsl.mjs
 */
import { readFileSync } from "node:fs";
import {
  parseDefinition,
  toElements,
  toDefinition,
  validateDefinition,
} from "../../timer-admin/src/utils/flowDsl";

const raw = readFileSync(
  "/Users/riversking/Desktop/timeV2/approval-server/e2e/dual_dept_v1.json",
  "utf-8",
);
const def = parseDefinition(raw);
if (!def) {
  throw new Error("定义解析失败");
}
console.log(`解析: ${def.nodes.length} 节点 / ${def.edges.length} 边`);

// ============ 1) DSL → UI 形态 ============
const { nodes, edges } = toElements(def);
const pick = (id: string) => nodes.find((n: any) => n.id === id)?.data?.config || {};

const deptA = pick("deptA_approval");
const rework = pick("deptA_rework");
console.log("UI deptA.config =", JSON.stringify(deptA));
console.log("UI rework.config =", JSON.stringify(rework));

if (deptA.resultVar !== "deptA_result") {
  throw new Error("UI 形态 resultVar 丢失");
}
if (deptA.returnMode !== "REWORK") {
  throw new Error("UI 形态 returnMode 丢失");
}
if (rework.outputMappingJson !== JSON.stringify({ deptA_result: "PENDING" })) {
  throw new Error(`UI 形态 outputMappingJson 转换异常: ${rework.outputMappingJson}`);
}
if (rework.outputMapping) {
  throw new Error("UI 形态不应残留 outputMapping 对象");
}

// ============ 2) UI → DSL 导出 ============
const back = toDefinition(nodes as any, edges as any, {
  key: def.key,
  name: def.name,
});
const deptA2 = back.nodes.find((n) => n.id === "deptA_approval")?.config || {};
const rework2 = back.nodes.find((n) => n.id === "deptA_rework")?.config || {};
console.log("导出 deptA.config =", JSON.stringify(deptA2));
console.log("导出 rework.config =", JSON.stringify(rework2));

if (deptA2.resultVar !== "deptA_result") {
  throw new Error("导出后 resultVar 丢失");
}
if (deptA2.returnMode !== "REWORK") {
  throw new Error("导出后 returnMode 丢失");
}
if (JSON.stringify(rework2.outputMapping) !== JSON.stringify({ deptA_result: "PENDING" })) {
  throw new Error(`导出后 outputMapping 丢失: ${JSON.stringify(rework2)}`);
}
if (rework2.outputMappingJson) {
  throw new Error("导出后不应残留 outputMappingJson");
}

// ============ 3) 往返等价（关键结构） ============
const sameNodeCount = back.nodes.length === def.nodes.length;
const sameEdgeCount = back.edges.length === def.edges.length;
if (!sameNodeCount || !sameEdgeCount) {
  throw new Error(`往返结构变化: ${back.nodes.length}/${back.edges.length}`);
}
const origA = def.nodes.find((n) => n.id === "deptA_approval")?.config || {};
const origRework = def.nodes.find((n) => n.id === "deptA_rework")?.config || {};
if (JSON.stringify(origA) !== JSON.stringify(deptA2)) {
  console.warn("注意: deptA config 往返有差异（可接受的话继续）", origA, deptA2);
}
if (JSON.stringify(origRework.outputMapping) !== JSON.stringify(rework2.outputMapping)) {
  throw new Error("rework outputMapping 往返不等价");
}

// ============ 4) 导出形态校验 ============
const { errors, warnings } = validateDefinition(back);
console.log("校验 errors =", JSON.stringify(errors));
console.log("校验 warnings =", JSON.stringify(warnings));
if (errors.length) {
  throw new Error(`导出定义校验出错: ${errors.join("; ")}`);
}

// ============ 5) 非法 JSON / 非法 returnMode 检出 ============
const badUi = {
  key: "",
  name: "",
  nodes: [
    { id: "start", type: "START", name: "开始" },
    {
      id: "t1",
      type: "USER_TASK",
      name: "t1",
      config: { outputMappingJson: "{bad", returnMode: "WHATEVER", resultVar: "1bad" },
    },
    { id: "end", type: "END", name: "结束" },
  ],
  edges: [
    { id: "e1", source: "start", target: "t1" },
    { id: "e2", source: "t1", target: "end" },
  ],
};
const v2 = validateDefinition(badUi as any);
console.log("非法配置检出 =", JSON.stringify(v2.errors));
if (!v2.errors.some((e) => e.includes("outputMapping"))) {
  throw new Error("非法 outputMapping JSON 未被检出");
}
if (!v2.errors.some((e) => e.includes("returnMode"))) {
  throw new Error("非法 returnMode 未被检出");
}
if (!v2.errors.some((e) => e.includes("resultVar"))) {
  throw new Error("非法 resultVar 未被检出");
}

console.log("\n✓ flowDsl 往返与校验全部通过");
