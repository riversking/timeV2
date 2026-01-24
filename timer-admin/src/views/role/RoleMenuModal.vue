<template>
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
              ref="treeRef"
              :data="menuTreeData"
              show-checkbox
              node-key="menuCode"
              :props="treeProps"
              :default-expanded-keys="expandedKeys"
              :check-strictly="false"
              :default-checked-keys="checkedKeys"
              @check="onTreeNodeCheck"
            />
          </el-col>
        </el-row>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import {
  ElTree,
  ElCard,
  ElRow,
  ElCol,
  ElTag,
  ElEmpty,
  ElButton,
  ElMessage,
} from "element-plus";
import { Finished, Close } from "@element-plus/icons-vue";
import { getMenuTree, getRoleMenu } from "@/api/menu";
import { getUserMenu } from "@/api/user";
import { MenuTreeVO } from "@/proto";

// 定义 props
interface Props {
  roleCode: string;
  roleName: string;
}

const props = withDefaults(defineProps<Props>(), {
  roleCode: "",
  roleName: "未知角色",
});

// 定义 emits
const emit = defineEmits<{
  cancel: [];
}>();

// 树形控件引用
const treeRef = ref();

// 权限树相关数据
const menuTreeData = ref<MenuTreeVO[]>([]);
const checkedKeys = ref<string[]>([]);
const expandedKeys = ref<string[]>([]);
const checkedNodes = ref<any[]>([]);

// 树形控件属性配置
const treeProps = {
  children: "children",
  label: "menuName",
  disabled: "disabled",
};

// 获取所有菜单权限
const fetchMenuTree = async () => {
  try {
    const response = await getMenuTree({ key: "" });
    if (response.code === 200) {
      menuTreeData.value = response.data;
      // 初始化时展开所有节点
      expandAll(true);
    } else {
      ElMessage.error(response.message || "获取菜单权限失败");
    }
  } catch (error) {
    console.error("获取菜单权限失败:", error);
    ElMessage.error("获取菜单权限失败");
  }
};

// 获取角色已有权限
const fetchRolePermissions = async () => {
  try {
    const response = await getRoleMenu({ roleCode: props.roleCode });
    if (response.code === 200) {
      console.log("response.data.checkedMenu", response.data.checkedMenu);
      checkedKeys.value = response.data.checkedMenu || [];
      expandedKeys.value = response.data.checkedMenu || [];
      updateCheckedNodes();
    } else {
      ElMessage.error(response.message || "获取角色权限失败");
    }
    console.log("获取角色权限成功:", checkedKeys);
  } catch (error) {
    console.error("获取角色权限失败:", error);
    ElMessage.error("获取角色权限失败");
  }
};

// 当节点选中状态改变时触发
const onTreeNodeCheck = (data: any, checkedInfo: any) => {
  updateCheckedNodes();
};

// 更新已选节点列表
const updateCheckedNodes = () => {
  if (!treeRef.value) return;

  const checkedNodesData = treeRef.value.getCheckedNodes(false, true);
  checkedNodes.value = checkedNodesData;

  // 更新选中的 keys
  checkedKeys.value = treeRef.value.getCheckedKeys(true);
};

// 展开/收起所有节点
const expandAll = (expand: boolean) => {
  if (!treeRef.value) return;

  const nodesToExpand: string[] = [];
  const traverse = (nodes: MenuTreeVO[]) => {
    nodes.forEach((node) => {
      if (node.id) {
        if (expand) {
          nodesToExpand.push(node.menuCode);
        }
        if (node.children && node.children.length) {
          traverse(node.children);
        }
      }
    });
  };
};

// 全选
const setCheckedAll = () => {
  if (!treeRef.value) return;
  treeRef.value.setCheckedNodes(menuTreeData.value as any);
  updateCheckedNodes();
};

// 清空选择
const setCheckedNone = () => {
  if (!treeRef.value) return;
  treeRef.value.setCheckedKeys([]);
  updateCheckedNodes();
};

// 移除已选节点
const removeCheckedNode = (node: any) => {
  if (!treeRef.value) return;
  treeRef.value.setChecked(node.id, false, false);
  updateCheckedNodes();
};

// 保存权限
const handleSave = async () => {
  //   try {
  //     const permissions = checkedNodes.value.map((node) => node.id);
  //     const response = await updateRolePermissions({
  //       roleCode: props.roleCode,
  //       permissions,
  //     });
  //     if (response.code === 200) {
  //       ElMessage.success("权限保存成功");
  //       // 可以选择关闭页面或保持打开
  //     } else {
  //       ElMessage.error(response.message || "权限保存失败");
  //     }
  //   } catch (error) {
  //     console.error("保存权限失败:", error);
  //     ElMessage.error("权限保存失败");
  //   }
};

// 取消操作
const handleCancel = () => {
  emit("cancel");
};

// 初始化数据
onMounted(async () => {
  await Promise.all([fetchMenuTree(), fetchRolePermissions()]);
});
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
