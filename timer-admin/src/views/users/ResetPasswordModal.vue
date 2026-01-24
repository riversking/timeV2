<template>
  <el-dialog
    v-model="dialogVisible"
    title="修改密码"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
    append-to-body
  >
    <el-form
      ref="passwordFormRef"
      :model="formData"
      :rules="formRules"
      label-width="120px"
    >
      <el-form-item label="原密码" prop="oldPassword">
        <el-input
          v-model="formData.oldPassword"
          type="password"
          placeholder="请输入原密码"
          show-password
        />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="formData.newPassword"
          type="password"
          placeholder="请输入新密码"
          show-password
        />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmNewPassword">
        <el-input
          v-model="formData.confirmNewPassword"
          type="password"
          placeholder="请再次输入新密码"
          show-password
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
          确定
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, nextTick } from "vue";
import {
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElButton,
  ElMessage,
} from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { resetPassword } from "@/api/user";

// 定义 props 和 emits
interface Props {
  modelValue: boolean;
}

interface Emits {
  (e: "update:modelValue", value: boolean): void;
  (e: "change-success"): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// 弹窗控制
const dialogVisible = ref(false);
const submitLoading = ref(false);

// 表单数据
const formData = reactive({
  oldPassword: "",
  newPassword: "",
  confirmNewPassword: "",
});

// 自定义验证器
const validatePass = (
  rule: any,
  value: string,
  callback: (error?: Error) => void
) => {
  if (value === "") {
    callback(new Error("请再次输入密码"));
  } else if (value !== formData.newPassword) {
    callback(new Error("两次输入的密码不一致"));
  } else {
    callback();
  }
};

// 表单验证规则
const formRules = reactive<FormRules>({
  oldPassword: [
    { required: true, message: "请输入原密码", trigger: "blur" },
    { min: 6, max: 20, message: "密码长度应在6-20个字符之间", trigger: "blur" },
  ],
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 6, max: 20, message: "密码长度应在6-20个字符之间", trigger: "blur" },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{6,}$/,
      message: "密码至少包含一个字母和一个数字",
      trigger: "blur",
    },
  ],
  confirmNewPassword: [
    { required: true, message: "请再次输入新密码", trigger: "blur" },
    { validator: validatePass, trigger: "blur" },
  ],
});

// 表单引用
const passwordFormRef = ref<FormInstance>();

// 监听 props 变化
watch(
  () => props.modelValue,
  (newValue) => {
    dialogVisible.value = newValue;
    if (newValue) {
      resetForm();
    }
  }
);

// 重置表单
const resetForm = () => {
  Object.assign(formData, {
    oldPassword: "",
    newPassword: "",
    confirmNewPassword: "",
  });
  nextTick(() => {
    if (passwordFormRef.value) {
      passwordFormRef.value.clearValidate();
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
  if (!passwordFormRef.value) return;

  const valid = await passwordFormRef.value.validate().catch(() => false);
  if (!valid) return;

  submitLoading.value = true;
  try {
    const result = await resetPassword({
      oldPassword: formData.oldPassword,
      newPassword: formData.newPassword,
    });

    if (result.code === 200) {
      ElMessage.success("密码修改成功");
      emit("change-success");
      handleClose();
    } else {
      ElMessage.error(result.message || "密码修改失败");
    }
  } catch (error: any) {
    console.error("修改密码失败:", error);
    ElMessage.error(error.message || "密码修改失败");
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
