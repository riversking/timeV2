<template>
  <div class="app-container">
    <div class="filter-container">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索词典项..."
        style="width: 200px; margin-right: 10px"
        class="filter-item"
        :prefix-icon="Search"
        @keyup.enter="handleFilter"
      />
      <el-button
        class="filter-item"
        type="primary"
        :icon="Search"
        @click="handleFilter"
      >
        搜索
      </el-button>
      <el-button
        class="filter-item"
        style="margin-left: 10px"
        type="primary"
        :icon="Plus"
        @click="handleCreate"
      >
        添加字典
      </el-button>
    </div>

    <el-row :gutter="20">
      <el-col :span="6">
        <div class="tree-container">
          <el-tree-v2
            ref="treeRef"
            :data="dictionaryTree"
            node-key="id"
            :props="treeProps"
            :expand-on-click-node="false"
            :highlight-current="true"
            :default-expanded-keys="expandedKeys"
            :height="treeHeight"
            @node-click="handleNodeClick"
            class="scrollable-tree"
          >
            <template #default="{ node, data }">
              <div class="custom-tree-node">
                <el-icon class="node-icon">
                  <component :is="getNodeIcon(data.type)" />
                </el-icon>
                <span class="node-label">{{ node.label }}</span>
                <span class="node-badge" v-if="data.children && data.children.length">
                  {{ data.children.length }}
                </span>
              </div>
            </template>
          </el-tree-v2>
        </div>
      </el-col>
      
      <el-col :span="18">
        <el-card v-if="selectedDictionary" class="box-card">
          <template #header>
            <div class="card-header">
              <span>{{ selectedDictionary.name }}</span>
              <el-tag :type="getStatusType(selectedDictionary.status)" effect="dark">
                {{ selectedDictionary.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </div>
          </template>
          
          <el-descriptions :column="2" border>
            <el-descriptions-item label="字典编码">
              <span class="code-field">{{ selectedDictionary.code }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="字典类型">
              <el-tag>{{ getTypeLabel(selectedDictionary.type) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDate(selectedDictionary.createTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              {{ formatDate(selectedDictionary.updateTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="排序">
              {{ selectedDictionary.sort }}
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">
              {{ selectedDictionary.remark || '无' }}
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="selectedDictionary.children" style="margin-top: 20px">
            <h3>字典子项列表</h3>
            <el-table :data="selectedDictionary.children" style="width: 100%" stripe>
              <el-table-column prop="name" label="名称" width="180" />
              <el-table-column prop="code" label="编码" width="180" />
              <el-table-column prop="value" label="值" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'info'">
                    {{ row.status === 1 ? '启用' : '禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button type="primary" link size="small">编辑</el-button>
                  <el-button type="danger" link size="small">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
        
        <el-empty v-else description="请选择字典项查看详情" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from "vue";
import {
  Collection,
  Search,
  Folder,
  Document,
  Setting,
  Plus,
  MessageBox,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { getDicTree } from "@/api/dic";

interface DicTree {
  id: number;
  parentId: number | null;
  children?: DicTree[];
  dicKey: string;
  dicValue: string;
  sort: number;
}

const containerHeight = ref(0);
const searchKeyword = ref("");
const dictionaryTree = ref<DicTree[]>([]);
const selectedDictionary = ref<any>(null);
const expandedKeys = ref([1]);

const treeProps = {
  label: "dicKey",
  children: "children",
};

// 动态计算树形高度
const treeHeight = computed(() => {
  // 直接使用 containerHeight，减去 padding（10px * 2 = 20px）
  return Math.max(containerHeight.value - 20, 300);
});

// 计算容器高度 - 修改为获取 tree-container 的实际高度
const calculateContainerHeight = () => {
  const treeContainer = document.querySelector(".tree-container");
  if (treeContainer) {
    // 直接获取容器的 clientHeight（包含 padding）
    containerHeight.value = treeContainer.clientHeight;
  }
};

const handleResize = () => {
  calculateContainerHeight();
};

onMounted(async () => {
  await fetchDicTree();
  calculateContainerHeight();
  window.addEventListener("resize", handleResize);
});

onUnmounted(() => {
  window.removeEventListener("resize", handleResize);
});

const fetchDicTree = async () => {
  try {
    const res = await getDicTree({ key: searchKeyword.value });
    if (res.code !== 200) {
      ElMessage.error(res.message);
      return;
    }
    dictionaryTree.value = res.data;
  } catch (error) {
    console.log(error);
    ElMessage.error("获取字典树失败");
  }
};

const handleFilter = () => {
  fetchDicTree();
};

const handleCreate = () => {
  console.log("添加字典");
};

const handleNodeClick = (data: any) => {
  selectedDictionary.value = data;
};

// 获取节点图标
const getNodeIcon = (type: string) => {
  const iconMap: Record<string, any> = {
    folder: Folder,
    document: Document,
    setting: Setting,
  };
  return iconMap[type] || Document;
};

// 获取标签类型
const getStatusType = (status: number) => {
  return status === 1 ? "success" : "info";
};

// 获取类型标签
const getTypeLabel = (type: string) => {
  const labelMap: Record<string, string> = {
    folder: "分类",
    document: "字典",
    setting: "配置项",
  };
  return labelMap[type] || "未知";
};

// 格式化日期
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString("zh-CN");
};
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.filter-container {
  margin-bottom: 20px;
}

.tree-container {
  height: calc(100vh - 200px);
  overflow: auto;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  padding: 10px;
  /* 确保容器有明确的盒模型 */
  box-sizing: border-box;
}

.tree-container::-webkit-scrollbar {
  width: 6px;
}

.tree-container::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.tree-container::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.tree-container::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.custom-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-icon {
  color: #409eff;
}

.node-badge {
  background: #409eff;
  color: white;
  border-radius: 10px;
  padding: 2px 6px;
  font-size: 12px;
}

.code-field {
  font-family: 'Monaco', 'Consolas', monospace;
  background: #f5f7fa;
  padding: 4px 8px;
  border-radius: 4px;
  color: #409eff;
  border: 1px solid #e0e0e0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.box-card {
  min-height: 300px;
}
</style>