<template>
  <div class="flow-inst-container">
    <!-- 头部 -->
    <div class="header">
      <h1 class="title">流程实例管理</h1>
      <div class="header-actions">
        <el-button type="primary" @click="openStartDialog">
          <el-icon><Promotion /></el-icon> 发起流程
        </el-button>
        <el-button @click="refreshCurrent">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <!-- 查询方式 Tabs -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="我发起的" name="mine" />
      <el-tab-pane label="按状态" name="status" />
      <el-tab-pane label="按业务键" name="business" />
      <el-tab-pane label="按编号" name="no" />
    </el-tabs>

    <!-- 条件区 -->
    <div class="search-bar">
      <template v-if="activeTab === 'status'">
        <el-select v-model="statusFilter" style="width: 200px">
          <el-option label="运行中 RUNNING" value="RUNNING" />
          <el-option label="已完成 COMPLETED" value="COMPLETED" />
          <el-option label="已终止 TERMINATED" value="TERMINATED" />
        </el-select>
        <el-button type="primary" @click="handleStatusQuery">
          <el-icon><Search /></el-icon> 查询
        </el-button>
      </template>
      <template v-else-if="activeTab === 'business'">
        <el-input
          v-model="businessKey"
          placeholder="业务键 businessKey"
          clearable
          style="max-width: 360px"
          @keyup.enter="handleBusinessQuery"
        />
        <el-button type="primary" @click="handleBusinessQuery">
          <el-icon><Search /></el-icon> 查询
        </el-button>
      </template>
      <template v-else-if="activeTab === 'no'">
        <el-input
          v-model="instanceNo"
          placeholder="实例编号 instanceNo，如 PI-xxxx"
          clearable
          style="max-width: 360px"
          @keyup.enter="handleNoQuery"
        />
        <el-button type="primary" @click="handleNoQuery">
          <el-icon><Search /></el-icon> 查询
        </el-button>
      </template>
      <template v-else>
        <span class="hint-text">展示当前登录用户发起的所有流程实例</span>
        <el-button type="primary" @click="fetchMine">
          <el-icon><Search /></el-icon> 查询
        </el-button>
      </template>
    </div>

    <!-- 实例表格 -->
    <div class="table-container">
      <el-table
        :data="tableData"
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa', color: '#333' }"
        :cell-style="{ backgroundColor: '#ffffff', color: '#333' }"
        v-loading="loading"
        empty-text="暂无数据"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="instanceNo" label="实例编号" min-width="200" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="definitionKey" label="定义标识" min-width="160" show-overflow-tooltip />
        <el-table-column prop="definitionVersion" label="版本" width="70">
          <template #default="{ row }">v{{ row.definitionVersion }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="instanceStatusType(row.status)" size="small" effect="plain">
              {{ instanceStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="initiatorName" label="发起人" width="100" />
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column prop="endTime" label="结束时间" width="170" />
        <el-table-column prop="approvalResult" label="审批结果" width="110">
          <template #default="{ row }">
            <el-tag
              v-if="row.approvalResult"
              :type="approvalResultType(row.approvalResult)"
              size="small"
              effect="plain"
            >
              {{ approvalResultText(row.approvalResult) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="showTrack(row)">
              跟踪
            </el-button>
            <el-button size="small" @click="showHistory(row)">历史</el-button>
            <el-button
              v-if="row.status === 'RUNNING'"
              size="small"
              type="danger"
              plain
              @click="handleTerminate(row)"
            >
              终止
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页（后端分页接口不返回 total，按"当前页是否满页"推算下一页可用性） -->
    <div class="pagination" v-if="activeTab === 'mine' || activeTab === 'status'">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="estimatedTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="handlePageChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 发起流程对话框 -->
    <el-dialog
      v-model="startDialogVisible"
      title="发起流程"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="定义标识" required>
          <el-input
            v-model="startForm.definitionKey"
            placeholder="已发布流程的 definitionKey"
          />
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="startForm.title" placeholder="例如：张三的请假申请" />
        </el-form-item>
        <el-form-item label="业务键">
          <el-input v-model="startForm.businessKey" placeholder="关联业务单据键（可选）" />
        </el-form-item>
        <el-form-item label="流程变量">
          <el-input
            v-model="startForm.variables"
            type="textarea"
            :rows="6"
            placeholder='JSON 对象，例如 {"amount": 500, "days": 3}'
            style="font-family: monospace"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleStart">
          发起
        </el-button>
      </template>
    </el-dialog>

    <!-- 流程跟踪时间线对话框 -->
    <el-dialog v-model="trackDialogVisible" title="流程跟踪" width="720px">
      <div v-loading="trackLoading">
        <el-timeline v-if="tracks.length">
          <el-timeline-item
            v-for="(item, index) in tracks"
            :key="index"
            :type="trackItemType(item.status)"
            :timestamp="item.approveTime || item.taskTime"
            placement="top"
          >
            <div class="track-card">
              <div class="track-line1">
                <span class="track-node">{{ item.nodeName }}</span>
                <el-tag :type="trackItemType(item.status)" size="small" effect="plain">
                  {{ trackStatusText(item.status) }}
                </el-tag>
              </div>
              <div class="track-line2">
                <span>处理人：{{ item.assignee || "-" }}</span>
                <span v-if="item.nextAssignee">下一处理人：{{ item.nextAssignee }}</span>
              </div>
              <div class="track-line2">
                <span>任务时间：{{ item.taskTime || "-" }}</span>
                <span v-if="item.approveTime">审批时间：{{ item.approveTime }}</span>
              </div>
              <div v-if="item.opinion" class="track-opinion">意见：{{ item.opinion }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else-if="!trackLoading" description="暂无跟踪数据" />
      </div>
    </el-dialog>

    <!-- 历史流水对话框 -->
    <el-dialog v-model="historyDialogVisible" title="流程历史流水" width="900px">
      <el-table
        :data="histories"
        size="small"
        v-loading="historyLoading"
        max-height="480"
        empty-text="暂无历史数据"
      >
        <el-table-column prop="eventType" label="事件" width="150" show-overflow-tooltip />
        <el-table-column prop="nodeName" label="节点" width="140" show-overflow-tooltip />
        <el-table-column prop="taskName" label="任务" width="140" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column prop="assignee" label="办理人" width="110" />
        <el-table-column prop="result" label="结果" width="100" />
        <el-table-column prop="opinion" label="意见" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onActivated } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Promotion, Refresh, Search } from "@element-plus/icons-vue";
import {
  listMyInitiated,
  listInstancesByStatus,
  listInstancesByBusinessKey,
  getInstanceByNo,
  startProcess,
  terminateProcess,
  trackByInstance,
  listHistoryByInstance,
  getCurrentUserInfo,
} from "@/api/approval";

// ==================== 通用 ====================
const activeTab = ref("mine");
const tableData = ref<any[]>([]);
const loading = ref(false);
const currentPage = ref(1);
const pageSize = ref(10);

// 无 total 的分页：当前页满页时认为可能有下一页
const estimatedTotal = computed(() => {
  if (tableData.value.length === pageSize.value) {
    return currentPage.value * pageSize.value + 1;
  }
  return (currentPage.value - 1) * pageSize.value + tableData.value.length;
});

const instanceStatusText = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: "运行中",
    COMPLETED: "已完成",
    TERMINATED: "已终止",
    FAILED: "失败",
  };
  return map[status] || status;
};

const instanceStatusType = (status: string) => {
  const map: Record<string, any> = {
    RUNNING: "primary",
    COMPLETED: "success",
    TERMINATED: "info",
    FAILED: "danger",
  };
  return map[status] || "info";
};

const approvalResultText = (result: string) => {
  const map: Record<string, string> = {
    APPROVED: "通过",
    REJECTED: "拒绝",
    RETURNED: "退回",
    CANCELLED: "取消",
  };
  return map[result] || result;
};

const approvalResultType = (result: string) => {
  const map: Record<string, any> = {
    APPROVED: "success",
    REJECTED: "danger",
    RETURNED: "warning",
    CANCELLED: "info",
  };
  return map[result] || "info";
};

// ==================== 查询 ====================
const fetchMine = async () => {
  loading.value = true;
  try {
    const res = await listMyInitiated({
      currentPage: currentPage.value,
      pageSize: pageSize.value,
    });
    if (res.code === 200) {
      tableData.value = res.data?.instances || [];
    } else {
      tableData.value = [];
      ElMessage.warning(res.message || "查询失败");
    }
  } catch (error) {
    tableData.value = [];
    ElMessage.error("查询失败");
  } finally {
    loading.value = false;
  }
};

const statusFilter = ref("RUNNING");
const handleStatusQuery = async () => {
  if (!statusFilter.value) {
    ElMessage.warning("请选择状态");
    return;
  }
  loading.value = true;
  try {
    const res = await listInstancesByStatus({
      status: statusFilter.value,
      currentPage: currentPage.value,
      pageSize: pageSize.value,
    });
    if (res.code === 200) {
      tableData.value = res.data?.instances || [];
    } else {
      tableData.value = [];
      ElMessage.warning(res.message || "查询失败");
    }
  } catch (error) {
    tableData.value = [];
    ElMessage.error("查询失败");
  } finally {
    loading.value = false;
  }
};

const businessKey = ref("");
const handleBusinessQuery = async () => {
  if (!businessKey.value) {
    ElMessage.warning("请输入业务键");
    return;
  }
  loading.value = true;
  try {
    const res = await listInstancesByBusinessKey({
      businessKey: businessKey.value,
    });
    if (res.code === 200) {
      tableData.value = res.data?.instances || [];
    } else {
      tableData.value = [];
      ElMessage.warning(res.message || "查询失败");
    }
  } catch (error) {
    tableData.value = [];
    ElMessage.error("查询失败");
  } finally {
    loading.value = false;
  }
};

const instanceNo = ref("");
const handleNoQuery = async () => {
  if (!instanceNo.value) {
    ElMessage.warning("请输入实例编号");
    return;
  }
  loading.value = true;
  try {
    const res = await getInstanceByNo({ instanceNo: instanceNo.value });
    if (res.code === 200 && res.data) {
      tableData.value = [res.data];
    } else {
      tableData.value = [];
      ElMessage.warning(res.message || "未查询到实例");
    }
  } catch (error) {
    tableData.value = [];
    ElMessage.error("查询失败");
  } finally {
    loading.value = false;
  }
};

const handleTabChange = () => {
  currentPage.value = 1;
  tableData.value = [];
  if (activeTab.value === "mine") {
    fetchMine();
  }
};

const handlePageChange = () => {
  if (activeTab.value === "mine") {
    fetchMine();
  } else if (activeTab.value === "status") {
    handleStatusQuery();
  }
};

const refreshCurrent = () => {
  if (activeTab.value === "mine") {
    fetchMine();
  } else if (activeTab.value === "status") {
    handleStatusQuery();
  } else if (activeTab.value === "business" && businessKey.value) {
    handleBusinessQuery();
  } else if (activeTab.value === "no" && instanceNo.value) {
    handleNoQuery();
  }
};

// ==================== 发起流程 ====================
const startDialogVisible = ref(false);
const submitting = ref(false);
const startForm = reactive({
  definitionKey: "",
  title: "",
  businessKey: "",
  variables: "",
});

const openStartDialog = () => {
  startForm.definitionKey = "";
  startForm.title = "";
  startForm.businessKey = "";
  startForm.variables = "";
  startDialogVisible.value = true;
};

const handleStart = async () => {
  if (!startForm.definitionKey || !startForm.title) {
    ElMessage.warning("定义标识与标题为必填项");
    return;
  }
  if (startForm.variables) {
    try {
      JSON.parse(startForm.variables);
    } catch (e: any) {
      ElMessage.error("流程变量不是合法 JSON: " + e.message);
      return;
    }
  }
  submitting.value = true;
  try {
    const me = getCurrentUserInfo();
    const res = await startProcess({
      definitionKey: startForm.definitionKey,
      title: startForm.title,
      businessKey: startForm.businessKey,
      initiator: me.userId,
      initiatorName: me.username,
      variables: startForm.variables,
    });
    if (res.code !== 200) {
      ElMessage.error(res.message || "发起失败");
      return;
    }
    ElMessage.success(`发起成功，实例编号：${res.data?.instanceNo || ""}`);
    startDialogVisible.value = false;
    activeTab.value = "mine";
    currentPage.value = 1;
    fetchMine();
  } catch (error) {
    ElMessage.error("发起失败");
  } finally {
    submitting.value = false;
  }
};

// ==================== 终止 ====================
const handleTerminate = (row: any) => {
  ElMessageBox.confirm(
    `确定终止实例 "${row.title}"（${row.instanceNo}）吗？终止后流程不再流转。`,
    "提示",
    { confirmButtonText: "确定终止", cancelButtonText: "取消", type: "warning" },
  ).then(async () => {
    try {
      const me = getCurrentUserInfo();
      const res = await terminateProcess({
        instanceId: row.id,
        operator: me.userId,
      });
      if (res.code !== 200) {
        ElMessage.error(res.message || "终止失败");
        return;
      }
      ElMessage.success("终止成功");
      refreshCurrent();
    } catch (error) {
      ElMessage.error("终止失败");
    }
  });
};

// ==================== 跟踪时间线 ====================
const trackDialogVisible = ref(false);
const trackLoading = ref(false);
const tracks = ref<any[]>([]);

const trackStatusText = (status: string) => {
  const map: Record<string, string> = {
    STARTED: "已发起",
    CLAIM: "已认领",
    PENDING: "待处理",
    TRANSFERRED: "已转交",
    APPROVED: "已通过",
    REJECTED: "已拒绝",
    RETURNED: "已退回",
    CANCELLED: "已取消",
  };
  return map[status] || status;
};

const trackItemType = (status: string) => {
  const map: Record<string, any> = {
    STARTED: "primary",
    CLAIM: "info",
    PENDING: "warning",
    TRANSFERRED: "info",
    APPROVED: "success",
    REJECTED: "danger",
    RETURNED: "danger",
    CANCELLED: "info",
  };
  return map[status] || "info";
};

const showTrack = async (row: any) => {
  trackDialogVisible.value = true;
  trackLoading.value = true;
  tracks.value = [];
  try {
    const res = await trackByInstance({ instanceId: row.id });
    if (res.code === 200) {
      tracks.value = res.data?.tracks || [];
    } else {
      ElMessage.warning(res.message || "加载跟踪失败");
    }
  } catch (error) {
    ElMessage.error("加载跟踪失败");
  } finally {
    trackLoading.value = false;
  }
};

// ==================== 历史流水 ====================
const historyDialogVisible = ref(false);
const historyLoading = ref(false);
const histories = ref<any[]>([]);

const showHistory = async (row: any) => {
  historyDialogVisible.value = true;
  historyLoading.value = true;
  histories.value = [];
  try {
    const res = await listHistoryByInstance({ instanceId: row.id });
    if (res.code === 200) {
      histories.value = res.data?.histories || [];
    } else {
      ElMessage.warning(res.message || "加载历史失败");
    }
  } catch (error) {
    ElMessage.error("加载历史失败");
  } finally {
    historyLoading.value = false;
  }
};

onActivated(() => {
  if (tableData.value.length === 0) {
    fetchMine();
  }
});
</script>

<style scoped>
.flow-inst-container {
  background: #ffffff;
  min-height: 0;
  padding: 20px;
  color: #333333;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.header-actions {
  display: flex;
  gap: 12px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding: 16px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.hint-text {
  color: #64748b;
  font-size: 13px;
}

.table-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: right;
}

.track-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px 14px;
}

.track-line1 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.track-node {
  font-weight: 600;
  color: #1e293b;
}

.track-line2 {
  display: flex;
  gap: 20px;
  color: #475569;
  font-size: 13px;
  line-height: 1.8;
}

.track-opinion {
  margin-top: 4px;
  color: #b45309;
  font-size: 13px;
}
</style>
