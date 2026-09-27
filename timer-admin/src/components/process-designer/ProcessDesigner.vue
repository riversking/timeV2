<template>
  <div class="process-designer">
    <!-- ==================== 工具条 ==================== -->
    <div class="pd-toolbar">
      <div class="pd-toolbar-group">
        <el-button size="small" @click="handleAutoLayout">
          <el-icon><MagicStick /></el-icon> 自动布局
        </el-button>
        <el-button size="small" type="primary" plain @click="handleLocalValidate">
          结构校验
        </el-button>
        <el-button size="small" type="success" plain @click="handleRuleEngineValidate">
          规则引擎校验
        </el-button>
      </div>
      <div class="pd-toolbar-group">
        <el-button size="small" @click="openImport">导入 JSON</el-button>
        <el-button size="small" @click="showPreview">预览 JSON</el-button>
        <el-button size="small" type="danger" plain @click="handleClear">清空画布</el-button>
      </div>
    </div>

    <!-- ==================== 主体三栏 ==================== -->
    <div class="pd-body">
      <!-- 左：节点面板 -->
      <div class="pd-palette">
        <div class="pd-palette-title">节点（拖入画布）</div>
        <div
          v-for="t in NODE_TYPES"
          :key="t.type"
          class="palette-item"
          draggable="true"
          @dragstart="onDragStart($event, t.type)"
        >
          <span class="pi-icon" :style="{ background: t.color }">{{ t.glyph }}</span>
          <div class="pi-text">
            <div class="pi-label">{{ t.label }}</div>
            <div class="pi-desc">{{ t.desc }}</div>
          </div>
        </div>
      </div>

      <!-- 中：画布 -->
      <div class="pd-canvas">
        <FlowCanvas
          ref="canvasRef"
          :definition="initialDefinition"
          @select="handleSelect"
          @elements-change="handleElementsChange"
        />
      </div>

      <!-- 右：属性面板 -->
      <div class="pd-props">
        <PropertyPanel
          :selected="selection"
          :gateway-targets="gatewayTargets"
          @delete="handleDeleteSelected"
          @rename-node="handleRenameNode"
        />
      </div>
    </div>

    <!-- ==================== 底部 ==================== -->
    <div class="pd-footer">
      <el-button @click="emit('cancel')">取消</el-button>
      <el-button type="primary" @click="handleApply">应用到 definitionJson</el-button>
    </div>

    <!-- ==================== JSON 预览 ==================== -->
    <el-dialog v-model="previewVisible" title="流程定义 JSON 预览" width="760px" append-to-body>
      <div class="pd-preview-toolbar">
        <span class="pd-preview-tip">与后端 ProcessDefinition{nodes, edges} 结构一致</span>
        <el-button size="small" type="primary" plain @click="copyPreview">复制</el-button>
      </div>
      <pre class="pd-preview">{{ previewContent }}</pre>
    </el-dialog>

    <!-- ==================== JSON 导入 ==================== -->
    <el-dialog v-model="importVisible" title="导入 definitionJson" width="760px" append-to-body>
      <el-input
        v-model="importText"
        type="textarea"
        :rows="18"
        placeholder='粘贴流程定义 JSON，结构 {"key","name","nodes":[...],"edges":[...]}'
        style="font-family: monospace"
      />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" @click="handleImport">导入到画布</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 结构校验结果 ==================== -->
    <el-dialog v-model="validateVisible" title="结构校验结果" width="640px" append-to-body>
      <el-alert
        v-if="!validateErrors.length"
        title="结构校验通过：节点/连线/网关规则均合法"
        type="success"
        :closable="false"
        show-icon
      />
      <template v-else>
        <el-alert
          :title="`发现 ${validateErrors.length} 个结构错误`"
          type="error"
          :closable="false"
          show-icon
          style="margin-bottom: 10px"
        />
        <ul class="pd-msg-list">
          <li v-for="(e, i) in validateErrors" :key="i">{{ e }}</li>
        </ul>
      </template>
      <template v-if="validateWarnings.length">
        <el-alert
          :title="`${validateWarnings.length} 个提示（不影响保存）`"
          type="warning"
          :closable="false"
          show-icon
          style="margin: 12px 0 10px"
        />
        <ul class="pd-msg-list warn">
          <li v-for="(w, i) in validateWarnings" :key="i">{{ w }}</li>
        </ul>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { MagicStick } from "@element-plus/icons-vue";
