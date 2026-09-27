<template>
  <div class="flow-canvas-wrap" @dragover.prevent @drop="handleDrop">
    <VueFlow
      :id="flowId"
      :nodes="initialElements.nodes"
      :edges="initialElements.edges"
      :node-types="nodeTypes"
      :edge-types="edgeTypes"
      delete-key-code="Delete"
      :min-zoom="0.25"
      :max-zoom="2"
      :default-viewport="{ zoom: 0.9 }"
      @connect="handleConnect"
      @node-click="handleNodeClick"
      @edge-click="handleEdgeClick"
      @pane-click="handlePaneClick"
      @nodes-change="handleNodesChange"
      @edges-change="handleEdgesChange"
      class="vf-instance"
    >
      <Background :gap="18" :pattern-color="'#d5dbe4'" />
      <Controls position="bottom-left" />
    </VueFlow>
    <div class="canvas-hint">
      从左侧拖入节点 · 拖动节点右缘圆点连线 · 点击元素后在右侧编辑属性 · 选中后按 Delete 删除
    </div>
  </div>
</template>

<script setup lang="ts">
import { markRaw, onMounted } from "vue";
import { VueFlow, useVueFlow, MarkerType } from "@vue-flow/core";
import type { Connection } from "@vue-flow/core";
import { Background } from "@vue-flow/background";
import { Controls } from "@vue-flow/controls";
import { ElMessage } from "element-plus";
import ProcessNode from "./ProcessNode.vue";
import ConditionEdge from "./ConditionEdge.vue";
import {
  toElements,
  genNodeId,
  genEdgeId,
  nodeTypeMeta,
  autoLayoutElements,
  type DslDefinition,
} from "@/utils/flowDsl";

import "@vue-flow/core/dist/style.css";
import "@vue-flow/core/dist/theme-default.css";
import "@vue-flow/controls/dist/style.css";

let flowSeq = 0;

const props = defineProps<{ definition: DslDefinition }>();

const emit = defineEmits<{
  (e: "select", payload: { kind: "node" | "edge"; element: any } | null): void;
  (e: "elements-change"): void;
}>();

const flowId = `flow-${++flowSeq}`;
const initialElements = toElements(props.definition);

const nodeTypes = { process: markRaw(ProcessNode) };
const edgeTypes = { condition: markRaw(ConditionEdge) };

const store = useVueFlow(flowId);
const { addNodes, addEdges, setNodes, setEdges } = store;

/**
 * store.nodes / store.edges 在类型上是 Ref、运行时可能已被响应式解包，统一取值。
 */
const unwrap = (x: any): any[] =>
  (x && typeof x === "object" && "value" in x ? x.value : x) || [];

const allNodes = (): any[] => unwrap(store.nodes);
const allEdges = (): any[] => unwrap(store.edges);

onMounted(() => {
  // 等节点完成尺寸测量后适配视图
  setTimeout(() => store.fitView({ padding: 0.2 }), 150);
});

// ==================== 拖入新建节点 ====================

const handleDrop = (evt: DragEvent) => {
  const type = evt.dataTransfer?.getData("application/flow-node");
  if (!type) {
    return;
  }
  const position = store.screenToFlowCoordinate({ x: evt.clientX, y: evt.clientY });
  const id = genNodeId(type, new Set(allNodes().map((n) => n.id)));
  const meta = nodeTypeMeta(type);
  addNodes([
    {
      id,
      type: "process",
      position: { x: position.x - 90, y: position.y - 35 },
      data: { dslType: type, name: meta.label, config: {} },
    },
  ]);
};

// ==================== 连线（含合法性校验） ====================

const handleConnect = (conn: Connection) => {
  if (!conn.source || !conn.target) {
    return;
  }
  if (conn.source === conn.target) {
    ElMessage.warning("不允许节点连接到自己");
    return;
  }
  const nodes = allNodes();
  const source = nodes.find((n) => n.id === conn.source);
  const target = nodes.find((n) => n.id === conn.target);
  if (!source || !target) {
    return;
  }
  if (source.data?.dslType === "END") {
    ElMessage.warning("END 节点不能有出边");
    return;
  }
  if (target.data?.dslType === "START") {
    ElMessage.warning("START 节点不能有入边");
    return;
  }
  if (allEdges().some((e) => e.source === conn.source && e.target === conn.target)) {
    ElMessage.warning("两节点之间已存在连线");
    return;
  }
  addEdges([
    {
      id: genEdgeId(new Set(allEdges().map((e) => e.id))),
      source: conn.source,
      target: conn.target,
      type: "condition",
      markerEnd: MarkerType.ArrowClosed,
      data: { conditionExpression: "" },
    },
  ]);
  emit("elements-change");
};

