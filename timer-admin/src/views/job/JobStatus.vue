<template>
  <div class="task-dashboard">
    <!-- 统计卡片区域 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="8">
        <el-card class="stat-card success-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon :size="40" color="#52c41a">
                <CircleCheckFilled />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value success-text">{{ totalSuccess }}</div>
              <div class="stat-label">总成功次数</div>
            </div>
          </div>
          <div class="stat-trend">
            <el-tag type="success" size="default">
              <el-icon><Top /></el-icon>
              {{ successRate }}% 成功率
            </el-tag>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="stat-card failure-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon :size="40" color="#ff4d4f">
                <CircleCloseFilled />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value failure-text">{{ totalFailure }}</div>
              <div class="stat-label">总失败次数</div>
            </div>
          </div>
          <div class="stat-trend">
            <el-tag type="danger" size="small">
              <el-icon><Bottom /></el-icon>
              {{ failureRate }}% 失败率
            </el-tag>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="stat-card total-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon">
              <el-icon :size="40" color="#1890ff">
                <List />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value total-text">{{ totalCount }}</div>
              <div class="stat-label">总任务数</div>
            </div>
          </div>
          <div class="stat-trend">
            <el-tag type="primary" size="small">
              <el-icon><Clock /></el-icon>
              实时更新
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表和状态区域 -->
    <el-row :gutter="20" class="chart-row">
      <!-- 任务执行趋势图 -->
      <el-col :span="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><TrendCharts /></el-icon>
                任务执行趋势
              </span>
              <el-select v-model="timeRange" size="small" style="width: 120px">
                <el-option label="最近 7 天" value="7" />
                <el-option label="最近 30 天" value="30" />
                <el-option label="最近 90 天" value="90" />
              </el-select>
            </div>
          </template>

          <div class="chart-container">
            <div class="bar-chart">
              <div
                v-for="(item, index) in trendData"
                :key="index"
                class="bar-item"
              >
                <div class="bar-label">{{ item.monthDay }}</div>
                <div class="bar-wrapper">
                  <div
                    class="bar-success"
                    :style="{ height: item.successPercent + '%' }"
                  >
                    <span class="bar-value">{{ item.successCount }}</span>
                  </div>
                  <div
                    class="bar-failure"
                    :style="{ height: item.failurePercent + '%' }"
                  >
                    <span class="bar-value">{{ item.failureCount }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="chart-legend">
            <div class="legend-item">
              <span class="legend-dot success-dot"></span>
              <span>成功</span>
            </div>
            <div class="legend-item">
              <span class="legend-dot failure-dot"></span>
              <span>失败</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 执行器状态 -->
      <el-col :span="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Cpu /></el-icon>
                执行器状态
              </span>
              <el-button
                type="primary"
                size="small"
                @click="refreshExecutorStatus"
              >
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <div class="executor-list">
            <div
              v-for="executor in executorData"
              :key="executor.schedulerName"
              class="executor-item"
            >
              <div class="executor-info">
                <div class="executor-name">
                  <el-icon
                    :size="18"
                    :color="
                      executor.status === 'COMPLATED' ? '#52c41a' : '#ff4d4f'
                    "
                  >
                    <Monitor v-if="executor.status === 'COMPLATED'" />
                    <WarnTriangleFilled v-else />
                  </el-icon>
                  <span>{{ executor.schedulerName }}</span>
                </div>
                <div class="executor-detail">
                  <span class="executor-ip">{{ executor.ip }}</span>
                  <el-tag
                    :type="
                      executor.status === 'COMPLATED' ? 'success' : 'danger'
                    "
                    size="small"
                    effect="plain"
                  >
                    {{ executor.status === "COMPLATED" ? "在线" : "离线" }}
                  </el-tag>
                </div>
              </div>

              <div class="executor-stats">
                <div class="executor-stat">
                  <div class="stat-label-small">执行次数</div>
                  <div class="stat-value-small">
                    {{ executor.jobCount }}
                  </div>
                </div>
                <div class="executor-stat">
                  <div class="stat-label-small">成功率</div>
                  <div
                    class="stat-value-small"
                    :class="
                      executor.successFullyCount >= 90
                        ? 'success-text'
                        : 'warning-text'
                    "
                  >
                    {{ executor.successFullyCount }}%
                  </div>
                </div>
                <div class="executor-stat">
                  <div class="stat-label-small">内存</div>
                  <div class="progress-bar">
                    <div
                      class="progress-fill"
                      :style="{ width: executor.usedMemory + '%' }"
                      :class="getMemoryUsageClass(executor.usedMemory)"
                    ></div>
                    <span class="progress-text"
                      >{{ executor.usedMemory }}%</span
                    >
                  </div>
                </div>
              </div>
            </div>

            <el-empty
              v-if="executorData.length === 0"
              description="暂无执行器数据"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近任务列表 -->
    <el-row class="table-row">
      <el-col :span="24">
        <el-card class="table-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Document /></el-icon>
                最近任务执行记录
              </span>
              <el-button type="primary" size="small" @click="viewAllTasks">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>

          <el-table :data="recentTasks" style="width: 100%" size="small" stripe>
            <el-table-column prop="taskName" label="任务名称" min-width="150" />
            <el-table-column prop="executor" label="执行器" width="120" />
            <el-table-column prop="startTime" label="开始时间" width="160" />
            <el-table-column prop="duration" label="执行时长" width="100" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag
                  :type="
                    row.status === 'success'
                      ? 'success'
                      : row.status === 'failure'
                        ? 'danger'
                        : 'warning'
                  "
                  size="small"
                  effect="plain"
                >
                  {{
                    row.status === "success"
                      ? "成功"
                      : row.status === "failure"
                        ? "失败"
                        : "进行中"
                  }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="message"
              label="执行信息"
              min-width="200"
              show-overflow-tooltip
            />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import {
  CircleCheckFilled,
  CircleCloseFilled,
  TrendCharts,
  Monitor,
  Top,
  Bottom,
  Clock,
  Refresh,
  Document,
  ArrowRight,
  List,
  WarnTriangleFilled,
  Cpu
} from "@element-plus/icons-vue";
import {
  ElCard,
  ElRow,
  ElCol,
  ElIcon,
  ElTag,
  ElSelect,
  ElOption,
  ElButton,
  ElTable,
  ElTableColumn,
  ElEmpty,
  ElMessage,
} from "element-plus";
import {
  getJobExecutionCounts,
  getJobExecutionByDate,
  getSchedules,
} from "@/api/job";

// 统计数据
const totalSuccess = ref(0);
const totalFailure = ref(0);
const totalCount = ref(0);
const successRate = ref(0);
const failureRate = ref(0);

// 时间范围选择
const timeRange = ref("7");

interface JobExecution {
  successCount: string;
  failureCount: string;
  monthDay: string;
  successPercent: string;
  failurePercent: string;
}

// 趋势图数据
const trendData = ref<JobExecution[]>();

// 执行器数据
interface Executor {
  id: number;
  schedulerName: string;
  ip: string;
  status: "COMPLATED" | "FAUILURE" | "RUNNING";
  maxMemory: string;
  usedMemory: string;
  totalMemory: string;
  freeMemory: string;
  cpuCores: string;
  cpuUsage: string;
  jobCount: string;
  successFullyCount: number;
}

const executorData = ref<Executor[]>([]);

// 最近任务数据
interface TaskRecord {
  taskName: string;
  executor: string;
  startTime: string;
  duration: string;
  status: "success" | "failure" | "running";
  message: string;
}

const recentTasks = ref<TaskRecord[]>([]);

// 刷新执行器状态
const refreshExecutorStatus = async () => {
  try {
    // TODO: 调用 API 刷新执行器状态
    ElMessage.success("执行器状态已更新");
  } catch (error) {
    ElMessage.error("刷新状态失败");
  }
};

// 获取 CPU 使用率样式类
const getCpuUsageClass = (usage: any) => {
  if (usage >= 80) return "progress-danger";
  if (usage >= 60) return "progress-warning";
  return "progress-success";
};

// 获取内存使用率样式类
const getMemoryUsageClass = (usage: any) => {
  if (usage >= 80) return "progress-danger";
  if (usage >= 60) return "progress-warning";
  return "progress-success";
};

// 查看全部任务
const viewAllTasks = () => {
  // TODO: 跳转到任务列表页面
  console.log("跳转到任务列表页面");
};

const handleJobExecutionCounts = async () => {
  try {
    const res = await getJobExecutionCounts();
    if (res.code !== 200) {
      ElMessage.error(res.message);
      return;
    }
    console.log(res.data);
    const list = res.data.jobExecutionCounts;
    if (list.length === 0) {
      return;
    }
    list.forEach((item: any) => {
      if (item.status === "COMPLETED") {
        totalSuccess.value = item.count;
        successRate.value = item.rate;
      } else if (item.status === "FAILED") {
        totalFailure.value = item.count;
        failureRate.value = item.rate;
      }
    });
    totalCount.value = res.data.totalCount;
  } catch (error) {
    ElMessage.error("获取任务执行次数失败");
  }
};

const handleJobExecutionByDate = async () => {
  try {
    const res = await getJobExecutionByDate({
      time: timeRange.value,
    });
    if (res.code !== 200) {
      ElMessage.error(res.message);
      return;
    }
    trendData.value = res.data.jobDateExecutions;
  } catch (error) {
    ElMessage.error("获取任务执行次数失败");
  }
};

const handleSchedules = async () => {
  try {
    const res = await getSchedules();
    if (res.code !== 200) {
      ElMessage.error(res.message);
      return;
    }
    executorData.value = res.data.schedules;
  } catch (error) {
    ElMessage.error("获取执行器列表失败");
  }
};

// 加载数据
onMounted(() => {
  // TODO: 调用 API 加载实际数据
  console.log("任务仪表板已加载");
  handleJobExecutionCounts();
  handleJobExecutionByDate();
  handleSchedules();
});
</script>

<style scoped>
.task-dashboard {
  padding: 20px;
}

/* 统计卡片行 */
.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 12px;
  transition: all 0.3s ease;
  overflow: hidden;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 10px 0;
  gap: 16px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f0f9ff, #e0f2fe);
  border-radius: 12px;
  flex-shrink: 0;
}

.success-card .stat-icon {
  background: linear-gradient(135deg, #f6ffed, #d9f7be);
}

.failure-card .stat-icon {
  background: linear-gradient(135deg, #fff1f0, #ffccc7);
}

.total-card .stat-icon {
  background: linear-gradient(135deg, #e6f7ff, #bae7ff);
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: 4px;
}

.success-text {
  color: #52c41a;
}

.failure-text {
  color: #ff4d4f;
}

.total-text {
  color: #1890ff;
}

.stat-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.stat-trend {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.stat-trend .el-tag {
  display: inline-flex !important;
  align-items: center !important;
  vertical-align: middle;
  gap: 4px;
}

.stat-trend .el-tag .el-icon {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}
/* 图表卡片行 */
.chart-row {
  margin-bottom: 20px;
}

.chart-card {
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 柱状图表 */
.chart-container {
  padding: 20px 0;
  height: 280px;
}

.bar-chart {
  display: flex;
  justify-content: space-around;
  align-items: flex-end;
  height: 100%;
  padding: 0 20px;
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  max-width: 80px;
}

.bar-label {
  font-size: 12px;
  color: #666;
  margin-bottom: 8px;
}

.bar-wrapper {
  display: flex;
  gap: 4px;
  align-items: flex-end;
  height: 200px;
  width: 100%;
  justify-content: center;
}

.bar-success,
.bar-failure {
  width: 20px;
  border-radius: 4px 4px 0 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 4px;
  transition: all 0.3s ease;
  position: relative;
}

.bar-success {
  background: linear-gradient(180deg, #52c41a, #73d13d);
}

.bar-failure {
  background: linear-gradient(180deg, #ff4d4f, #ff7875);
}

.bar-value {
  font-size: 10px;
  color: white;
  font-weight: 600;
  transform: rotate(-90deg);
  white-space: nowrap;
  position: absolute;
  bottom: 50%;
  left: 50%;
  transform: translateX(-50%) rotate(-90deg);
}

.chart-legend {
  display: flex;
  justify-content: center;
  gap: 30px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #666;
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.success-dot {
  background: linear-gradient(135deg, #52c41a, #73d13d);
}

.failure-dot {
  background: linear-gradient(135deg, #ff4d4f, #ff7875);
}

/* 执行器列表 */
.executor-list {
  max-height: 400px;
  overflow-y: auto;
}

.executor-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.2s ease;
}

.executor-item:last-child {
  border-bottom: none;
}

.executor-item:hover {
  background: #fafafa;
}

.executor-info {
  flex: 1;
}

.executor-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.executor-detail {
  display: flex;
  align-items: center;
  gap: 12px;
}

.executor-ip {
  font-size: 13px;
  color: #666;
}

.executor-stats {
  display: flex;
  gap: 20px;
  align-items: center;
}

.executor-stat {
  text-align: center;
  min-width: 70px;
}

.stat-label-small {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.stat-value-small {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.warning-text {
  color: #faad14;
}

/* 进度条 */
.progress-bar {
  width: 100px;
  height: 18px;
  background: #f0f0f0;
  border-radius: 9px;
  overflow: hidden;
  position: relative;
  display: inline-block;
  vertical-align: middle;
}

.progress-fill {
  height: 100%;
  border-radius: 9px;
  transition: width 0.3s ease;
}

.progress-success {
  background: linear-gradient(90deg, #52c41a, #73d13d);
}

.progress-warning {
  background: linear-gradient(90deg, #faad14, #ffc53d);
}

.progress-danger {
  background: linear-gradient(90deg, #ff4d4f, #ff7875);
}

.progress-text {
  font-size: 11px;
  color: #666;
  margin-left: 6px;
}

/* 表格卡片 */
.table-row {
  margin-bottom: 0;
}

.table-card {
  border-radius: 12px;
}

/* 响应式布局 */
@media (max-width: 1200px) {
  .executor-stats {
    gap: 12px;
  }

  .executor-stat {
    min-width: 60px;
  }

  .progress-bar {
    width: 80px;
  }
}

@media (max-width: 768px) {
  .stat-value {
    font-size: 28px;
  }

  .stat-icon {
    width: 60px;
    height: 60px;
  }

  .executor-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .executor-stats {
    width: 100%;
    justify-content: space-around;
  }
}
</style>
