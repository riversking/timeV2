<template>
  <div class="prop-panel">
    <div class="pp-header">
      <span class="pp-title">{{ headerText }}</span>
      <el-tag v-if="isNode && selected" size="small" effect="plain" :style="nodeTagStyle">
        {{ meta.label }}
      </el-tag>
    </div>

    <div v-if="!selected" class="pp-empty">
      点击画布中的节点或连线
      <br />
      在此编辑属性
    </div>

    <!-- ==================== 节点属性 ==================== -->
    <template v-else-if="isNode">
      <el-form label-position="top" size="small" class="pp-form">
        <el-form-item label="节点 ID">
          <el-input
            v-model="idInput"
            placeholder="字母开头，仅字母/数字/下划线"
            @blur="commitId"
            @keyup.enter="commitId"
          />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="selected.element.data.name" placeholder="节点展示名称" />
        </el-form-item>

        <!-- USER_TASK -->
        <template v-if="dslType === 'USER_TASK'">
          <el-divider content-position="left">审批人</el-divider>
          <el-form-item label="表达式 candidateExpr">
            <el-input
              v-model="nodeConfig.candidateExpr"
              type="textarea"
              :rows="2"
              placeholder="如 $startUser,$leader,${deptManager}"
              style="font-family: monospace"
            />
            <div class="pp-tokens">
              <el-button size="small" text type="primary" @click="appendExpr('$startUser')">
                $startUser
              </el-button>
              <el-button size="small" text type="primary" @click="appendExpr('$leader')">
                $leader
              </el-button>
              <el-button size="small" text type="primary" @click="appendExpr('$leaderMax')">
                $leaderMax
              </el-button>
            </div>
            <div class="pp-tip">
              多 token 逗号分隔：$leader 逐级上级链（出现 N 次 = N 级串签）、$leaderMax
              链尾、${变量名} 取流程变量、无 $ 前缀为静态直写。表达式为空时回退下方静态配置。
            </div>
          </el-form-item>
          <el-form-item label="任务模式 taskMode">
            <el-select
              v-model="taskMode"
              clearable
              placeholder="不设置（系统自动判定）"
              style="width: 100%"
            >
              <el-option label="CLAIM · 领单后办理" value="CLAIM" />
              <el-option label="ANY_ONE · 并签，任一人办理" value="ANY_ONE" />
              <el-option label="ALL · 并签，全部办理" value="ALL" />
              <el-option label="SEQUENTIAL · 串签，按顺序办理" value="SEQUENTIAL" />
            </el-select>
            <div class="pp-tip">不设置时：表达式解析出多人自动串签；否则默认 CLAIM 领单。</div>
          </el-form-item>
          <el-divider content-position="left">结果与退回</el-divider>
          <el-form-item label="结果变量 resultVar">
            <el-input
              v-model="nodeConfig.resultVar"
              placeholder="如 deptA_result（并行分支隔离，可选）"
              style="font-family: monospace"
            />
            <div class="pp-tip">
              办理结果（APPROVED/RETURNED 等）写入该流程变量；并行分支各用不同变量名，
              便于汇聚网关读取判断。
            </div>
          </el-form-item>
          <el-form-item label="退回模式 returnMode">
            <el-select
              v-model="returnMode"
              clearable
              placeholder="TERMINATE（缺省：退回即终止）"
              style="width: 100%"
            >
              <el-option label="TERMINATE · 退回即终止流程（缺省）" value="TERMINATE" />
              <el-option label="REWORK · 打回重审，按出边继续流转" value="REWORK" />
            </el-select>
            <div class="pp-tip">
              REWORK：退回不终止实例，由该节点出边继续流转（通常指向"修改重提"节点，
              再回边到本节点重审）。
            </div>
          </el-form-item>
          <el-form-item label="输出映射 outputMapping">
            <el-input
              v-model="nodeConfig.outputMappingJson"
              type="textarea"
              :rows="2"
              placeholder='JSON 对象，如 {"deptA_result": "PENDING"}'
              style="font-family: monospace"
            />
            <div v-if="outputMappingInvalid" class="pp-tip" style="color: #f56c6c">
              JSON 格式不合法，应用时将被忽略
            </div>
            <div v-else class="pp-tip">
              节点完成时合并写入流程变量（如修改节点重提时重置结果为 PENDING，
              避免重审中被他方网关误判为退回态）。
            </div>
          </el-form-item>
          <el-divider content-position="left">静态兜底（表达式为空时生效）</el-divider>
          <el-form-item label="候选用户 candidateUsers">
            <el-select
              v-model="candidateUsers"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="输入 userId 回车添加"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="兜底办理人 assignee">
            <el-input v-model="nodeConfig.assignee" placeholder="单人 userId（可选）" />
          </el-form-item>
        </template>

        <!-- EXCLUSIVE_GATEWAY -->
        <template v-else-if="dslType === 'EXCLUSIVE_GATEWAY'">
          <el-divider content-position="left">内嵌规则（priority 越大越先求值）</el-divider>
          <div class="pp-rules">
            <div v-for="(rule, idx) in rules" :key="idx" class="pp-rule-card">
              <div class="pp-rule-head">
                <span>规则 #{{ idx + 1 }}</span>
                <el-button size="small" text type="danger" @click="rules.splice(idx, 1)">
                  删除
                </el-button>
              </div>
              <el-input
                v-model="rule.condition"
                size="small"
                placeholder="SpEL 条件，如 #amount >= 500"
                style="font-family: monospace; margin-bottom: 6px"
              />
              <el-select
                v-model="rule.targetNodeId"
                size="small"
                placeholder="目标节点（该网关出边目标）"
                style="width: 100%; margin-bottom: 6px"
                :no-data-text="gatewayTargets.length ? '无可选项' : '该网关暂无出边，请先连线'"
              >
                <el-option v-for="t in gatewayTargets" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input-number
                v-model="rule.priority"
                size="small"
                :controls="false"
                placeholder="priority（默认 0）"
                style="width: 100%; margin-bottom: 6px"
              />
              <el-input
                v-model="rule.outputMappingJson"
                size="small"
                placeholder='输出变量 JSON（可选），如 {"level":"BIG"}'
                style="font-family: monospace"
              />
            </div>
            <el-button size="small" type="primary" plain style="width: 100%" @click="addRule">
              + 添加规则
            </el-button>
            <div class="pp-tip">
              规则命中即走目标节点；未命中走边条件（按定义顺序）→ 默认边（首条无条件出边）。
            </div>
          </div>
        </template>

        <!-- PARALLEL_GATEWAY -->
        <div v-else-if="dslType === 'PARALLEL_GATEWAY'" class="pp-note">
          并行网关无需配置：
          <br />
          出边 ≥ 2 自动 Fork 并行分流；
          <br />
          入边 ≥ 2 自动 Join，所有分支到齐后继续。
        </div>

        <!-- START / END -->
        <div v-else class="pp-note">
          {{
            dslType === "START"
              ? "流程入口节点：无入边，仅可配置名称与 ID。"
              : "流程终点节点：无出边，仅可配置名称与 ID。"
          }}
        </div>
      </el-form>
      <div class="pp-actions">
        <el-button type="danger" plain size="small" @click="emit('delete')">
          删除节点（或按 Delete）
        </el-button>
      </div>
    </template>

    <!-- ==================== 连线属性 ==================== -->
    <template v-else>
      <el-form label-position="top" size="small" class="pp-form">
        <el-form-item label="连线">
          <div class="pp-edge-endpoints">
            <el-tag size="small" effect="plain">{{ selected.element.source }}</el-tag>
            <span class="pp-arrow">→</span>
            <el-tag size="small" effect="plain">{{ selected.element.target }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="条件表达式 conditionExpression">
          <el-input
            v-model="selected.element.data.conditionExpression"
            placeholder="留空 = 默认边"
            style="font-family: monospace"
          />
          <div class="pp-tip">
            仅作为排他网关出边的降级条件（内嵌规则未命中时按定义顺序求值）；普通节点出边不使用该条件。
          </div>
        </el-form-item>
      </el-form>
      <div class="pp-actions">
        <el-button type="danger" plain size="small" @click="emit('delete')">
          删除连线（或按 Delete）
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { nodeTypeMeta, parseOutputMapping } from "@/utils/flowDsl";

const props = defineProps<{
  selected: { kind: "node" | "edge"; element: any } | null;
  gatewayTargets: string[];
}>();

const emit = defineEmits<{
  (e: "delete"): void;
  (e: "rename-node", payload: { oldId: string; newId: string }): void;
}>();

const isNode = computed(() => props.selected?.kind === "node");
const dslType = computed(() => props.selected?.element?.data?.dslType || "");
const meta = computed(() => nodeTypeMeta(dslType.value));
const headerText = computed(() => {
  if (!props.selected) {
    return "属性面板";
  }
  return props.selected.kind === "node" ? "节点属性" : "连线属性";
});
const nodeTagStyle = computed(() => ({
  color: meta.value.color,
  borderColor: meta.value.color,
}));

// ==================== 节点 ID 编辑（提交时级联重命名） ====================
const idInput = ref("");
watch(
  () => props.selected,
  (sel) => {
    if (sel?.kind === "node") {
      idInput.value = sel.element.id;
    }
  },
  { immediate: true },
);

const commitId = () => {
  const sel = props.selected;
  if (!sel || sel.kind !== "node") {
    return;
  }
  const newId = idInput.value.trim();
  if (!newId || newId === sel.element.id) {
    idInput.value = sel.element.id;
    return;
  }
  emit("rename-node", { oldId: sel.element.id, newId });
};

// ==================== 节点 config 便捷读写 ====================
const nodeConfig = computed<Record<string, any>>(
  () => props.selected?.element?.data?.config || {},
);

const taskMode = computed({
  get: () => nodeConfig.value.taskMode || "",
  set: (val: string) => {
    if (val) {
      nodeConfig.value.taskMode = val;
    } else {
      delete nodeConfig.value.taskMode;
    }
  },
});

const returnMode = computed({
  get: () => nodeConfig.value.returnMode || "",
  set: (val: string) => {
    if (val) {
      nodeConfig.value.returnMode = val;
    } else {
      delete nodeConfig.value.returnMode;
    }
  },
});

const outputMappingInvalid = computed(() => {
  const raw = nodeConfig.value.outputMappingJson;
  return typeof raw === "string" && raw.trim() !== "" && !parseOutputMapping(raw);
});

const candidateUsers = computed<string[]>({
  get: () =>
    Array.isArray(nodeConfig.value.candidateUsers) ? nodeConfig.value.candidateUsers : [],
  set: (val) => {
    if (val && val.length) {
      nodeConfig.value.candidateUsers = val;
    } else {
      delete nodeConfig.value.candidateUsers;
    }
  },
});

const appendExpr = (token: string) => {
  const cur = typeof nodeConfig.value.candidateExpr === "string" ? nodeConfig.value.candidateExpr : "";
  const parts = cur
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  if (parts.includes(token)) {
    return;
  }
  parts.push(token);
  nodeConfig.value.candidateExpr = parts.join(",");
};

// ==================== 网关规则编辑 ====================
watch(
  () => props.selected?.element,
  (el) => {
    if (
      el?.data?.dslType === "EXCLUSIVE_GATEWAY" &&
      el.data.config &&
      !Array.isArray(el.data.config.rules)
    ) {
      el.data.config.rules = [];
    }
  },
  { immediate: true },
);

const rules = computed<any[]>(() =>
  Array.isArray(nodeConfig.value.rules) ? nodeConfig.value.rules : [],
);

const addRule = () => {
  rules.value.push({ condition: "", targetNodeId: "", priority: 0, outputMappingJson: "" });
};
</script>

<style scoped>
.prop-panel {
  padding: 12px;
  height: 100%;
  box-sizing: border-box;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.pp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;
  margin-bottom: 10px;
}

.pp-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.pp-empty {
  color: #94a3b8;
  font-size: 13px;
  text-align: center;
  padding: 40px 0;
  line-height: 1.8;
}

.pp-form {
  flex: 1;
}

.pp-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.pp-form :deep(.el-divider) {
  margin: 8px 0 12px;
}

.pp-tip {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.5;
  margin-top: 4px;
}

.pp-tokens {
  display: flex;
  flex-wrap: wrap;
  margin-top: 2px;
}

.pp-rules {
  width: 100%;
}

.pp-rule-card {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 8px;
  margin-bottom: 8px;
  background: #f8fafc;
}

.pp-rule-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 6px;
}

.pp-note {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  padding: 12px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.8;
  margin-bottom: 12px;
}

.pp-actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.pp-edge-endpoints {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pp-arrow {
  color: #94a3b8;
}
</style>
