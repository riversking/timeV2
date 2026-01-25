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
          <span>已选人员 ({{ selectedTotal }})</span>
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
            max-height="350"
            @selection-change="handleSelectedChange"
            ref="selectedTableRef"
            v-loading="selectedLoading"
          >
            <el-table-column type="selection" width="55" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="userId" label="用户ID" />
          </el-table>
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="selectedCurrentPage"
              v-model:page-size="selectedPageSize"
              :total="selectedTotal"
              :page-sizes="[5, 10, 20]"
              layout="total, prev, pager, next"
              @size-change="handleSelectedSizeChange"
              @current-change="handleSelectedCurrentChange"
            />
          </div>
        </div>
      </div>

      <div class="transfer-buttons">
        <el-button
          type="primary"
          :disabled="unselectedTotal === 0"
          @click="moveToSelected"
          :icon="ArrowLeft"
        >
          移入
        </el-button>
        <el-button
          :disabled="selectedTotal === 0"
          @click="moveToUnselected"
          :icon="ArrowRight"
        >
          移出
        </el-button>
      </div>

      <div class="transfer-panel">
        <div class="panel-header">
          <span>未选人员 ({{ unselectedTotal }})</span>
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
            max-height="350"
            @selection-change="handleUnselectedChange"
            ref="unselectedTableRef"
            v-loading="unselectedLoading"
          >
            <el-table-column type="selection" width="55" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="userId" label="用户ID" />
          </el-table>
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="unselectedCurrentPage"
              v-model:page-size="unselectedPageSize"
              :total="unselectedTotal"
              :page-sizes="[5, 10, 20]"
              layout="total, prev, pager, next"
              @size-change="handleUnselectedSizeChange"
              @current-change="handleUnselectedCurrentChange"
            />
          </div>
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
import { ref, watch } from "vue";
import { ElDialog, ElTable, ElInput, ElButton, ElMessage } from "element-plus";
import { ArrowRight, ArrowLeft } from "@element-plus/icons-vue";
import { getUserPage } from "@/api/user";
import { getRoleUserPage, saveUserRole, removeUserRole } from "@/api/role";

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
  roleCode?: string;
}

interface Emits {
  (e: "update:modelValue", value: boolean): void;
}

const props = withDefaults(defineProps<Props>(), {
  roleUserData: null,
  roleCode: "",
});

const emit = defineEmits<Emits>();

// 对话框显示状态
const dialogVisible = ref(false);
const roleCode = ref("");

// 已选用户数据
const selectedUsers = ref<User[]>([]);

// 未选用户数据
const unselectedUsers = ref<User[]>([]);

// 已选用户的 ID 数组
const selectedUserIds = ref<string[]>([]);

// 未选用户的 ID 数组（临时存储待移入的用户）
const unselectedUserIds = ref<string[]>([]);

// 已选人员搜索过滤
const selectedFilter = ref("");

// 未选人员搜索过滤
const unselectedFilter = ref("");

// 引用表格实例
const selectedTableRef = ref();
const unselectedTableRef = ref();
// 已选用户分页参数
const selectedCurrentPage = ref(1);
const selectedPageSize = ref(10);
// 未选用户分页参数
const unselectedCurrentPage = ref(1);
const unselectedPageSize = ref(10);
const selectedTotal = ref(0);
const unselectedTotal = ref(0);
const unselectedLoading = ref(false);
const selectedLoading = ref(false);

// 监听 props 的变化
watch(
  () => props.modelValue,
  (newVal) => {
    dialogVisible.value = newVal;
    if (newVal) {
      loadUnselectedUsers();
    }
  }
);

watch(
  () => props.roleCode,
  (newVal) => {
    console.log("newVal", newVal);
    if (!newVal) return;
    roleCode.value = newVal;
    fetchSelectedUsers();
  }
);

