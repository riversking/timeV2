<!-- src/views/role/AddRoleModal.vue -->
<template>
  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? '编辑角色' : '新增角色'"
    width="500px"
    :before-close="handleClose"
  >
    <el-form
      :model="roleForm"
      :rules="roleRules"
      ref="formRef"
      label-width="100px"
      style="margin-left: 20px; margin-right: 20px"
    >
      <el-form-item label="角色编码" prop="roleCode">
        <el-input
          v-model="roleForm.roleCode"
          placeholder="请输入角色编码"
        />
      </el-form-item>
      <el-form-item label="角色名称" prop="roleName">
        <el-input
          v-model="roleForm.roleName"
          placeholder="请输入角色名称"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, reactive } from "vue";
import {
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElSwitch,
  ElButton,
  ElMessage,
} from "element-plus";
import { saveRole, updateRole } from "@/api/role";

// 定义角色类型
interface Role {
  id?: number;
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
  (e: "save", data: Role): void;
  (e: "edit", data: Role): void;
}

const props = withDefaults(defineProps<Props>(), {
  roleData: null,
});

const emit = defineEmits<Emits>();

// 表单引用
const formRef = ref();

// 是否编辑模式
const isEdit = ref(false);

// 对话框显示状态
const dialogVisible = ref(false);

// 角色表单数据
const roleForm = reactive<Role>({
  roleName: "",
  roleCode: "",
});

// 表单验证规则
const roleRules = {
  roleName: [
    { required: true, message: "请输入角色名称", trigger: "blur" },
    { min: 2, max: 20, message: "角色名称长度应在2-20之间", trigger: "blur" },
  ],
  roleCode: [
    { required: true, message: "请输入角色编码", trigger: "blur" },
    { min: 2, max: 20, message: "角色编码长度应在2-20之间", trigger: "blur" },
  ],
};

// 监听 props 的变化
watch(
  () => props.modelValue,
  (newVal) => {
    dialogVisible.value = newVal;
  }
);

watch(
  () => props.roleData,
  (newVal) => {
    if (newVal) {
      Object.assign(roleForm, newVal);
      isEdit.value = true;
    } else {
      resetForm();
      isEdit.value = false;
    }
  }
);

// 重置表单
const resetForm = () => {
  Object.assign(roleForm, {
    roleName: "",
    roleCode: "",
    description: "",
    status: 0,
  });
};

// 关闭对话框
const handleClose = () => {
  if (formRef.value) {
    formRef.value.resetFields();
  }
  resetForm();
  emit("update:modelValue", false);
  isEdit.value = false;
};

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return;

  try {
    await formRef.value.validate();

    if (isEdit.value) {
      // 编辑角色
      const result = await updateRole(roleForm);
      if (result.code === 200) {
        ElMessage.success("角色更新成功");
        emit("edit", roleForm);
        handleClose();
      } else {
        ElMessage.error(result.message || "角色更新失败");
      }
    } else {
      // 新增角色
      const result = await saveRole(roleForm);
      if (result.code === 200) {
        ElMessage.success("角色添加成功");
        emit("save", roleForm);
        handleClose();
      } else {
        ElMessage.error(result.message || "角色添加失败");
      }
    }
  } catch (error) {
    console.error(isEdit.value ? "更新角色失败" : "添加角色失败", error);
    ElMessage.error(isEdit.value ? "更新角色失败" : "添加角色失败");
  }
};
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
