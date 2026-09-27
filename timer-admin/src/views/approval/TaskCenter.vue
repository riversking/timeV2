<template>
  <div class="task-center-container">
    <!-- 头部 -->
    <div class="header">
      <h1 class="title">任务中心</h1>
      <div class="header-actions">
        <el-button @click="fetchCurrent">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <!-- Tab：待办 / 可认领 -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="我的待办" name="todo" />
      <el-tab-pane label="可认领任务" name="claimable" />
    </el-tabs>

    <!-- 任务表格 -->
    <div class="table-container">
      <el-table
        :data="tableData"
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa', color: '#333' }"
        :cell-style="{ backgroundColor: '#ffffff', color: '#333' }"
        v-loading="loading"
        empty-text="暂无任务"
      >
        <el-table-column prop="taskNo" label="任务编号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="taskName" label="任务名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="instanceId" label="实例ID" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="taskStatusType(row.status)" size="small" effect="plain">
              {{ taskStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assignee" label="办理人" width="140" show-overflow-tooltip />
        <el-table-column
          prop="candidateUsers"
          label="候选用户"
          min-width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.candidateUsers || "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="claimedBy" label="认领人" width="130" show-overflow-tooltip />
        <el-table-column prop="claimedTime" label="认领时间" width="170" />
        <el-table-column prop="dueTime" label="截止时间" width="170" />
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <template v-if="activeTab === 'todo'">
              <el-button size="small" type="success" @click="openActionDialog(row, 'approve')">
                审批
              </el-button>
              <el-button size="small" type="danger" @click="openActionDialog(row, 'reject')">
                拒绝
              </el-button>
              <el-button size="small" type="warning" @click="openActionDialog(row, 'return')">
                退回
              </el-button>
              <el-button
                v-if="row.status === 'CLAIMED'"
                size="small"
                type="primary"
                plain
                @click="openTransferDialog(row)"
              >
                转交
              </el-button>
              <el-button size="small" plain @click="handleCancel(row)">取消</el-button>
              <el-button size="small" text type="primary" @click="showTrack(row)">
                跟踪
              </el-button>
            </template>
            <template v-else>
              <el-button size="small" type="primary" @click="handleClaim(row)">
                认领
              </el-button>
              <el-button size="small" text type="primary" @click="showTrack(row)">
                跟踪
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页（无 total，按满页推算） -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="estimatedTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchCurrent"
        @current-change="fetchCurrent"
      />
    </div>

    <!-- 办理动作对话框（审批 / 拒绝 / 退回） -->
    <el-dialog
      v-model="actionDialogVisible"
      :title="actionTitle"
      width="560px"
      :close-on-click-modal="false"
    >
      <div class="action-summary">
        <div>任务编号：{{ actionRow?.taskNo }}</div>
        <div>任务名称：{{ actionRow?.taskName }}</div>
        <div>办理人：{{ actionRow?.assignee }}</div>
      </div>
      <el-input
        v-model="actionComment"
        type="textarea"
        :rows="4"
        :placeholder="actionPlaceholder"
      />
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleActionSubmit"
        >
          确认{{ actionText }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 转交对话框 -->
    <el-dialog
      v-model="transferDialogVisible"
      title="转交任务"
      width="560px"
      :close-on-click-modal="false"
    >
      <div class="action-summary">
        <div>任务编号：{{ transferRow?.taskNo }}</div>
        <div>当前办理人：{{ transferRow?.assignee }}</div>
        <div class="hint-text">仅已认领任务可转交，操作人必须为当前办理人</div>
      </div>
      <el-input
        v-model="transferTarget"
        placeholder="目标用户 userId"
        style="margin-top: 10px"
      />
      <el-input
        v-model="transferReason"
        type="textarea"
        :rows="3"
        placeholder="转交原因（可选，将记录到审批意见）"
        style="margin-top: 10px"
      />
      <template #footer>
        <el-button @click="transferDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleTransferSubmit">
          确认转交
        </el-button>
      </template>
    </el-dialog>

    <!-- 流程跟踪对话框 -->
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onActivated } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import {
  listTodoTasks,
  listClaimableTasks,
  claimTask,
  approveTask,
  rejectTask,
  returnTask,
  cancelTask,
  transferTask,
  trackByInstance,
  getCurrentUserInfo,
} from "@/api/approval";

// ==================== 列表 ====================
const activeTab = ref("todo");
const tableData = ref<any[]>([]);
const loading = ref(false);
const currentPage = ref(1);
const pageSize = ref(10);

const estimatedTotal = computed(() => {
  if (tableData.value.length === pageSize.value) {
    return currentPage.value * pageSize.value + 1;
  }
  return (currentPage.value - 1) * pageSize.value + tableData.value.length;
});

const taskStatusText = (status: string) => {
  const map: Record<string, string> = {
    PENDING: "待处理",
    CLAIMED: "已认领",
    WAITING: "等待中",
    COMPLETED: "已完成",
    TRANSFERRED: "已转交",
    CANCELLED: "已取消",
  };
  return map[status] || status;
};

const taskStatusType = (status: string) => {
  const map: Record<string, any> = {
    PENDING: "warning",
    CLAIMED: "primary",
    WAITING: "info",
    COMPLETED: "success",
    TRANSFERRED: "info",
    CANCELLED: "info",
  };
  return map[status] || "info";
};

const fetchCurrent = async () => {
  loading.value = true;
  try {
    const api = activeTab.value === "todo" ? listTodoTasks : listClaimableTasks;
    const res = await api({
      currentPage: currentPage.value,
      pageSize: pageSize.value,
    });
    if (res.code === 200) {
      tableData.value = res.data?.tasks || [];
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

const handleTabChange = () => {
  currentPage.value = 1;
  tableData.value = [];
  fetchCurrent();
};

// ==================== 办理动作（审批/拒绝/退回） ====================
const actionDialogVisible = ref(false);
const actionType = ref("approve");
const actionRow = ref<any>(null);
const actionComment = ref("");
const submitting = ref(false);

const actionText = computed(() => {
  const map: Record<string, string> = {
    approve: "审批",
    reject: "拒绝",
    return: "退回",
  };
  return map[actionType.value] || "操作";
});

const actionTitle = computed(() => `任务${actionText.value}`);
const actionPlaceholder = computed(() => {
  const map: Record<string, string> = {
    approve: "审批意见（可选）",
    reject: "拒绝原因（建议填写）",
    return: "退回原因（建议填写）",
  };
  return map[actionType.value] || "意见";
});

const openActionDialog = (row: any, type: string) => {
  actionRow.value = row;
  actionType.value = type;
  actionComment.value = "";
  actionDialogVisible.value = true;
};

const handleActionSubmit = async () => {
  if (!actionRow.value) return;
  submitting.value = true;
  try {
    const me = getCurrentUserInfo();
    const payload = {
      taskNo: actionRow.value.taskNo,
      comment: actionComment.value,
      instanceId: String(actionRow.value.instanceId),
      userId: me.userId,
    };
    const api =
      actionType.value === "approve"
        ? approveTask
        : actionType.value === "reject"
          ? rejectTask
          : returnTask;
    const res = await api(payload);
    if (res.code !== 200) {
      ElMessage.error(res.message || `${actionText.value}失败`);
      return;
    }
    ElMessage.success(`${actionText.value}成功`);
    actionDialogVisible.value = false;
    fetchCurrent();
  } catch (error) {
    ElMessage.error(`${actionText.value}失败`);
  } finally {
    submitting.value = false;
  }
};

// ==================== 认领 ====================
const handleClaim = (row: any) => {
  ElMessageBox.confirm(`确定认领任务 "${row.taskName}"（${row.taskNo}）吗？`, "提示", {
    confirmButtonText: "确定认领",
    cancelButtonText: "取消",
    type: "warning",
  }).then(async () => {
    try {
      const me = getCurrentUserInfo();
      const res = await claimTask({
        taskNo: row.taskNo,
        instanceId: String(row.instanceId),
        userId: me.userId,
      });
      if (res.code !== 200) {
        ElMessage.error(res.message || "认领失败");
        return;
      }
      ElMessage.success("认领成功，任务已进入我的待办");
      fetchCurrent();
    } catch (error) {
      ElMessage.error("认领失败");
    }
  });
};

// ==================== 取消 ====================
const handleCancel = (row: any) => {
  ElMessageBox.confirm(`确定取消任务 "${row.taskName}"（${row.taskNo}）吗？`, "提示", {
    confirmButtonText: "确定取消",
    cancelButtonText: "再想想",
    type: "warning",
  }).then(async () => {
    try {
      const me = getCurrentUserInfo();
      const res = await cancelTask({
        taskNo: row.taskNo,
        operator: me.userId,
        instanceId: String(row.instanceId),
        userId: me.userId,
      });
      if (res.code !== 200) {
        ElMessage.error(res.message || "取消失败");
        return;
      }
      ElMessage.success("已取消");
      fetchCurrent();
    } catch (error) {
      ElMessage.error("取消失败");
    }
  });
};

// ==================== 转交 ====================
const transferDialogVisible = ref(false);
const transferRow = ref<any>(null);
const transferTarget = ref("");
const transferReason = ref("");

const openTransferDialog = (row: any) => {
  transferRow.value = row;
  transferTarget.value = "";
  transferReason.value = "";
  transferDialogVisible.value = true;
};

const handleTransferSubmit = async () => {
  if (!transferTarget.value) {
    ElMessage.warning("请输入目标用户 userId");
    return;
  }
  submitting.value = true;
  try {
    const me = getCurrentUserInfo();
    const res = await transferTask({
      taskNo: transferRow.value.taskNo,
      targetUser: transferTarget.value,
      operator: me.userId,
      instanceId: String(transferRow.value.instanceId),
    });
    if (res.code !== 200) {
      ElMessage.error(res.message || "转交失败");
      return;
    }
    ElMessage.success("转交成功，任务已指派给目标用户");
    transferDialogVisible.value = false;
    fetchCurrent();
  } catch (error) {
    ElMessage.error("转交失败");
  } finally {
    submitting.value = false;
  }
};

// ==================== 跟踪 ====================
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
    const res = await trackByInstance({ instanceId: Number(row.instanceId) });
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

onActivated(() => {
  if (tableData.value.length === 0) {
    fetchCurrent();
  }
});
</script>

<style scoped>
.task-center-container {
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

.action-summary {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #475569;
  line-height: 1.9;
}

.hint-text {
  color: #b45309;
  font-size: 12px;
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
