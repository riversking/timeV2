<template>
  <div class="flow-def-container">
    <!-- 头部 -->
    <div class="header">
      <h1 class="title">流程定义管理</h1>
      <div class="header-actions">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon> 新建定义
        </el-button>
      </div>
    </div>

    <!-- 查询栏（按 definitionKey 精确查询，version 可选） -->
    <div class="search-bar">
      <el-input
        v-model="queryKey"
        placeholder="流程定义标识 definitionKey（必填，如 leave_v3_xxx）"
        clearable
        style="max-width: 380px"
        @keyup.enter="handleQuery"
      />
      <el-input
        v-model="queryVersion"
        placeholder="版本号（空=最新已发布）"
        clearable
        style="max-width: 200px"
        @keyup.enter="handleQuery"
      />
      <el-button type="primary" @click="handleQuery">
        <el-icon><Search /></el-icon> 查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <!-- 结果表格 -->
    <div class="table-container">
      <el-table
        :data="tableData"
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa', color: '#333' }"
        :cell-style="{ backgroundColor: '#ffffff', color: '#333' }"
        v-loading="loading"
        empty-text="请输入 definitionKey 查询流程定义"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column
          prop="definitionKey"
          label="定义标识"
          min-width="190"
          show-overflow-tooltip
        />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="100" show-overflow-tooltip />
        <el-table-column
          prop="description"
          label="描述"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column prop="createUser" label="创建人" width="90" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showJson(row)">查看JSON</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              size="small"
              type="success"
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              size="small"
              type="warning"
              @click="handleDisable(row)"
            >
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建定义对话框 -->
    <el-dialog
      v-model="createDialogVisible"
      title="新建流程定义"
      width="720px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form label-width="110px" label-position="right">
        <el-form-item label="定义标识" required>
          <el-input
            v-model="form.definitionKey"
            placeholder="唯一业务键，例如 leave_apply_001"
          />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="流程名称，例如 请假申请流程" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="例如 OA / test" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="图标标识或URL（可选）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="流程用途说明（可选）"
          />
        </el-form-item>
        <el-form-item label="definitionJson" required>
          <el-input
            v-model="form.definitionJson"
            type="textarea"
            :rows="12"
            placeholder='流程定义 DSL JSON，结构 {"key","name","nodes":[...],"edges":[...]}'
            style="font-family: monospace"
          />
          <div class="json-tools">
            <el-button size="small" type="warning" plain @click="openDesignerFromCreate">
              <el-icon><Connection /></el-icon> 图形化设计
            </el-button>
            <el-button size="small" @click="fillSampleJson">填入示例</el-button>
            <el-button size="small" type="primary" plain @click="checkJsonLocal">
              本地JSON校验
            </el-button>
            <el-button size="small" type="success" plain @click="checkJsonByRuleEngine">
              规则引擎校验
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">
          提交
        </el-button>
      </template>
    </el-dialog>

    <!-- JSON 查看对话框 -->
    <el-dialog v-model="jsonDialogVisible" title="流程定义 JSON" width="760px" append-to-body>
      <div class="json-toolbar">
        <el-tag size="small" effect="plain">{{ jsonViewKey }}</el-tag>
        <div class="json-toolbar-actions">
          <el-button size="small" type="warning" plain @click="openDesignerFromView">
            在设计中打开
          </el-button>
          <el-button size="small" @click="copyJson">复制</el-button>
        </div>
      </div>
      <pre class="json-view">{{ jsonViewContent }}</pre>
    </el-dialog>

    <!-- 图形化流程设计器 -->
    <el-dialog
      v-model="designerVisible"
      title="图形化流程设计器"
      fullscreen
      append-to-body
      :close-on-click-modal="false"
      destroy-on-close
    >
      <ProcessDesigner
        v-if="designerVisible"
        :model-value="designerInitJson"
        :default-key="form.definitionKey"
        :default-name="form.name"
        @apply="onDesignerApply"
        @cancel="designerVisible = false"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onActivated } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Search, Connection } from "@element-plus/icons-vue";
import ProcessDesigner from "@/components/process-designer/ProcessDesigner.vue";
import {
  getLatestDefinition,
  getDefinitionByKeyAndVersion,
  createDefinition,
  publishDefinition,
  disableDefinition,
} from "@/api/approval";
import { validateDefinitionRule } from "@/api/ruleengine";

// ==================== 查询 ====================
const queryKey = ref("");
const queryVersion = ref("");
const tableData = ref<any[]>([]);
const loading = ref(false);

