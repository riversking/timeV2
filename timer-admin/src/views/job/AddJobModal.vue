<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="jobFormRef"
      :model="formData"
      :rules="rules"
      label-width="120px"
      size="default"
    >
      <el-form-item label="任务名称" prop="jobName">
        <el-input v-model="formData.jobName" placeholder="请输入任务名称" />
      </el-form-item>
      <el-form-item label="任务Bean" prop="taskName">
        <el-input v-model="formData.targetUrl" placeholder="请输入任务Bean" />
      </el-form-item>
      <el-form-item label="Cron表达式" prop="cron">
        <el-input v-model="formData.cron" placeholder="请输入Cron表达式" />
      </el-form-item>
      <el-form-item label="请求方法" prop="httpMethod">
        <el-select v-model="formData.httpMethod" placeholder="请选择请求方法">
          <el-option label="GET" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
      </el-form-item>
      
      <el-form-item label="请求头" prop="headers">
        <el-input 
          v-model="formData.headers" 
          type="textarea" 
          placeholder="JSON格式的请求头，例如：{&quot;Content-Type&quot;:&quot;application/json&quot;}"
          :rows="3"
        />
      </el-form-item>
      
      <el-form-item label="请求体" prop="body">
        <el-input 
          v-model="formData.body" 
          type="textarea" 
          placeholder="请求体内容（POST/PUT请求时使用）"
          :rows="4"
        />
      </el-form-item>
      
      <el-form-item label="描述" prop="description">
        <el-input 
          v-model="formData.description" 
          type="textarea" 
          placeholder="任务描述"
          :rows="2"
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
  ElSelect,
  ElOption,
  ElButton,
  ElMessage,
} from "element-plus";
import type { FormInstance, FormRules } from "element-plus";

// 定义任务类型
interface Job {
  id?: number;
  jobName: string;
  taskName: string;
  cron: string;
}

// 定义 props 和 emits
interface Props {
  modelValue: boolean;
  jobData?: Job | null;
}

interface Emits {
  (e: "update:modelValue", value: boolean): void;
  (e: "save", job: Job): void;
  (e: "edit", job: Job): void;
}

const props = withDefaults(defineProps<Props>(), {
  jobData: null,
});

const emit = defineEmits<Emits>();

// 弹窗控制
const dialogVisible = ref(false);
const isEdit = ref(false);
const submitLoading = ref(false);

// 表单引用
const jobFormRef = ref<FormInstance>();

// 表单数据
const formData = reactive<Job>({
  jobName: "",
  taskName: "",
  cron: "",
});

// 表单验证规则
const rules = reactive<FormRules>({
  jobName: [
    { required: true, message: "请输入任务名称", trigger: "blur" },
    { min: 2, max: 50, message: "任务名称长度在2到50个字符之间", trigger: "blur" }
  ],
  cron: [
    { required: true, message: "请输入Cron表达式", trigger: "blur" }
  ],
});

// 监听弹窗状态变化
watch(
  () => props.modelValue,
  (val) => {
    dialogVisible.value = val;
    if (val) {
      nextTick(() => {
        // 重置表单
        if (jobFormRef.value) {
          jobFormRef.value.clearValidate();
        }
        
        // 设置编辑模式和数据
        if (props.jobData) {
          isEdit.value = true;
          Object.assign(formData, props.jobData);
        } else {
          isEdit.value = false;
          resetFormData();
        }
      });
    }
  }
);

// 监听内部弹窗关闭
watch(dialogVisible, (val) => {
  if (!val) {
    emit("update:modelValue", false);
  }
});

// 重置表单数据
const resetFormData = () => {
  formData.jobName = "";
  formData.taskName = "";
  formData.cron = "";
};

// 关闭弹窗
const handleClose = () => {
  dialogVisible.value = false;
};

// 提交表单
const handleSubmit = () => {
  if (!jobFormRef.value) return;
  
  jobFormRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true;
      try {
        const submitData = { ...formData };
        
        // 如果是编辑模式，保留ID
        if (isEdit.value && props.jobData?.id) {
          submitData.id = props.jobData.id;
        }
        
        if (isEdit.value) {
          emit("edit", submitData);
        } else {
          emit("save", submitData);
        }
      } catch (error) {
        ElMessage.error("提交失败");
      } finally {
        submitLoading.value = false;
      }
    }
  });
};

// 计算对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? "编辑定时任务" : "添加定时任务";
});
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>