import FlowCanvas from "./FlowCanvas.vue";
import PropertyPanel from "./PropertyPanel.vue";
import {
  NODE_TYPES,
  createDefaultDefinition,
  parseDefinition,
  toDefinition,
  validateDefinition,
  type DslDefinition,
} from "@/utils/flowDsl";
import { validateDefinitionRule } from "@/api/ruleengine";

const props = defineProps<{
  /** 初始 definitionJson 字符串（空则给出默认骨架） */
  modelValue: string;
  defaultKey?: string;
  defaultName?: string;
}>();

const emit = defineEmits<{
  (e: "apply", json: string): void;
  (e: "cancel"): void;
}>();

const canvasRef = ref<any>(null);

const onDragStart = (evt: DragEvent, type: string) => {
  evt.dataTransfer?.setData("application/flow-node", type);
  if (evt.dataTransfer) {
    evt.dataTransfer.effectAllowed = "move";
  }
};

// 初始定义：优先解析传入 JSON，否则默认骨架
const initialDefinition = ref<DslDefinition>(
  (() => {
    const parsed = parseDefinition(props.modelValue);
    if (parsed && parsed.nodes.length) {
      return parsed;
    }
    return createDefaultDefinition(props.defaultKey || "", props.defaultName || "");
  })(),
);

// ==================== 选中与网关目标 ====================

const selection = ref<{ kind: "node" | "edge"; element: any } | null>(null);
const gatewayTargets = ref<string[]>([]);

const refreshGatewayTargets = () => {
  const sel = selection.value;
  if (!sel || sel.kind !== "node" || sel.element?.data?.dslType !== "EXCLUSIVE_GATEWAY") {
    gatewayTargets.value = [];
    return;
  }
  const { edges } = canvasRef.value?.getElements() || { edges: [] };
  gatewayTargets.value = edges
    .filter((e: any) => e.source === sel.element.id)
    .map((e: any) => e.target);
};

// 键盘删除等场景：选中元素已不存在则清空选中
const validateSelection = () => {
  const sel = selection.value;
  if (!sel) {
    return;
  }
  const { nodes, edges } = canvasRef.value?.getElements() || { nodes: [], edges: [] };
  const list = sel.kind === "node" ? nodes : edges;
  if (!list.some((el: any) => el.id === sel.element.id)) {
    selection.value = null;
    gatewayTargets.value = [];
  }
};

const handleSelect = (payload: { kind: "node" | "edge"; element: any } | null) => {
  selection.value = payload;
  refreshGatewayTargets();
};

const handleElementsChange = () => {
  validateSelection();
  refreshGatewayTargets();
};

// ==================== 删除 / 重命名 ====================

const handleDeleteSelected = () => {
  const sel = selection.value;
  if (!sel) {
    return;
  }
  if (sel.kind === "node") {
    canvasRef.value?.removeNode(sel.element.id);
  } else {
    canvasRef.value?.removeEdge(sel.element.id);
  }
  selection.value = null;
  gatewayTargets.value = [];
};

const handleRenameNode = ({ oldId, newId }: { oldId: string; newId: string }) => {
  const result = canvasRef.value?.renameNode(oldId, newId);
  if (!result || result.err) {
    ElMessage.error(result?.err || "重命名失败");
    // 替换选中包装引用，触发属性面板重置输入框
    if (selection.value) {
      selection.value = { kind: "node", element: selection.value.element };
    }
    return;
  }
  if (result.newNode) {
    selection.value = { kind: "node", element: result.newNode };
  }
  ElMessage.success(`节点 id 已改为 ${newId}`);
  refreshGatewayTargets();
};

// ==================== 布局 / 导入 / 预览 ====================

const handleAutoLayout = () => {
  canvasRef.value?.applyLayout();
  ElMessage.success("已按拓扑重新布局");
};

const openImport = () => {
  importText.value = "";
  importVisible.value = true;
};
const importVisible = ref(false);
const importText = ref("");

const handleImport = () => {
  const parsed = parseDefinition(importText.value);
  if (!parsed || !parsed.nodes.length) {
    ElMessage.error("JSON 无法解析或缺少 nodes 数组");
    return;
  }
  initialDefinition.value = parsed;
  canvasRef.value?.replaceAll(parsed);
  selection.value = null;
  gatewayTargets.value = [];
  importVisible.value = false;
  ElMessage.success(`已导入 ${parsed.nodes.length} 个节点 / ${parsed.edges.length} 条连线`);
};

