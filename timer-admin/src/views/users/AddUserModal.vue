<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="userFormRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
    >
     <el-form-item label="账号" prop="userId">
        <el-input v-model="formData.userId" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="用户名" prop="username">
        <el-input v-model="formData.username" placeholder="请输入用户名" />
      </el-form-item>
      <el-form-item label="邮箱" prop="mail">
        <el-input v-model="formData.mail" placeholder="请输入邮箱" />
      </el-form-item>
      <el-form-item label="电话" prop="phone">
        <el-input v-model="formData.phone" placeholder="请输入电话号码" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="formData.password"
          type="password"
          placeholder="请输入密码"
          v-if="!isEdit"
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword" v-if="!isEdit">
        <el-input
          v-model="formData.confirmPassword"
          type="password"
          placeholder="请再次输入密码"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button
          type="primary"
          @click="handleSubmit"
          :loading="submitLoading"
        >
          {{ isEdit ? "更新" : "创建" }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, nextTick, computed } from "vue";
import {
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElButton,
  ElMessage,
} from "element-plus";
import type { FormInstance, FormRules } from "element-plus";

// 定义用户类型
interface User {
  id?: number;
  username: string;
  userId: string;
  mail: string;
  phone: string;
  password?: string;
  confirmPassword?: string;
  isEnable?: number;
}

// 定义旧的用户类型，用于兼容现有数据
interface OldUser {
  id: number;
  username: string;
  userId: string;
  email: string;
  role: string;
  status: number;
}

// 统一用户类型接口
type CompatibleUser = User | OldUser;

// 定义 props 和 emits
interface Props {
  modelValue: boolean;
  userData?: CompatibleUser | null;
}

interface Emits {
  (e: "update:modelValue", value: boolean): void;
  (e: "save", user: User): void;
}

const props = withDefaults(defineProps<Props>(), {
  userData: null,
});

const emit = defineEmits<Emits>();

// 弹窗控制
const dialogVisible = ref(false);
const isEdit = ref(false);
const submitLoading = ref(false);

// 表单数据
const formData = reactive<User>({
  id: undefined,
  username: "",
  userId: "",
  mail: "",
  phone: "",
  password: "",
  confirmPassword: "",
  isEnable: 1,
});

// 表单验证规则
const rules = reactive<FormRules<User>>({
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    {
      min: 3,
      max: 20,
      message: "用户名长度应在3-20个字符之间",
      trigger: "blur",
    },
  ],
  userId: [{ required: true, message: "请输入账号", trigger: "blur" }],
  mail: [
    { required: true, message: "请输入邮箱", trigger: "blur" },
    { type: "email", message: "请输入正确的邮箱地址", trigger: "blur" },
  ],
  phone: [
    { required: true, message: "请输入电话号码", trigger: "blur" },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: "请输入正确的手机号码",
      trigger: "blur",
    },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, max: 20, message: "密码长度应在6-20个字符之间", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "请再次输入密码", trigger: "blur" },
    {
      validator: (
        rule: any,
        value: string,
        callback: (error?: Error) => void
      ) => {
        if (value !== formData.password) {
          callback(new Error("两次输入的密码不一致"));
        } else {
          callback();
        }
      },
      trigger: "blur",
    },
  ],
});

// 表单引用
const userFormRef = ref();

// 获取对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? "编辑用户" : "新增用户";
});

// 将旧的用户格式转换为新的用户格式
const convertOldUserFormat = (oldUser: OldUser): User => {
  return {
    id: oldUser.id,
    username: oldUser.username, // 将 name 映射到 username
    userId: oldUser.userId, // 将 name 映射到 nickname
    mail: oldUser.email, // 将 email 映射到 mail
    phone: "", // 旧格式中没有电话字段
    password: undefined,
    confirmPassword: undefined,
    isEnable: oldUser.status, // 将 status 映射到 isEnable
  };
};

// 监听 props 变化
watch(
  () => props.modelValue,
  (newValue) => {
    dialogVisible.value = newValue;
    if (newValue) {
      resetForm();
      if (props.userData) {
        // 检查是否是旧格式的用户数据
        isEdit.value = true;
        if ("name" in props.userData) {
          // 是旧格式的用户数据
          const convertedUser = convertOldUserFormat(props.userData as OldUser);
          Object.assign(formData, convertedUser);
        } else {
          // 是新格式的用户数据
          Object.assign(formData, props.userData);
        }
      } else {
        // 新增模式
        isEdit.value = false;
        resetForm();
      }
    }
  }
);

// 重置表单
const resetForm = () => {
  Object.assign(formData, {
    id: undefined,
    username: "",
    userId: "",
    nickname: "",
    mail: "",
    phone: "",
    password: "",
    confirmPassword: "",
    isEnable: 1,
  });
  nextTick(() => {
    if (userFormRef.value) {
      userFormRef.value.clearValidate();
    }
  });
};

// 处理关闭
const handleClose = () => {
  emit("update:modelValue", false);
  nextTick(() => {
    resetForm();
  });
};

// 提交表单
const handleSubmit = async () => {
  if (!userFormRef.value) return;

  const valid = await userFormRef.value.validate().catch(() => false);
  if (!valid) return;

  submitLoading.value = true;
  try {
    // 准备提交数据
    const submitData = { ...formData };
    if (!isEdit.value) {
      // 新增时不传入 confirmPassword 字段
      delete submitData.confirmPassword;
    } else {
      // 编辑时，如果没有更改密码则不传递密码字段
      if (!submitData.password) {
        delete submitData.password;
      }
      delete submitData.confirmPassword;
    }
    // 触发保存事件
    emit("save", submitData);
    handleClose();
  } catch (error) {
    console.error(isEdit.value ? "更新用户失败" : "创建用户失败", error);
    ElMessage.error(isEdit.value ? "更新用户失败" : "创建用户失败");
  } finally {
    submitLoading.value = false;
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
