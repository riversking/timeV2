<template>
  <el-drawer
    v-model="showPermissionDrawer"
    title="权限配置"
    direction="rtl"
    size="60%"
    @close="handleClose"
    append-to-body
  >
    <div class="permission-config-container">
      <div class="header">
        <h1 class="title">权限配置</h1>
        <div class="header-actions">
          <el-button type="primary" @click="handleSave">
            <el-icon><Finished /></el-icon> 保存权限
          </el-button>
          <el-button @click="handleCancel">
            <el-icon><Close /></el-icon> 取消
          </el-button>
        </div>
      </div>

      <div class="content">
        <el-card class="box-card">
          <template #header>
            <div class="card-header">
              <span>角色：{{ roleName }}</span>
            </div>
          </template>

          <el-row :gutter="20">
            <el-col :span="8">
              <div class="tree-header">
                <h3>菜单权限</h3>
                <div class="tree-controls">
                  <el-button size="small" @click="setCheckedAll()"
                    >全选</el-button
                  >
                  <el-button size="small" @click="setCheckedNone()"
                    >清空</el-button
                  >
                </div>
              </div>
              <el-tree
                v-if="showTree"
                ref="treeRef"
                :data="menuTreeData"
                show-checkbox
                node-key="menuCode"
                :props="treeProps"
                default-expand-all
                :check-strictly="true"
                :default-checked-keys="checkedKeys"
                @check="onTreeNodeCheck"
              />
            </el-col>
          </el-row>
        </el-card>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from "vue";
import {
  ElTree,
  ElCard,
  ElRow,
  ElCol,
  ElDrawer,
  ElButton,
  ElMessage,
  ElEmpty,
} from "element-plus";
import { Finished, Close } from "@element-plus/icons-vue";
import { getMenuTree, getRoleMenu, saveRoleMenu } from "@/api/menu";
import { MenuTreeVO } from "@/proto";

// 定义 props
interface Props {
  modelValue: boolean;
  roleCode?: string;
  roleName: string;
}

const props = withDefaults(defineProps<Props>(), {
  roleCode: "",
  roleName: "",
});

// 定义 emits
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "update:roleCode", value: string): void;
  (e: "cancel", value: []): void;
}>();

// 树形控件引用
const treeRef = ref();

// 权限树相关数据
const menuTreeData = ref<MenuTreeVO[]>([]);
const checkedKeys = ref<string[]>([]);
const roleCode = ref("");
const showPermissionDrawer = ref(false);
const checkedNodes = ref<MenuTreeVO[]>([]);
const showTree = ref(true); // 控制树形组件重新渲染

// 树形控件属性配置
const treeProps = {
  children: "children",
  label: "menuName",
  disabled: "disabled",
};

watch(
  () => props.modelValue,
  async (newVal) => {
    showPermissionDrawer.value = newVal;
    if (!newVal) {
      // 关闭时重置数据
      treeRef.value?.setCheckedKeys([]);
      checkedKeys.value = [];
      checkedNodes.value = [];
      menuTreeData.value = [];
      return;
    }
    // 打开时重新加载数据
    await nextTick();
    await fetchMenuTree();
    await nextTick();
  }
);

watch(
  () => props.roleCode,
  async (newVal) => {
    console.log("newVal", newVal);
    if (!newVal) {
      return;
    }
    roleCode.value = newVal;
    await fetchRoleMenu();
    await nextTick();
    // 重新渲染树形组件以确保状态正确
    showTree.value = false;
    await nextTick();
    showTree.value = true;
  }
);

// 获取所有菜单权限
const fetchMenuTree = async () => {
  try {
    const response = await getMenuTree({ key: "" });
    if (response.code === 200) {
      menuTreeData.value = response.data;
      console.log("Fetched menu tree data:", menuTreeData.value);
    } else {
      ElMessage.error(response.message || "获取菜单权限失败");
    }
  } catch (error) {
    console.error("获取菜单权限失败:", error);
    ElMessage.error("获取菜单权限失败");
  }
};

// 获取角色已有权限
const fetchRoleMenu = async () => {
  try {
    const response = await getRoleMenu({ roleCode: roleCode.value });
    if (response.code === 200) {
      console.log("response.data.checkedMenu", response.data.checkedMenu);
      checkedKeys.value = response.data.checkedMenu || [];
      // 等待树形组件渲染完成后更新选中节点
      await nextTick();
    } else {
      ElMessage.error(response.message || "获取角色权限失败");
    }
    console.log("获取角色权限成功:", checkedKeys.value);
  } catch (error) {
    console.error("获取角色权限失败:", error);
    ElMessage.error("获取角色权限失败");
  }
};

// 当节点选中状态改变时触发
const onTreeNodeCheck = (data: any, checkedInfo: any) => {
  console.log("Checked nodes changed:", checkedInfo);
  console.log("Currently checked keys:", data);
  updateCheckedNodes();
};

// 更新已选节点列表
const updateCheckedNodes = () => {
  if (!treeRef.value) return;
  const checkedNodesData = treeRef.value.getCheckedNodes(false, false);
  checkedNodes.value = checkedNodesData;
  // 更新选中的 keys
  checkedKeys.value = treeRef.value.getCheckedKeys(false);
};

// 全选
const setCheckedAll = () => {
  if (!treeRef.value) return;
  // 收集所有节点的 menuCode
  const allNodeIds: string[] = [];
  const collectNodeIds = (nodes: MenuTreeVO[]) => {
    nodes.forEach((node) => {
      allNodeIds.push(node.menuCode);
      if (node.children && node.children.length) {
        collectNodeIds(node.children);
      }
    });
  };
  collectNodeIds(menuTreeData.value);
  // 设置所有节点为选中状态
  treeRef.value.setCheckedKeys(allNodeIds);
  updateCheckedNodes();
};

// 清空选择
const setCheckedNone = () => {
  if (!treeRef.value) return;
  treeRef.value.setCheckedKeys([]);
  updateCheckedNodes();
};

// 保存权限
const handleSave = async () => {
  try {
    const permissions = checkedKeys.value;
    console.log("Selected permissions:", permissions);
    const response = await saveRoleMenu({
      roleCode: roleCode.value,
      menuCodes: permissions,
    });
    if (response.code !== 200) {
      ElMessage.error(response.message || "保存权限失败");
      return;
    }
    ElMessage.success("权限保存成功");
    handleClose();
  } catch (error) {
    console.error("保存权限失败:", error);
    ElMessage.error("权限保存失败");
  }
};

// 取消操作
const handleCancel = () => {
  treeRef.value?.setCheckedKeys([]);
  checkedNodes.value = [];
  checkedKeys.value = [];
  roleCode.value = "";
  emit("cancel", []);
};
// 处理关闭
const handleClose = () => {
  emit("update:modelValue", false);
  nextTick(() => {
    treeRef.value?.setCheckedKeys([]);
    checkedKeys.value = [];
    checkedNodes.value = [];
    menuTreeData.value = [];
  });
};
</script>

<style scoped>
.permission-config-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100%;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e0e0e0;
}

.title {
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
  margin: 0;
}

.content {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tree-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.tree-controls {
  display: flex;
  gap: 8px;
}

.permissions-selected {
  background: #fafafa;
  padding: 20px;
  border-radius: 4px;
  min-height: 400px;
}

.selected-list {
  margin-top: 15px;
  min-height: 350px;
}
</style>
