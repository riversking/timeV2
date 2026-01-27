<template>
  <el-dialog
    v-model="dialogVisible"
    :title="isEditMode ? '编辑菜单' : '添加菜单'"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
    append-to-body
  >
    <el-form
      ref="menuFormRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-form-item label="菜单名称" prop="menuName">
        <el-input v-model="formData.menuName" placeholder="请输入菜单名称" />
      </el-form-item>

      <el-form-item label="菜单编码" prop="menuCode">
        <el-input v-model="formData.menuCode" placeholder="请输入菜单编码" />
      </el-form-item>

      <el-form-item label="路由路径" prop="routePath">
        <el-input v-model="formData.routePath" placeholder="请输入路由路径" />
      </el-form-item>
      <el-form-item label="排序" prop="sortOrder">
        <el-input-number
          v-model="formData.sortOrder"
          :min="0"
          :max="999"
          controls-position="right"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="图标">
        <el-input
          v-model="formData.icon"
          placeholder="请输入图标类名，如：el-icon-menu"
        />
      </el-form-item>
      <el-form-item label="组件路径">
        <el-input v-model="formData.component" placeholder="请输入组件路径" />
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
import { ref, reactive, watch, nextTick, computed } from "vue";
import {
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElButton,
  ElMessage,
} from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { MenuTreeVO } from "@/proto";

// 定义 props 和 emits
interface Props {
  modelValue: boolean;
  menuData?: any;
}

interface Menu {
  id?: number;
  menuCode: string;
  menuName: string;
  routePath: string;
  sortOrder: number;
  parentId?: number;
  icon?: string;
  component?: string;
}

interface Emits {
  (e: "update:modelValue", value: boolean): void;
  (e: "save", menu: Menu): void;
  (e: "edit", menu: Menu): void;
}

const props = withDefaults(defineProps<Props>(), {
  menuData: () => ({}),
});

const emit = defineEmits<Emits>();

// 弹窗控制
const dialogVisible = ref(false);
const submitLoading = ref(false);

// 表单数据
const formData = reactive<Menu>({
  id: 0,
  menuCode: "",
  menuName: "",
  parentId: 0,
  routePath: "",
  sortOrder: 0,
  icon: "",
  component: "",
});

// 菜单树数据
const menuTreeData = ref<MenuTreeVO[]>([]);
const treeProps = {
  value: "menuCode",
  label: "menuName",
  children: "children",
};

// 表单验证规则
const formRules = reactive<FormRules>({
  menuName: [
    { required: true, message: "请输入菜单名称", trigger: "blur" },
    { min: 1, max: 50, message: "菜单名称长度应在1-50之间", trigger: "blur" },
  ],
  menuCode: [
    { required: true, message: "请输入菜单编码", trigger: "blur" },
    { min: 1, max: 50, message: "菜单编码长度应在1-50之间", trigger: "blur" },
  ],
  routePath: [{ required: true, message: "请输入路由路径", trigger: "blur" }],
  orderNum: [
    { required: true, message: "请输入排序号", trigger: "blur" },
    { type: "number", message: "排序号必须为数字", trigger: "blur" },
  ],
});

// 表单引用
const menuFormRef = ref<FormInstance>();

// 是否为编辑模式
const isEditMode = computed(
  () => !!props.menuData && !!props.menuData.menuCode
);

// 监听 props 变化
watch(
  () => props.modelValue,
  async (newVal) => {
    dialogVisible.value = newVal;
    if (newVal) {
      await nextTick();
      resetForm();
      // 如果是编辑模式，填充表单数据
      if (isEditMode.value) {
        Object.assign(formData, props.menuData);
      }
    }
  }
);

// 重置表单
const resetForm = () => {
  Object.assign(formData, {
    id: undefined,
    menuCode: "",
    menuName: "",
    routePath: "",
    sortOrder: 0,
    icon: "",
    component: "",
  });
  nextTick(() => {
    if (menuFormRef.value) {
      menuFormRef.value.clearValidate();
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
  if (!menuFormRef.value) return;
  const valid = await menuFormRef.value.validate().catch(() => false);
  if (!valid) return;
  submitLoading.value = true;
  try {
    if (!isEditMode.value) {
      emit("save", formData);
    } else {
      emit("edit", formData);
    }
  } catch (error: any) {
    console.error(isEditMode.value ? "更新菜单失败:" : "保存菜单失败:", error);
    ElMessage.error(
      error.message || (isEditMode.value ? "菜单更新失败" : "菜单保存失败")
    );
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