const statusText = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: "草稿",
    PUBLISHED: "已发布",
    DISABLED: "已停用",
  };
  return map[status] || status;
};

const statusTagType = (status: string) => {
  const map: Record<string, any> = {
    DRAFT: "warning",
    PUBLISHED: "success",
    DISABLED: "info",
  };
  return map[status] || "info";
};

// definition_key 为 proto 蛇形字段，网关序列化后不返回该字段，查询结果用输入值回填
const handleQuery = async () => {
  if (!queryKey.value) {
    ElMessage.warning("请输入 definitionKey");
    return;
  }
  loading.value = true;
  try {
    const version = queryVersion.value ? Number(queryVersion.value) : 0;
    const res = version
      ? await getDefinitionByKeyAndVersion({
          definitionKey: queryKey.value,
          version,
        })
      : await getLatestDefinition({ definitionKey: queryKey.value });
    if (res.code === 200 && res.data) {
      tableData.value = [{ ...res.data, definitionKey: queryKey.value }];
    } else {
      tableData.value = [];
      ElMessage.warning(res.message || "未查询到流程定义");
    }
  } catch (error) {
    tableData.value = [];
    ElMessage.error("查询失败");
  } finally {
    loading.value = false;
  }
};

const handleReset = () => {
  queryKey.value = "";
  queryVersion.value = "";
  tableData.value = [];
};

// ==================== 生命周期操作 ====================
const refreshRow = async () => {
  await handleQuery();
};

const handlePublish = (row: any) => {
  ElMessageBox.confirm(
    `确定发布流程定义 "${row.name}" (v${row.version}) 吗？发布后可用于发起流程。`,
    "提示",
    { confirmButtonText: "确定发布", cancelButtonText: "取消", type: "warning" },
  ).then(async () => {
    try {
      const res = await publishDefinition({ id: row.id });
      if (res.code !== 200) {
        ElMessage.error(res.message || "发布失败");
        return;
      }
      ElMessage.success("发布成功");
      refreshRow();
    } catch (error) {
      ElMessage.error("发布失败");
    }
  });
};

const handleDisable = (row: any) => {
  ElMessageBox.confirm(
    `确定停用流程定义 "${row.name}" (v${row.version}) 吗？停用后无法再发起新流程。`,
    "提示",
    { confirmButtonText: "确定停用", cancelButtonText: "取消", type: "warning" },
  ).then(async () => {
    try {
      const res = await disableDefinition({ id: row.id });
      if (res.code !== 200) {
        ElMessage.error(res.message || "停用失败");
        return;
      }
      ElMessage.success("停用成功");
      refreshRow();
    } catch (error) {
      ElMessage.error("停用失败");
    }
  });
};

// ==================== 新建 ====================
const createDialogVisible = ref(false);
const submitting = ref(false);
const form = reactive({
  definitionKey: "",
  name: "",
  description: "",
  category: "",
  icon: "",
  definitionJson: "",
});

const openCreateDialog = () => {
  form.definitionKey = "";
  form.name = "";
  form.description = "";
  form.category = "";
  form.icon = "";
  form.definitionJson = "";
  createDialogVisible.value = true;
};

const fillSampleJson = () => {
  form.definitionJson = JSON.stringify(
    {
      key: form.definitionKey || "leave_demo",
      name: form.name || "请假流程演示",
      nodes: [
        { id: "start", type: "START", name: "开始" },
        {
          id: "dept",
          type: "USER_TASK",
          name: "部门审批(领单)",
          config: { candidateUsers: ["userA", "userB"], taskMode: "CLAIM" },
        },
        {
          id: "mgr",
          type: "USER_TASK",
          name: "经理审批(免领单)",
          config: { candidateUsers: ["userC"], taskMode: "ANY_ONE" },
        },
        { id: "end", type: "END", name: "结束" },
      ],
      edges: [
        { id: "e1", source: "start", target: "dept" },
        { id: "e2", source: "dept", target: "mgr" },
        { id: "e3", source: "mgr", target: "end" },
      ],
    },
    null,
    2,
  );
};

const checkJsonLocal = () => {
  try {
    JSON.parse(form.definitionJson);
    ElMessage.success("JSON 格式合法");
  } catch (e: any) {
    ElMessage.error("JSON 解析失败: " + e.message);
  }
};