// ==================== 选中 ====================

const handleNodeClick = ({ node }: any) => {
  emit("select", { kind: "node", element: node });
};

const handleEdgeClick = ({ edge }: any) => {
  emit("select", { kind: "edge", element: edge });
};

const handlePaneClick = () => {
  emit("select", null);
};

// 键盘删除（Remove 变更）后通知外部校验选中有效性
const handleNodesChange = (changes: any[]) => {
  if (changes.some((c) => c.type === "remove")) {
    emit("elements-change");
  }
};

const handleEdgesChange = (changes: any[]) => {
  if (changes.some((c) => c.type === "remove")) {
    emit("elements-change");
  }
};

// ==================== 对外操作方法（ProcessDesigner 调用） ====================

const getElements = () => ({ nodes: allNodes(), edges: allEdges() });

const applyLayout = () => {
  autoLayoutElements(allNodes(), allEdges());
  setTimeout(() => store.fitView({ padding: 0.2 }), 120);
};

const removeNode = (id: string) => {
  store.removeNodes([id], true);
  emit("elements-change");
};

const removeEdge = (id: string) => {
  store.removeEdges([id]);
  emit("elements-change");
};

/**
 * 重命名节点 id 并级联更新连线与网关规则引用。
 * 返回 { err, newNode }：err 非空表示失败（id 非法/重复）。
 */
const renameNode = (oldId: string, newId: string): { err: string | null; newNode: any } => {
  if (!newId) {
    return { err: "节点 id 不能为空", newNode: null };
  }
  if (oldId === newId) {
    return { err: null, newNode: allNodes().find((n) => n.id === oldId) || null };
  }
  if (!/^[A-Za-z][A-Za-z0-9_]*$/.test(newId)) {
    return { err: "节点 id 需字母开头，仅含字母/数字/下划线", newNode: null };
  }
  const nodes = allNodes();
  if (nodes.some((n) => n.id === newId)) {
    return { err: `节点 id "${newId}" 已存在`, newNode: null };
  }
  if (!nodes.some((n) => n.id === oldId)) {
    return { err: "节点不存在", newNode: null };
  }
  let renamed: any = null;
  setNodes(
    nodes.map((n) => {
      if (n.id === oldId) {
        renamed = { ...n, id: newId };
        return renamed;
      }
      const rules = n.data?.config?.rules;
      if (Array.isArray(rules) && rules.some((r: any) => r.targetNodeId === oldId)) {
        const nextRules = rules.map((r: any) =>
          r.targetNodeId === oldId ? { ...r, targetNodeId: newId } : r,
        );
        return { ...n, data: { ...n.data, config: { ...n.data.config, rules: nextRules } } };
      }
      return n;
    }),
  );
  setEdges(
    allEdges().map((e) => {
      if (e.source === oldId) {
        return { ...e, source: newId };
      }
      if (e.target === oldId) {
        return { ...e, target: newId };
      }
      return e;
    }),
  );
  return { err: null, newNode: renamed };
};

/**
 * 整体替换画布内容（导入 JSON / 清空重置）。
 */
const replaceAll = (def: DslDefinition) => {
  const elements = toElements(def);
  setNodes(elements.nodes);
  setEdges(elements.edges);
  emit("select", null);
  setTimeout(() => store.fitView({ padding: 0.2 }), 120);
};

defineExpose({ getElements, applyLayout, removeNode, removeEdge, renameNode, replaceAll });
</script>

<style scoped>
.flow-canvas-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fbfdff;
}

.vf-instance {
  width: 100%;
  height: 100%;
}

.canvas-hint {
  position: absolute;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 4px 14px;
  font-size: 12px;
  color: #64748b;
  pointer-events: none;
  white-space: nowrap;
  z-index: 5;
}
</style>
