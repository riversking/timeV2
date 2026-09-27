<template>
  <div class="rule-playground-container">
    <!-- 头部 -->
    <div class="header">
      <h1 class="title">规则调试台</h1>
      <div class="header-sub">
        rule-engine-server · /rule 三大能力在线验证（无状态纯计算）
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <!-- ==================== Tab 1：定义校验 ==================== -->
      <el-tab-pane label="定义规则校验" name="validate">
        <div class="panel">
          <div class="panel-toolbar">
            <span class="panel-label">definitionJson</span>
            <div class="toolbar-btns">
              <el-button size="small" @click="fillValidateSample">填入示例</el-button>
              <el-button size="small" @click="validateForm.definitionJson = ''">清空</el-button>
              <el-button
                size="small"
                type="primary"
                :loading="validateLoading"
                @click="handleValidate"
              >
                校验
              </el-button>
            </div>
          </div>
          <el-input
            v-model="validateForm.definitionJson"
            type="textarea"
            :rows="16"
            placeholder="流程定义 definitionJson 原文"
            style="font-family: monospace"
          />

          <!-- 结果 -->
          <div class="result-area" v-if="validateDone">
            <el-alert
              v-if="validateErrors.length === 0"
              title="校验通过：JSON 可解析，排他网关内嵌规则全部合法"
              type="success"
              :closable="false"
              show-icon
            />
            <template v-else>
              <el-alert
                :title="`校验失败：发现 ${validateErrors.length} 个错误（首个错误即返回）`"
                type="error"
                :closable="false"
                show-icon
                style="margin-bottom: 10px"
              />
              <el-table :data="validateErrors" size="small" border>
                <el-table-column prop="nodeId" label="节点ID" width="160">
                  <template #default="{ row }">{{ row.nodeId || "(JSON整体)" }}</template>
                </el-table-column>
                <el-table-column prop="ruleIndex" label="规则下标" width="100">
                  <template #default="{ row }">{{ row.ruleIndex }}</template>
                </el-table-column>
                <el-table-column prop="message" label="错误信息" min-width="320" />
              </el-table>
            </template>
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== Tab 2：审批人表达式解析 ==================== -->
      <el-tab-pane label="审批人表达式解析" name="assignee">
        <div class="panel">
          <el-form label-width="120px">
            <el-form-item label="表达式 expr">
              <el-input
                v-model="assigneeForm.expr"
                placeholder="多 token 逗号分隔，如：$leader,${deptManager},userF"
                style="font-family: monospace"
              />
            </el-form-item>
            <el-form-item label="发起人 initiator">
              <el-input
                v-model="assigneeForm.initiator"
                placeholder="发起人 userId（$startUser 数据源）"
              />
            </el-form-item>
            <el-form-item label="流程变量">
              <el-input
                v-model="assigneeForm.variablesJson"
                type="textarea"
                :rows="6"
                placeholder='JSON 对象，如 {"leaderChain": ["m1","m2"], "deptManager": "userB"}'
                style="font-family: monospace"
              />
            </el-form-item>
            <el-form-item>
              <el-button size="small" @click="fillAssigneeSample">填入示例</el-button>
              <el-button
                size="small"
                type="primary"
                :loading="assigneeLoading"
                @click="handleResolve"
              >
                解析
              </el-button>
            </el-form-item>
          </el-form>

          <div class="result-area" v-if="assigneeDone">
            <el-alert
              v-if="assignees.length === 0"
              title="未解析出处理人（表达式缺失或无 token，调用方应回退静态配置）"
              type="warning"
              :closable="false"
              show-icon
            />
            <div v-else class="assignee-result">
              <span class="result-label">解析结果（保序去重）：</span>
              <el-tag
                v-for="(a, i) in assignees"
                :key="i"
                type="primary"
                effect="plain"
                style="margin-right: 8px"
              >
                {{ a }}
              </el-tag>
            </div>
          </div>

          <div class="tip-box">
            <div class="tip-title">token 语法</div>
            <div class="tip-item">$leader / ${leader} — 上级链逐级（第 k 个 token 取第 k 级）</div>
            <div class="tip-item">$leaderMax — 上级链最高级（链尾）</div>
            <div class="tip-item">$startUser — 发起人本人</div>
            <div class="tip-item">${name} — 流程变量通道；无匹配时兜底查用户，不存在则保留字面原文</div>
            <div class="tip-item">无 $ 前缀（userF / userF,userG）— 静态直写处理人，仅当整个表达式不含 $token 时生效</div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== Tab 3：排他网关路由 ==================== -->
      <el-tab-pane label="排他网关路由" name="gateway">
        <div class="panel">
          <el-form label-width="120px">
            <el-form-item label="网关节点 id">
              <el-input v-model="gatewayForm.nodeId" placeholder="网关节点 id（日志用）" />
            </el-form-item>
          </el-form>

          <!-- 内嵌规则 -->
          <div class="sub-panel">
            <div class="sub-title">
              内嵌规则（config.rules，priority 越大越先求值）
              <el-button size="small" text type="primary" @click="addRule">
                + 添加规则
              </el-button>
            </div>
            <el-table :data="gatewayForm.rules" size="small" border>
              <el-table-column label="condition（SpEL，变量用 #name）" min-width="240">
                <template #default="{ row }">
                  <el-input
                    v-model="row.condition"
                    size="small"
                    placeholder="#amount >= 500"
                    style="font-family: monospace"
                  />
                </template>
              </el-table-column>
              <el-table-column label="targetNodeId" width="150">
                <template #default="{ row }">
                  <el-input v-model="row.targetNodeId" size="small" placeholder="big" />
                </template>
              </el-table-column>
              <el-table-column label="priority" width="100">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.priority"
                    size="small"
                    :controls="false"
                    style="width: 80px"
                  />
                </template>
              </el-table-column>
              <el-table-column label="outputMappingJson" min-width="180">
                <template #default="{ row }">
                  <el-input
                    v-model="row.outputMappingJson"
                    size="small"
                    placeholder='{"level":"BIG"}'
                    style="font-family: monospace"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ $index }">
                  <el-button size="small" text type="danger" @click="removeRule($index)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 出边 -->
          <div class="sub-panel">
            <div class="sub-title">
              网关出边（按定义顺序，edge 条件为第二级降级）
              <el-button size="small" text type="primary" @click="addEdge">
                + 添加出边
              </el-button>
            </div>
            <el-table :data="gatewayForm.edges" size="small" border>
              <el-table-column label="target" width="160">
                <template #default="{ row }">
                  <el-input v-model="row.target" size="small" placeholder="目标节点 id" />
                </template>
              </el-table-column>
              <el-table-column label="conditionExpression（空 = 默认边）" min-width="280">
                <template #default="{ row }">
                  <el-input
                    v-model="row.conditionExpression"
                    size="small"
                    placeholder="#amount > 0（留空表示默认边）"
                    style="font-family: monospace"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ $index }">
                  <el-button size="small" text type="danger" @click="removeEdge($index)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 变量 -->
          <div class="sub-panel">
            <div class="sub-title">流程变量 variablesJson</div>
            <el-input
              v-model="gatewayForm.variablesJson"
              type="textarea"
              :rows="5"
              placeholder='{"amount": 600}'
              style="font-family: monospace"
            />
          </div>

          <div class="panel-toolbar">
            <el-button size="small" @click="fillGatewaySample">填入示例</el-button>
            <el-button
              size="small"
              type="primary"
              :loading="gatewayLoading"
              @click="handleRoute"
            >
              执行路由
            </el-button>
          </div>

          <!-- 路由结果 -->
          <div class="result-area" v-if="routeResult">
            <el-alert
              v-if="routeResult.failed"
              :title="`路由失败：${routeResult.message}`"
              type="error"
              :closable="false"
              show-icon
            />
            <div v-else class="route-result">
              <div class="route-line">
                <span class="result-label">目标节点：</span>
                <span class="route-target">{{ routeResult.targetNodeId }}</span>
                <el-tag :type="hitSourceType(routeResult.hitSource)" effect="dark" size="small">
                  命中来源：{{ hitSourceText(routeResult.hitSource) }}
                </el-tag>
              </div>
              <div v-if="routeResult.outputVariablesJson" class="route-line">
                <span class="result-label">输出变量：</span>
                <pre class="route-json">{{ prettyJson(routeResult.outputVariablesJson) }}</pre>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import {
  validateDefinitionRule,
  resolveAssignees,
  routeGateway,
} from "@/api/ruleengine";

