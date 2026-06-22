<template>
  <el-dialog
    v-model="visible"
    title="创建群组"
    width="420px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-input
      v-model="groupName"
      placeholder="请输入群名称"
      style="margin-bottom: 12px"
    />
    <div class="select-friends-title">
      选择好友
      <span class="select-count">(已选 {{ selectedIds.size }} 人)</span>
    </div>
    <el-scrollbar max-height="300px">
      <div
        v-for="friend in friendList"
        :key="friend.friendId"
        class="select-friend-item"
        :class="{ selected: selectedIds.has(friend.friendId) }"
        @click="toggleSelect(friend.friendId)"
      >
        <el-checkbox :model-value="selectedIds.has(friend.friendId)" />
        <el-avatar size="small" :src="friend.friendAvatar">
          {{ friend.friendName?.charAt(0) }}
        </el-avatar>
        <span>{{ friend.friendName }}</span>
      </div>
      <div v-if="friendList.length === 0" class="empty-tip">
        暂无好友可选
      </div>
    </el-scrollbar>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :disabled="!groupName.trim() || selectedIds.size === 0"
        :loading="submitting"
        @click="handleSubmit"
      >
        创建
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";

interface Friend {
  friendId: string;
  friendName: string;
  friendAvatar?: string;
}

const props = defineProps<{
  modelValue: boolean;
  friendList: Friend[];
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  "create-group": [data: { name: string; userIds: string[] }];
}>();

const visible = ref(false);
const groupName = ref("");
const selectedIds = ref<Set<string>>(new Set());
const submitting = ref(false);

watch(
  () => props.modelValue,
  (val) => {
    visible.value = val;
    if (val) {
      groupName.value = "";
      selectedIds.value = new Set();
      submitting.value = false;
    }
  },
);

watch(visible, (val) => {
  emit("update:modelValue", val);
});

const toggleSelect = (friendId: string) => {
  const next = new Set(selectedIds.value);
  next.has(friendId) ? next.delete(friendId) : next.add(friendId);
  selectedIds.value = next;
};

const handleSubmit = () => {
  if (!groupName.value.trim() || selectedIds.value.size === 0) return;
  submitting.value = true;
  emit("create-group", {
    name: groupName.value.trim(),
    userIds: [...selectedIds.value],
  });
};

const handleClose = () => {
  visible.value = false;
};

defineExpose({ setSubmitting: (val: boolean) => { submitting.value = val; } });
</script>

<style scoped>
.select-friends-title {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 8px;
}
.select-count {
  color: #909399;
  font-size: 12px;
}
.select-friend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 4px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
}
.select-friend-item:hover {
  background: #f5f7fa;
}
.select-friend-item.selected {
  background: #ecf5ff;
}
.empty-tip {
  text-align: center;
  padding: 15px;
  color: #909399;
  font-size: 12px;
}
</style>