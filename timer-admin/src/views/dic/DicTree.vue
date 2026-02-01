<template>
  <div class="dictionary-container">
    <!-- 简洁头部 -->
    <div class="header">
      <h1 class="title">数据词典</h1>
      <div class="header-actions">
        <el-button type="primary" @click="handleAddDictionary">
          <el-icon><Plus /></el-icon> 添加字典
        </el-button>
      </div>
    </div>

    <el-container class="main-layout">
      <!-- 左侧树形字典面板 -->
      <el-aside width="320px" class="tree-panel">
        <div class="search-container">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索词典项..."
            :prefix-icon="Search"
          />
        </div>

        <div class="tree-container">
          <el-tree
            ref="treeRef"
            :data="dictionaryTree"
            node-key="id"
            :props="treeProps"
            :expand-on-click-node="false"
            :highlight-current="true"
            :default-expanded-keys="expandedKeys"
            @node-click="handleNodeClick"
            class="dictionary-tree"
          >
            <template #default="{ node, data }">
              <div class="tree-node">
                <el-icon class="node-icon">
                  <component :is="getNodeIcon(data.type)" />
                </el-icon>
                <span class="node-label">{{ node.label }}</span>
                <span class="node-count" v-if="data.children && data.children.length">
                  {{ data.children.length }}
                </span>
              </div>
            </template>
          </el-tree>
        </div>
      </el-aside>

      <!-- 右侧详情面板 -->
      <el-main class="detail-panel">
        <div v-if="selectedDictionary" class="detail-content">
          <!-- 详情头部 -->
          <div class="detail-header">
            <div class="header-main">
              <el-icon class="detail-icon" :class="getDetailIconClass(selectedDictionary.type)">
                <component :is="getNodeIcon(selectedDictionary.type)" />
              </el-icon>
              <div class="header-info">
                <h2 class="detail-title">{{ selectedDictionary.name }}</h2>
                <p class="detail-description">{{ selectedDictionary.description }}</p>
              </div>
            </div>
            <div class="header-meta">
              <el-tag 
                :type="getTagType(selectedDictionary.status)"
                class="status-tag"
              >
                {{ selectedDictionary.status === 1 ? '启用' : '禁用' }}
              </el-tag>
              <span class="update-time">
                更新时间: {{ formatDate(selectedDictionary.updateTime) }}
              </span>
            </div>
          </div>

          <!-- 详情内容 -->
          <div class="detail-body">
            <el-tabs type="border-card" class="detail-tabs">
              <el-tab-pane label="基本信息" name="basic">
                <div class="tab-content">
                  <el-descriptions :column="2" border class="info-table">
                    <el-descriptions-item label="字典编码">
                      <span class="code-value">{{ selectedDictionary.code }}</span>
                    </el-descriptions-item>
                    <el-descriptions-item label="字典类型">
                      <el-tag>{{ getTypeLabel(selectedDictionary.type) }}</el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="创建时间">
                      {{ formatDate(selectedDictionary.createTime) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="排序">
                      {{ selectedDictionary.sort }}
                    </el-descriptions-item>
                    <el-descriptions-item label="备注" :span="2">
                      {{ selectedDictionary.remark || '无' }}
                    </el-descriptions-item>
                  </el-descriptions>
                </div>
              </el-tab-pane>

              <el-tab-pane label="子项管理" name="items" v-if="selectedDictionary.children">
                <div class="tab-content">
                  <div class="items-header">
                    <h3>字典子项列表</h3>
                    <el-button type="primary" size="small">
                      <el-icon><Plus /></el-icon>
                      新增子项
                    </el-button>
                  </div>
                  <el-table 
                    :data="selectedDictionary.children" 
                    style="width: 100%"
                    class="items-table"
                  >
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
              </el-tab-pane>
            </el-tabs>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-state">
          <div class="empty-content">
            <el-icon class="empty-icon"><MessageBox /></el-icon>
            <h3>选择字典项查看详情</h3>
            <p>请从左侧树形结构中选择一个字典项</p>
          </div>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  Collection,
  Search,
  Folder,
  Document,
  Setting,
  Plus,
  MessageBox
} from '@element-plus/icons-vue'

// 搜索关键词
const searchKeyword = ref('')

// 树形数据
const dictionaryTree = ref([
  {
    id: 1,
    name: '系统配置',
    code: 'SYSTEM_CONFIG',
    type: 'folder',
    description: '系统基础配置参数',
    status: 1,
    sort: 1,
    createTime: '2024-01-01 10:00:00',
    updateTime: '2024-01-15 14:30:00',
    remark: '包含系统运行所需的基础配置',
    children: [
      {
        id: 11,
        name: '用户配置',
        code: 'USER_CONFIG',
        type: 'document',
        description: '用户相关配置项',
        status: 1,
        sort: 1,
        createTime: '2024-01-02 09:00:00',
        updateTime: '2024-01-10 16:45:00',
        remark: '用户注册、登录等相关配置',
        children: [
          {
            id: 111,
            name: '注册开关',
            code: 'REGISTER_ENABLED',
            type: 'setting',
            value: 'true',
            description: '控制用户注册功能的开启状态',
            status: 1,
            sort: 1
          }
        ]
      }
    ]
  }
])

// 树形配置
const treeProps = {
  label: 'name',
  children: 'children'
}

// 展开的节点
const expandedKeys = ref([1])

// 选中的字典项
const selectedDictionary = ref<any>(null)

// 获取节点图标
const getNodeIcon = (type: string) => {
  const iconMap: Record<string, any> = {
    folder: Folder,
    document: Document,
    setting: Setting
  }
  return iconMap[type] || Document
}

// 获取详情图标类名
const getDetailIconClass = (type: string) => {
  const classMap: Record<string, string> = {
    folder: 'icon-folder',
    document: 'icon-document',
    setting: 'icon-setting'
  }
  return classMap[type] || 'icon-document'
}

// 获取标签类型
const getTagType = (status: number) => {
  return status === 1 ? 'success' : 'info'
}

// 获取类型标签
const getTypeLabel = (type: string) => {
  const labelMap: Record<string, string> = {
    folder: '分类',
    document: '字典',
    setting: '配置项'
  }
  return labelMap[type] || '未知'
}

// 格式化日期
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString('zh-CN')
}