const activeTab = ref("validate");

// ==================== Tab 1：定义校验 ====================
const validateForm = reactive({ definitionJson: "" });
const validateLoading = ref(false);
const validateDone = ref(false);
const validateErrors = ref<any[]>([]);

const fillValidateSample = () => {
  validateForm.definitionJson = JSON.stringify(
    {
      key: "leave_gw_demo",
      name: "请假流程-网关规则演示",
      nodes: [
        { id: "start", type: "START", name: "开始" },
        {
          id: "gw",
          type: "EXCLUSIVE_GATEWAY",
          name: "金额路由",
          config: {
            rules: [
              {
                condition: "#amount >= 500",
                targetNodeId: "big",
                priority: 10,
                outputMapping: { level: "BIG" },
              },
              { condition: "#amount > 0", targetNodeId: "small", priority: 1 },
            ],
          },
        },
        {
          id: "big",
          type: "USER_TASK",
          name: "大额审批",
          config: { candidateUsers: ["userD"], taskMode: "ANY_ONE" },
        },
        {
          id: "small",
          type: "USER_TASK",
          name: "小额审批",
          config: { candidateUsers: ["userE"], taskMode: "ANY_ONE" },
        },
        { id: "end", type: "END", name: "结束" },
      ],
      edges: [
        { id: "e1", source: "start", target: "gw" },
        { id: "e2", source: "gw", target: "big" },
        { id: "e3", source: "gw", target: "small", conditionExpression: "#amount > 0" },
        { id: "e4", source: "big", target: "end" },
        { id: "e5", source: "small", target: "end" },
      ],
    },
    null,
    2,
  );
};

