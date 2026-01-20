<template>
  <el-dialog
    v-model="dialogVisible"
    title="添加角色人员"
    width="1000px"
    :before-close="handleClose"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
  >
    <div class="transfer-container">
      <div class="transfer-panel">
        <div class="panel-header">
          <span>已选人员 ({{ selectedUsers.length }})</span>
          <el-input
            v-model="selectedFilter"
            placeholder="搜索已选人员"
            size="small"
            clearable
          />
        </div>
        <div class="panel-body">
          <el-table
            :data="selectedUsers"
            style="width: 100%"
            max-height="250"
            @selection-change="handleSelectedChange"
            ref="selectedTableRef"
          >
            <el-table-column type="selection" width="55" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="userId" label="用户ID" />
          </el-table>
          <div v-if="selectedUsers.length === 0" class="empty-state">
            暂无已选人员
          </div>
        </div>
      </div>

      <div class="transfer-buttons">
        <el-button
          type="primary"
          :disabled="unselectedUserIds.length === 0"
          @click="moveToSelected"
          icon="ArrowRight"
        >
          移入
        </el-button>
        <el-button
          :disabled="selectedUserIds.length === 0"
          @click="moveToUnselected"
          icon="ArrowLeft"
        >
          移出
        </el-button>
      </div>

      <div class="transfer-panel">
        <div class="panel-header">
          <span>未选人员 ({{ unselectedUsers.length }})</span>
          <el-input
            v-model="unselectedFilter"
            placeholder="搜索未选人员"
            size="small"
            clearable
          />
        </div>
        <div class="panel-body">
          <el-table
            :data="unselectedUsers"
            style="width: 100%"
            max-height="250"
            @selection-change="handleUnselectedChange"
            ref="unselectedTableRef"
          >
            <el-table-column type="selection" width="55" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="userId" label="用户ID" />
          </el-table>
          <div v-if="unselectedUsers.length === 0" class="empty-state">
            暂无更多人员可选
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { ElDialog, ElTable, ElInput, ElButton, ElMessage } from "element-plus";
import { ArrowRight, ArrowLeft } from "@element-plus/icons-vue";
import { getUserPage } from "@/api/user";

// 定义用户类型
interface User {
  id: number;
  username: string;
  userId: string;
}

// 定义角色类型
interface RoleUser {
  id: number;
  username: string;
  userId: string;
}

// Props 和 Emits
interface Props {
  modelValue: boolean;
  roleUserData?: RoleUser | null;
}

interface Emits {
  (e: "update:modelValue", value: boolean): void;
  (e: "confirm", data: { roleId: number; userIds: number[] }): void;
}

const props = withDefaults(defineProps<Props>(), {
  roleUserData: null,
});

const emit = defineEmits<Emits>();

// 对话框显示状态
const dialogVisible = ref(false);

// 已选用户数据
const selectedUsers = ref<User[]>([]);

// 未选用户数据
const unselectedUsers = ref<User[]>([]);

// 已选用户的 ID 数组
const selectedUserIds = ref<number[]>([]);

// 未选用户的 ID 数组（临时存储待移入的用户）
const unselectedUserIds = ref<number[]>([]);

// 已选人员搜索过滤
const selectedFilter = ref("");

// 未选人员搜索过滤
const unselectedFilter = ref("");

// 引用表格实例
const selectedTableRef = ref();
const unselectedTableRef = ref();

// 监听 props 的变化
watch(
  () => props.modelValue,
  (newVal) => {
    dialogVisible.value = newVal;
    if (newVal) {
      loadUsers();
    }
  }
);

// 加载用户数据
const loadUsers = async () => {
  try {
    // 获取所有用户
    const response = await getUserPage({
      currentPage: 1,
      pageSize: 1000, // 获取所有用户
    });

    if (response.code !== 200) {
      ElMessage.error(response.message || "获取用户数据失败");
    }
    unselectedUsers.value = response.data.users || [];
  } catch (error) {
    console.error("获取用户数据失败:", error);
    ElMessage.error("获取用户数据失败");
  }
};

// 处理已选表格的选择变化
const handleSelectedChange = (selection: User[]) => {
  selectedUserIds.value = selection.map((user) => user.id);
};

// 处理未选表格的选择变化
const handleUnselectedChange = (selection: User[]) => {
  unselectedUserIds.value = selection.map((user) => user.id);
};

// 将未选用户移入已选列表
const moveToSelected = () => {};

// 将已选用户移出到未选列表
const moveToUnselected = () => {};

// 关闭对话框
const handleClose = () => {
  selectedFilter.value = "";
  unselectedFilter.value = "";
  unselectedUserIds.value = [];
  selectedUserIds.value = [];
  emit("update:modelValue", false);
};

// 确认分配
const handleConfirm = async () => {
  if (!props.roleUserData) {
    ElMessage.error("角色数据不完整");
    return;
  }

  // 发送分配请求到后端
  // 这里应该调用实际的API来分配用户到角色
  try {
    // 模拟API调用
    handleClose();
    // } else {
    //   ElMessage.error(result.message || "用户分配失败");
    // }
  } catch (error) {
    console.error("分配用户失败:", error);
    ElMessage.error("用户分配失败");
  }
};
</script>

<style scoped>
.transfer-container {
  display: flex;
  height: 350px;
}

.transfer-panel {
  flex: 1;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 10px;
  background-color: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header .el-input {
  width: 150px;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.empty-state {
  text-align: center;
  color: #909399;
  padding: 20px 0;
  font-style: italic;
}

.transfer-buttons {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
}

.transfer-buttons .el-button {
  display: block;
  margin: 5px 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
:deep(.el-dialog__wrapper) {
  pointer-events: auto;
}

:deep(.el-dialog) {
  margin-top: 6vh !important;
  pointer-events: auto;
}
</style>
