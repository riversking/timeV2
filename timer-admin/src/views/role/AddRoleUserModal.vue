<!-- src/views/role/AddRoleUsersModal.vue -->
<template>
  <el-dialog
    v-model="dialogVisible"
    title="添加角色人员"
    width="800px"
    :before-close="handleClose"
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
          <el-checkbox-group v-model="selectedUserIds" class="user-list">
            <el-checkbox
              v-for="user in filteredSelectedUsers"
              :key="user.id"
              :label="user.id"
              class="user-item"
            >
              <div class="user-info">
                <span class="user-name">{{ user.username }}</span>
                <span class="user-id">({{ user.userId }})</span>
              </div>
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="filteredSelectedUsers.length === 0" class="empty-state">
            暂无已选人员
          </div>
        </div>
      </div>

      <div class="transfer-buttons">
        <el-button
          type="primary"
          :disabled="unselectedUsers.length === 0"
          @click="moveToSelected"
          icon="ArrowRight"
        >
          移入
        </el-button>
        <el-button
          :disabled="selectedUsers.length === 0"
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
          <el-checkbox-group v-model="unselectedUserIds" class="user-list">
            <el-checkbox
              v-for="user in filteredUnselectedUsers"
              :key="user.id"
              :label="user.id"
              class="user-item"
            >
              <div class="user-info">
                <span class="user-name">{{ user.username }}</span>
                <span class="user-id">({{ user.userId }})</span>
              </div>
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="filteredUnselectedUsers.length === 0" class="empty-state">
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
import {
  ElDialog,
  ElCheckboxGroup,
  ElCheckbox,
  ElInput,
  ElButton,
  ElMessage
} from "element-plus";
import { ArrowRight, ArrowLeft } from "@element-plus/icons-vue";
import { getUserPage } from "@/api/user";

// 定义用户类型
interface User {
  id: number;
  username: string;
  userId: string;
  email?: string;
}

// 定义角色类型
interface Role {
  id: number;
  roleName: string;
  roleCode: string;
}

// Props 和 Emits
interface Props {
  modelValue: boolean;
  roleData?: Role | null;
}

interface Emits {
  (e: "update:modelValue", value: boolean): void;
  (e: "confirm", data: { roleId: number; userIds: number[] }): void;
}

const props = withDefaults(defineProps<Props>(), {
  roleData: null,
});

const emit = defineEmits<Emits>();

// 对话框显示状态
const dialogVisible = ref(false);

// 所有用户数据
const allUsers = ref<User[]>([]);

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

// 监听 props 的变化
watch(
  () => props.modelValue,
  (newVal) => {
    dialogVisible.value = newVal;
    if (newVal && props.roleData) {
      loadUsers();
    }
  }
);

// 计算属性：过滤后的已选用户
const filteredSelectedUsers = computed(() => {
  if (!selectedFilter.value) return selectedUsers.value;
  return selectedUsers.value.filter(user =>
    user.username.toLowerCase().includes(selectedFilter.value.toLowerCase()) ||
    user.userId.toLowerCase().includes(selectedFilter.value.toLowerCase())
  );
});

// 计算属性：过滤后的未选用户
const filteredUnselectedUsers = computed(() => {
  if (!unselectedFilter.value) return unselectedUsers.value;
  return unselectedUsers.value.filter(user =>
    user.username.toLowerCase().includes(unselectedFilter.value.toLowerCase()) ||
    user.userId.toLowerCase().includes(unselectedFilter.value.toLowerCase())
  );
});

// 加载用户数据
const loadUsers = async () => {
  try {
    // 获取所有用户
    const response = await getUserPage({
      currentPage: 1,
      pageSize: 1000, // 获取所有用户
    });

    if (response.code === 200) {
      allUsers.value = response.data.users || [];

      // 模拟获取角色已分配的用户（在实际项目中，这应该是从后端API获取的数据）
      // 这里我们随机选择一些用户作为已分配用户
      const assignedUserIds = [allUsers.value[0]?.id, allUsers.value[2]?.id].filter(Boolean);
      
      selectedUsers.value = allUsers.value.filter(user => 
        assignedUserIds.includes(user.id)
      );
      unselectedUsers.value = allUsers.value.filter(user => 
        !assignedUserIds.includes(user.id)
      );

      // 初始化已选用户的 ID 数组
      selectedUserIds.value = selectedUsers.value.map(user => user.id);
      unselectedUserIds.value = [];
    } else {
      ElMessage.error(response.message || "获取用户数据失败");
    }
  } catch (error) {
    console.error("获取用户数据失败:", error);
    ElMessage.error("获取用户数据失败");
  }
};

// 将未选用户移入已选列表
const moveToSelected = () => {
  if (unselectedUserIds.value.length === 0) return;

  // 从未选列表中移除选中的用户
  const movedUsers = unselectedUsers.value.filter(user => 
    unselectedUserIds.value.includes(user.id)
  );
  
  unselectedUsers.value = unselectedUsers.value.filter(user => 
    !unselectedUserIds.value.includes(user.id)
  );
  
  // 添加到已选列表
  selectedUsers.value = [...selectedUsers.value, ...movedUsers];
  
  // 更新 ID 数组
  selectedUserIds.value = [...selectedUserIds.value, ...unselectedUserIds.value];
  unselectedUserIds.value = [];

  ElMessage.success(`成功添加 ${movedUsers.length} 名用户`);
};

// 将已选用户移出到未选列表
const moveToUnselected = () => {
  if (selectedUserIds.value.length === 0) return;

  // 从已选列表中移除选中的用户
  const movedUsers = selectedUsers.value.filter(user => 
    selectedUserIds.value.includes(user.id)
  );
  
  selectedUsers.value = selectedUsers.value.filter(user => 
    !selectedUserIds.value.includes(user.id)
  );
  
  // 添加到未选列表
  unselectedUsers.value = [...unselectedUsers.value, ...movedUsers];
  
  // 更新 ID 数组
  unselectedUserIds.value = [...unselectedUserIds.value, ...selectedUserIds.value];
  selectedUserIds.value = [];

  ElMessage.success(`成功移出 ${movedUsers.length} 名用户`);
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
  if (!props.roleData) {
    ElMessage.error("角色数据不完整");
    return;
  }

  // 发送分配请求到后端
  // 这里应该调用实际的API来分配用户到角色
  try {
    // 模拟API调用
    // const result = await assignUsersToRole({
    //   roleId: props.roleData.id,
    //   userIds: selectedUserIds.value
    // });
    
    // if (result.code === 200) {
      ElMessage.success("用户分配成功");
      emit("confirm", {
        roleId: props.roleData.id,
        userIds: selectedUserIds.value
      });
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
  height: 400px;
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

.user-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.user-item {
  padding: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  transition: all 0.3s;
}

.user-item:hover {
  background-color: #f5f7fa;
}

.user-info {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-weight: 500;
}

.user-id {
  font-size: 12px;
  color: #909399;
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
</style>