// 处理节点点击
const handleNodeClick = (data: any) => {
  selectedDictionary.value = data
}

// 处理添加字典
const handleAddDictionary = () => {
  console.log('添加字典')
}
</script>

<style scoped>
.dictionary-container {
  background: #ffffff;
  min-height: 100vh;
  padding: 20px;
  color: #333333;
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

/* 左侧面板样式 */
.tree-panel {
  background: #f5f7fa;
  border-right: 1px solid #e0e0e0;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.search-container {
  margin-bottom: 20px;
}

.tree-container {
  flex: 1;
  overflow: auto;
}

.dictionary-tree {
  background: transparent;
  color: #333;
}

.dictionary-tree :deep(.el-tree-node) {
  margin-bottom: 8px;
}

.dictionary-tree :deep(.el-tree-node__content) {
  height: 40px;
  border-radius: 4px;
  transition: all 0.2s;
}

.dictionary-tree :deep(.el-tree-node__content:hover) {
  background: #ecf5ff;
}

.dictionary-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #409eff;
  color: white;
}

.tree-node {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 0 12px;
}

.node-icon {
  margin-right: 8px;
  color: #409eff;
  font-size: 16px;
}

.dictionary-tree :deep(.el-tree-node.is-current) .node-icon {
  color: white;
}

.node-label {
  flex: 1;
  color: #333;
  font-size: 14px;
}

.dictionary-tree :deep(.el-tree-node.is-current) .node-label {
  color: white;
}

.node-count {
  background: #ecf5ff;
  color: #409eff;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.dictionary-tree :deep(.el-tree-node.is-current) .node-count {
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

/* 右侧详情面板样式 */
.detail-panel {
  background: #ffffff;
  padding: 0;
  overflow: auto;
}

.detail-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 详情头部样式 */
.detail-header {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  border: 1px solid #e0e0e0;
}

.header-main {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 15px;
}

.detail-icon {
  font-size: 40px;
  padding: 12px;
  border-radius: 8px;
}

.detail-icon.icon-folder {
  background: #409eff;
  color: white;
}

.detail-icon.icon-document {
  background: #67c23a;
  color: white;
}

.detail-icon.icon-setting {
  background: #909399;
  color: white;
}

.header-info {
  flex: 1;
}

.detail-title {
  color: #333;
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.detail-description {
  color: #666;
  font-size: 14px;
  margin: 0;
  line-height: 1.5;
}

.header-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 15px;
}

.status-tag {
  font-size: 12px;
  font-weight: 500;
}

.update-time {
  color: #666;
  font-size: 12px;
}

/* 详情主体样式 */
.detail-body {
  flex: 1;
}

.detail-tabs {
  background: #ffffff;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e0e0e0;
}

.detail-tabs :deep(.el-tabs__header) {
  background: #f5f7fa;
  margin: 0;
  border-bottom: 1px solid #e0e0e0;
}

.detail-tabs :deep(.el-tabs__item) {
  color: #666;
  height: 45px;
  line-height: 45px;
}

.detail-tabs :deep(.el-tabs__item.is-active) {
  color: #409eff;
  background: #ffffff;
}

.tab-content {
  padding: 20px;
}

/* 信息表格样式 */
.info-table {
  background: transparent;
}

.info-table :deep(.el-descriptions__body) {
  background: #ffffff;
  border-radius: 4px;
  overflow: hidden;
}

.info-table :deep(.el-descriptions__label) {
  background: #f5f7fa;
  color: #666;
  font-weight: 500;
}

.info-table :deep(.el-descriptions__content) {
  color: #333;
}

.code-value {
  font-family: 'Monaco', 'Consolas', monospace;
  background: #f5f7fa;
  padding: 4px 8px;
  border-radius: 4px;
  color: #409eff;
  border: 1px solid #e0e0e0;
}

/* 子项管理样式 */
.items-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.items-header h3 {
  color: #333;
  margin: 0;
  font-size: 18px;
}

.items-table {
  background: #ffffff;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #e0e0e0;
}

.items-table :deep(.el-table__header) {
  background: #f5f7fa;
}

.items-table :deep(.el-table__header th) {
  background: transparent;
  color: #666;
  font-weight: 500;
}

.items-table :deep(.el-table__row) {
  background: #ffffff;
  color: #333;
}

.items-table :deep(.el-table__row:hover) {
  background: #f5f7fa;
}

/* 空状态样式 */
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: #f5f7fa;
  border-radius: 8px;
}

.empty-content {
  text-align: center;
  padding: 40px;
}

.empty-icon {
  font-size: 48px;
  color: #409eff;
  margin-bottom: 20px;
  opacity: 0.6;
}

.empty-content h3 {
  color: #333;
  font-size: 20px;
  margin: 0 0 15px 0;
}

.empty-content p {
  color: #666;
  font-size: 14px;
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .dictionary-container {
    padding: 10px;
  }
  
  .main-layout {
    flex-direction: column;
  }
  
  .tree-panel {
    width: 100%;
    height: auto;
    border-right: none;
    border-bottom: 1px solid #e0e0e0;
  }
  
  .header-main {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
}
</style>