// 加载用户数据
const loadUnselectedUsers = async () => {
  unselectedLoading.value = true;
  try {
    // 获取所有用户
    const response = await getUserPage({
      currentPage: unselectedCurrentPage.value,
      pageSize: unselectedPageSize.value, // 获取所有用户
    });

    if (response.code !== 200) {
      ElMessage.error(response.message || "获取用户数据失败");
    }
    unselectedUsers.value = response.data.users || [];
    unselectedTotal.value = response.data.total || 0;
  } catch (error) {
    console.error("获取用户数据失败:", error);
    ElMessage.error("获取用户数据失败");
  } finally {
    unselectedLoading.value = false;
  }
};

const fetchSelectedUsers = async () => {
  selectedLoading.value = true; 
  try {
    const res = await getRoleUserPage({
      currentPage: selectedCurrentPage.value,
      pageSize: selectedPageSize.value, // 获取所有用户
      roleCode: roleCode.value,
    });
    if (res.code !== 200) {
      ElMessage.error(res.message || "获取已选用户数据失败");
      return;
    }
    selectedUsers.value = res.data.users || [];
    selectedTotal.value = res.data.total || 0;
  } catch (error) {
    console.error("获取已选用户数据失败:", error);
    ElMessage.error("获取已选用户数据失败");
  } finally {
    selectedLoading.value = false;
  }
};

// 处理已选表格的选择变化
const handleSelectedChange = (selection: User[]) => {
  selectedUserIds.value = selection.map((user) => user.userId);
};

// 处理未选表格的选择变化
const handleUnselectedChange = (selection: User[]) => {
  unselectedUserIds.value = selection.map((user) => user.userId);
};

// 将未选用户移入已选列表
const moveToSelected = async () => {
  try {
    const unselected = unselectedUserIds.value;
    if (unselected.length === 0) {
      ElMessage.warning("请选择要添加的用户");
      return;
    }
    const data = {
      roleCode: roleCode.value,
      userIds: unselected,
    };
    const res = await saveUserRole(data);
    if (res.code !== 200) {
      ElMessage.error(res.message);
      return;
    }
    ElMessage.success("添加用户成功");
    await fetchSelectedUsers();
  } catch (error) {
    console.error("添加用户失败:", error);
    ElMessage.error("添加用户失败");
  } finally {
    roleCode.value = "";
  }
};

// 将已选用户移出到未选列表
const moveToUnselected = async () => {
  try {
    const selected = selectedUserIds.value;
    if (selected.length === 0) {
      ElMessage.warning("请选择要取消的用户");
      return;
    }
    const data = {
      roleCode: roleCode.value,
      userIds: selected,
    };
    const res = await removeUserRole(data);
    if (res.code !== 200) {
      ElMessage.error(res.message);
      return;
    }
    ElMessage.success("取消用户成功");
    await fetchSelectedUsers();
  } catch (error) {
    console.error("取消用户失败:", error);
    ElMessage.error("取消用户失败");
  } finally {
    roleCode.value = "";
  }
};

// 已选用户当前页变化
const handleSelectedCurrentChange = (page: number) => {
  selectedCurrentPage.value = page;
};

// 未选用户分页大小变化
const handleUnselectedSizeChange = (size: number) => {
  unselectedPageSize.value = size;
  unselectedCurrentPage.value = 1;
  loadUnselectedUsers();
};

// 未选用户当前页变化
const handleUnselectedCurrentChange = (page: number) => {
  unselectedCurrentPage.value = page;
  loadUnselectedUsers();
};

// 已选用户分页大小变化
const handleSelectedSizeChange = (size: number) => {
  selectedPageSize.value = size;
  selectedCurrentPage.value = 1;
};

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
  height: 500px;
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
  display: flex;
  flex-direction: column;
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
  padding: 0 20px;
}

.transfer-buttons .el-button {
  display: block;
  margin: 5px 0;
  width: 80px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.pagination-container {
  margin-top: 10px;
  display: flex;
  justify-content: right;
}

:deep(.el-dialog__wrapper) {
  pointer-events: auto;
}

:deep(.el-dialog) {
  margin-top: 6vh !important;
  pointer-events: auto;
}
</style>