const handleClear = () => {
  ElMessageBox.confirm("确定清空画布并重置为默认骨架（开始 → 结束）吗？", "提示", {
    confirmButtonText: "清空",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      const fresh = createDefaultDefinition(props.defaultKey || "", props.defaultName || "");
      initialDefinition.value = fresh;
      canvasRef.value?.replaceAll(fresh);
      selection.value = null;
      gatewayTargets.value = [];
      ElMessage.success("已重置为默认骨架");
    })
    .catch(() => {});
};

// ==================== 快照导出 ====================

const getDefinition = (): DslDefinition => {
  const { nodes, edges } = canvasRef.value?.getElements() || { nodes: [], edges: [] };
  const base = initialDefinition.value;
  return toDefinition(nodes, edges, {
    key: base.key || props.defaultKey || "",
    name: base.name || props.defaultName || "",
  });
};

const previewVisible = ref(false);
const previewContent = ref("");

const showPreview = () => {
  previewContent.value = JSON.stringify(getDefinition(), null, 2);
  previewVisible.value = true;
};

const copyPreview = async () => {
  try {
    await navigator.clipboard.writeText(previewContent.value);
    ElMessage.success("已复制到剪贴板");
  } catch {
    ElMessage.error("复制失败");
  }
};

// ==================== 校验 ====================

const validateVisible = ref(false);
const validateErrors = ref<string[]>([]);
const validateWarnings = ref<string[]>([]);

const handleLocalValidate = () => {
  const { errors, warnings } = validateDefinition(getDefinition());
  validateErrors.value = errors;
  validateWarnings.value = warnings;
  if (!errors.length && !warnings.length) {
    ElMessage.success("结构校验通过");
    return;
  }
  validateVisible.value = true;
};

const handleRuleEngineValidate = async () => {
  try {
    const res = await validateDefinitionRule({
      definitionJson: JSON.stringify(getDefinition()),
    });
    if (res.code !== 200) {
      ElMessage.error(res.message || "校验接口调用失败");
      return;
    }
    const errors = res.data?.errors || [];
    if (!errors.length) {
      ElMessage.success("规则引擎校验通过：JSON 可解析，网关规则全部合法");
      return;
    }
    const first = errors[0];
    ElMessageBox.alert(
      `节点[${first.nodeId || "JSON整体"}] 规则#${first.ruleIndex} · ${first.message}`,
      "规则引擎校验失败",
      { confirmButtonText: "知道了", type: "error" },
    );
  } catch {
    ElMessage.error("校验失败（规则引擎服务不可用？）");
  }
};

// ==================== 应用 ====================

const handleApply = () => {
  const def = getDefinition();
  const { errors } = validateDefinition(def);
  if (errors.length) {
    ElMessageBox.confirm(
      `当前画布存在 ${errors.length} 个结构错误（可点"结构校验"查看详情），仍要应用到 definitionJson 吗？`,
      "结构校验未通过",
      { confirmButtonText: "仍要应用", cancelButtonText: "返回修改", type: "warning" },
    )
      .then(() => {
        emit("apply", JSON.stringify(def, null, 2));
      })
      .catch(() => {});
    return;
  }
  emit("apply", JSON.stringify(def, null, 2));
};
</script>

<style scoped>
.process-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 112px);
  gap: 10px;
}

.pd-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pd-toolbar-group {
  display: flex;
  gap: 8px;
  align-items: center;
}

.pd-body {
  flex: 1;
  display: flex;
  gap: 10px;
  min-height: 0;
}

.pd-palette {
  width: 210px;
  flex-shrink: 0;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px;
  overflow-y: auto;
}

.pd-palette-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 10px;
}

.palette-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: grab;
  background: #ffffff;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.palette-item:hover {
  border-color: #3b82f6;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.15);
}

.palette-item:active {
  cursor: grabbing;
}

.pi-icon {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
}

.pi-label {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.pi-desc {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.35;
}

.pd-canvas {
  flex: 1;
  min-width: 0;
}

.pd-props {
  width: 320px;
  flex-shrink: 0;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow-y: auto;
}

.pd-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.pd-msg-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: #dc2626;
  line-height: 1.9;
}

.pd-msg-list.warn {
  color: #d97706;
}

.pd-preview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.pd-preview-tip {
  font-size: 12px;
  color: #94a3b8;
}

.pd-preview {
  max-height: 60vh;
  overflow: auto;
  background: #0f172a;
  color: #a5f3fc;
  padding: 14px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
}
</style>
