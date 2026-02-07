<template>
  <el-dialog
    v-model="dialogVisible"
    :title="isEditMode ? '编辑字典' : '新增字典'"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
    append-to-body
  >
    <el-form
      ref="dicFormRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-form-item label="字典名称" prop="dicValue">
        <el-input v-model="formData.dicValue" placeholder="请输入字典名称" />
      </el-form-item>

      <el-form-item label="字典编码" prop="dicKey">
        <el-input v-model="formData.dicKey" placeholder="请输入字典编码" />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number
          v-model="formData.sort"
          :min="0"
          :max="999"
          controls-position="right"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="formData.dicDesc"
          type="textarea"
          placeholder="请输入备注信息"
          :rows="3"
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
          {{ isEditMode ? "更新" : "保存" }}
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
  ElInputNumber,
  ElButton,
} from "element-plus";
import type { FormInstance, FormRules } from "element-plus";

// 定义字典类型
interface Dictionary {
  id?: number;
  dicKey: string;
  dicValue: string;
  sort: number;
  dicDesc?: string;
  parentId?: number | null;
}

// 定义 props 和 emits
interface Props {
  modelValue: boolean;
  dicData?: Dictionary | null;
  parentId?: number | null;
}

interface Emits {
  (e: "update:modelValue", value: boolean): void;
  (e: "save", dic: Dictionary): void;
  (e: "edit", dic: Dictionary): void;
}

const props = withDefaults(defineProps<Props>(), {
  dicData: null,
  parentId: null,
});

const emit = defineEmits<Emits>();

// 弹窗控制
const dialogVisible = ref(false);
const submitLoading = ref(false);
const isEditMode = ref(false);

// 表单引用
const dicFormRef = ref<FormInstance>();

// 表单数据
const formData = reactive<Dictionary>({
  dicKey: "",
  dicValue: "",
  sort: 0,
  dicDesc: "",
  parentId: null,
});

// 表单验证规则
const formRules: FormRules = {
  name: [
    { required: true, message: "请输入字典名称", trigger: "blur" },
    { min: 2, max: 50, message: "字典名称长度应在2-50个字符之间", trigger: "blur" },
  ],
  code: [
    { required: true, message: "请输入字典编码", trigger: "blur" },
    { pattern: /^[a-zA-Z0-9_]+$/, message: "字典编码只能包含字母、数字和下划线", trigger: "blur" },
  ],
  type: [
    { required: true, message: "请选择字典类型", trigger: "change" },
  ],
  sort: [
    { required: true, message: "请输入排序值", trigger: "blur" },
  ],
};

// 监听 props 变化
watch(
  () => props.modelValue,
  (newVal) => {
    dialogVisible.value = newVal;
  }
);

watch(
  () => props.dicData,
  (newVal) => {
    if (newVal) {
      Object.assign(formData, newVal);
      isEditMode.value = true;
    } else {
      resetForm();
      isEditMode.value = false;
    }
    // 设置父级ID
    if (props.parentId !== undefined) {
      formData.parentId = props.parentId;
    }
  }
);

// 重置表单
const resetForm = () => {
  Object.assign(formData, {
    dicKey: "",
    dicValue: "",
    sort: 0,
    dicDesc: "",
    parentId: props.parentId || null,
  });
  
  if (dicFormRef.value) {
    nextTick(() => {
      dicFormRef.value?.clearValidate();
    });
  }
};

// 关闭弹窗
const handleClose = () => {
  resetForm();
  emit("update:modelValue", false);
};

// 提交表单
const handleSubmit = async () => {
  if (!dicFormRef.value) return;
  
  try {
    await dicFormRef.value.validate();
    submitLoading.value = true;
    
    // 模拟提交延迟
    await new Promise(resolve => setTimeout(resolve, 500));
    
    if (isEditMode.value) {
      emit("edit", { ...formData });
    } else {
      emit("save", { ...formData });
    }
    handleClose();
  } catch (error) {
    console.error("表单验证失败:", error);
  } finally {
    submitLoading.value = false;
  }
};

// 初始化时设置父级ID
if (props.parentId !== undefined) {
  formData.parentId = props.parentId;
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>