const handleValidate = async () => {
  if (!validateForm.definitionJson) {
    ElMessage.warning("请填写 definitionJson");
    return;
  }
  validateLoading.value = true;
  validateDone.value = false;
  try {
    const res = await validateDefinitionRule({
      definitionJson: validateForm.definitionJson,
    });
    if (res.code !== 200) {
      ElMessage.error(res.message || "校验接口调用失败");
      return;
    }
    validateErrors.value = res.data?.errors || [];
    validateDone.value = true;
  } catch (error) {
    ElMessage.error("校验失败（规则引擎服务不可用？）");
  } finally {
    validateLoading.value = false;
  }
};

// ==================== Tab 2：表达式解析 ====================
const assigneeForm = reactive({
  expr: "",
  initiator: "",
  variablesJson: "",
});
const assigneeLoading = ref(false);
const assigneeDone = ref(false);
const assignees = ref<string[]>([]);

const fillAssigneeSample = () => {
  assigneeForm.expr = "$startUser,$leader,${deptManager}";
  assigneeForm.initiator = "userA";
  assigneeForm.variablesJson = JSON.stringify(
    { leaderChain: ["m1", "m2"], deptManager: "userB" },
    null,
    2,
  );
};

const handleResolve = async () => {
  if (!assigneeForm.expr) {
    ElMessage.warning("请填写表达式 expr");
    return;
  }
  assigneeLoading.value = true;
  assigneeDone.value = false;
  try {
    const res = await resolveAssignees({
      expr: assigneeForm.expr,
      initiator: assigneeForm.initiator,
      variablesJson: assigneeForm.variablesJson,
    });
    if (res.code !== 200) {
      ElMessage.error(res.message || "解析失败");
      return;
    }
    assignees.value = res.data?.assignees || [];
    assigneeDone.value = true;
  } catch (error) {
    ElMessage.error("解析失败（规则引擎服务不可用？）");
  } finally {
    assigneeLoading.value = false;
  }
};

// ==================== Tab 3：网关路由 ====================
const gatewayForm = reactive<{
  nodeId: string;
  rules: Array<{
    condition: string;
    targetNodeId: string;
    priority: number | undefined;
    outputMappingJson: string;
  }>;
  edges: Array<{ target: string; conditionExpression: string }>;
  variablesJson: string;
}>({
  nodeId: "gw",
  rules: [],
  edges: [],
  variablesJson: "",
});