// 调用 rule-engine-server 做与引擎一致的规则级校验（内嵌规则/网关配置）
const checkJsonByRuleEngine = async () => {
  if (!form.definitionJson) {
    ElMessage.warning("请先填写 definitionJson");
    return;
  }
  try {
    const res = await validateDefinitionRule({
      definitionJson: form.definitionJson,
    });
    if (res.code !== 200) {
      ElMessage.error(res.message || "校验接口调用失败");
      return;
    }
    const errors = res.data?.errors || [];
    if (errors.length === 0) {
      ElMessage.success("校验通过：无规则错误");
    } else {
      const first = errors[0];
      ElMessageBox.alert(
        `节点: ${first.nodeId || "(JSON整体)"}\n规则下标: ${first.ruleIndex}\n错误: ${first.message}`,
        "校验失败",
        { confirmButtonText: "知道了", type: "error" },
      );
    }
  } catch (error) {
    ElMessage.error("校验失败（规则引擎服务不可用？）");
  }
};

const handleCreate = async () => {
  if (!form.definitionKey || !form.name || !form.definitionJson) {
    ElMessage.warning("定义标识、名称、definitionJson 为必填项");
    return;
  }
  try {
    JSON.parse(form.definitionJson);
  } catch (e: any) {
    ElMessage.error("definitionJson 不是合法 JSON: " + e.message);
    return;
  }
  submitting.value = true;
  try {
    const res = await createDefinition({ ...form });
    if (res.code !== 200) {
      ElMessage.error(res.message || "创建失败");
      return;
    }
    ElMessage.success("创建成功（草稿状态，需发布后生效）");
    createDialogVisible.value = false;
    // 自动查询新建的定义：create 恒为 v1 草稿，空版本走 getLatest（仅已发布）会查不到，故按 v1 精确查询
    queryKey.value = form.definitionKey;
    queryVersion.value = "1";
    await handleQuery();
  } catch (error) {
    ElMessage.error("创建失败（该 definitionKey 可能已存在，请更换标识）");
  } finally {
    submitting.value = false;
  }
};

// ==================== JSON 查看 ====================
const jsonDialogVisible = ref(false);
const jsonViewKey = ref("");
const jsonViewContent = ref("");

const showJson = (row: any) => {
  jsonViewKey.value = `${row.definitionKey} (v${row.version})`;
  try {
    jsonViewContent.value = JSON.stringify(JSON.parse(row.definitionJson), null, 2);
  } catch {
    jsonViewContent.value = row.definitionJson || "";
  }
  jsonDialogVisible.value = true;
};

const copyJson = async () => {
  try {
    await navigator.clipboard.writeText(jsonViewContent.value);
    ElMessage.success("已复制到剪贴板");
  } catch {
    ElMessage.error("复制失败");
  }
};

// ==================== 图形化设计器 ====================
const designerVisible = ref(false);
const designerInitJson = ref("");
const designerOrigin = ref<"create" | "view">("create");

const openDesignerFromCreate = () => {
  designerOrigin.value = "create";
  designerInitJson.value = form.definitionJson || "";
  designerVisible.value = true;
};

const openDesignerFromView = () => {
  designerOrigin.value = "view";
  designerInitJson.value = jsonViewContent.value || "";
  jsonDialogVisible.value = false;
  designerVisible.value = true;
};

const onDesignerApply = (json: string) => {
  if (designerOrigin.value === "create") {
    // 回写 definitionJson；key/name 与表单保持一致
    try {
      const def = JSON.parse(json);
      def.key = form.definitionKey || def.key || "";
      def.name = form.name || def.name || "";
      form.definitionJson = JSON.stringify(def, null, 2);
    } catch {
      form.definitionJson = json;
    }
    designerVisible.value = false;
    ElMessage.success("设计内容已写入 definitionJson");
    return;
  }
  jsonViewContent.value = json;
  designerVisible.value = false;
  jsonDialogVisible.value = true;
  ElMessage.success("设计内容已更新到 JSON 预览（仅本地，如需保存请复制）");
};

onActivated(() => {
  // 页面缓存激活时保留查询状态即可
});
</script>

<style scoped>
.flow-def-container {
  background: #ffffff;
  min-height: 0;
  padding: 20px;
  color: #333333;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e0e6ed;
}

.title {
  font-size: 24px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  padding: 16px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.table-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  margin-bottom: 20px;
}

.json-tools {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.json-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.json-toolbar-actions {
  display: flex;
  gap: 8px;
}

.json-view {
  max-height: 480px;
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