const gatewayLoading = ref(false);
const routeResult = ref<any>(null);

const addRule = () => {
  gatewayForm.rules.push({
    condition: "",
    targetNodeId: "",
    priority: 0,
    outputMappingJson: "",
  });
};

const removeRule = (index: number) => {
  gatewayForm.rules.splice(index, 1);
};

const addEdge = () => {
  gatewayForm.edges.push({ target: "", conditionExpression: "" });
};

const removeEdge = (index: number) => {
  gatewayForm.edges.splice(index, 1);
};

const fillGatewaySample = () => {
  gatewayForm.nodeId = "gw";
  gatewayForm.rules = [
    {
      condition: "#amount >= 500",
      targetNodeId: "big",
      priority: 10,
      outputMappingJson: '{"level":"BIG"}',
    },
    {
      condition: "#amount > 0",
      targetNodeId: "small",
      priority: 1,
      outputMappingJson: "",
    },
  ];
  gatewayForm.edges = [
    { target: "big", conditionExpression: "#amount >= 500" },
    { target: "small", conditionExpression: "#amount > 0" },
    { target: "small", conditionExpression: "" },
  ];
  gatewayForm.variablesJson = JSON.stringify({ amount: 600 }, null, 2);
};

const handleRoute = async () => {
  if (!gatewayForm.nodeId) {
    ElMessage.warning("请填写网关节点 id");
    return;
  }
  gatewayLoading.value = true;
  routeResult.value = null;
  try {
    const res = await routeGateway({
      nodeId: gatewayForm.nodeId,
      rules: gatewayForm.rules.map((r) => ({
        condition: r.condition,
        targetNodeId: r.targetNodeId,
        priority: r.priority ?? 0,
        outputMappingJson: r.outputMappingJson,
      })),
      edges: gatewayForm.edges.map((e) => ({
        target: e.target,
        conditionExpression: e.conditionExpression,
      })),
      variablesJson: gatewayForm.variablesJson,
    });
    if (res.code !== 200) {
      routeResult.value = { failed: true, message: res.message };
      return;
    }
    routeResult.value = { ...res.data };
  } catch (error) {
    ElMessage.error("路由失败（规则引擎服务不可用？）");
  } finally {
    gatewayLoading.value = false;
  }
};

const hitSourceText = (source: string) => {
  const map: Record<string, string> = {
    EMBEDDED_RULE: "内嵌规则命中",
    EDGE_CONDITION: "边条件命中",
    DEFAULT_EDGE: "默认边兜底",
  };
  return map[source] || source;
};

const hitSourceType = (source: string) => {
  const map: Record<string, any> = {
    EMBEDDED_RULE: "success",
    EDGE_CONDITION: "primary",
    DEFAULT_EDGE: "info",
  };
  return map[source] || "info";
};

const prettyJson = (raw: string) => {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
};
</script>

<style scoped>
.rule-playground-container {
  background: #ffffff;
  min-height: 0;
  padding: 20px;
  color: #333333;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 12px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e0e6ed;
}

.title {
  font-size: 24px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.header-sub {
  color: #64748b;
  font-size: 13px;
}

.panel {
  padding: 8px 4px;
}

.panel-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.panel-label {
  font-family: monospace;
  color: #475569;
  font-size: 13px;
}

.toolbar-btns {
  display: flex;
  gap: 8px;
}

.result-area {
  margin-top: 16px;
}

.assignee-result {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.result-label {
  color: #475569;
  font-size: 13px;
}

.tip-box {
  margin-top: 16px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 6px;
  padding: 12px 14px;
}

.tip-title {
  font-weight: 600;
  color: #92400e;
  margin-bottom: 6px;
  font-size: 13px;
}

.tip-item {
  color: #b45309;
  font-size: 12px;
  line-height: 1.9;
}

.sub-panel {
  margin-bottom: 18px;
}

.sub-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #475569;
  font-size: 13px;
  margin-bottom: 8px;
}

.route-result {
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}

.route-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.route-target {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  font-family: monospace;
}

.route-json {
  background: #0f172a;
  color: #a5f3fc;
  padding: 10px;
  border-radius: 6px;
  font-size: 12px;
  margin: 0;
  flex: 1;
}
</style>
