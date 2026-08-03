<template>
  <div class="operation-log-page">
    <!-- 筛选栏 -->
    <a-card :bordered="false" class="filter-card">
      <a-form layout="inline">
        <a-form-item label="日志类型">
          <a-select v-model:value="filters.activityType" style="width:140px" allow-clear placeholder="全部类型" @change="handleSearch">
            <a-select-option value="">全部类型</a-select-option>
            <a-select-option value="LOGIN">登录</a-select-option>
            <a-select-option value="LOGOUT">注销</a-select-option>
            <a-select-option value="OPERATION">业务操作</a-select-option>
            <a-select-option value="ACCESS">页面访问</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="开始时间">
          <a-date-picker v-model:value="filters.startDate" style="width:160px" placeholder="选择开始日期" @change="handleSearch" />
        </a-form-item>
        <a-form-item label="结束时间">
          <a-date-picker v-model:value="filters.endDate" style="width:160px" placeholder="选择结束日期" @change="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch" :loading="loading">
            <template #icon><search-outlined /></template>
            查询
          </a-button>
          <a-button style="margin-left: 8px" @click="handleReset">
            <template #icon><reload-outlined /></template>
            重置
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 日志表格 -->
    <a-card :bordered="false" class="table-card" title="操作日志">
      <template #extra>
        <span class="total-count">共 {{ total }} 条记录</span>
      </template>
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        row-key="oid"
        size="middle"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <!-- 类型标签 -->
          <template v-if="column.key === 'activityType'">
            <a-tag :color="typeTagColor(record.activityType)" size="small">
              {{ typeLabel(record.activityType) }}
            </a-tag>
          </template>

          <!-- 操作描述 -->
          <template v-else-if="column.key === 'actionDesc'">
            <span :style="{ color: actionColor(record.activityType, record.actionDesc) }">
              {{ record.actionDesc || '-' }}
            </span>
          </template>

          <!-- 操作结果 -->
          <template v-else-if="column.key === 'result'">
            <a-tag v-if="record.result === 'SUCCESS'" color="success" size="small">成功</a-tag>
            <a-tag v-else-if="record.result === 'FAIL'" color="error" size="small">失败</a-tag>
            <span v-else>-</span>
          </template>

          <!-- 耗时 -->
          <template v-else-if="column.key === 'durationMs'">
            <span v-if="record.durationMs != null">{{ record.durationMs }}ms</span>
            <span v-else>-</span>
          </template>

          <!-- 时间 -->
          <template v-else-if="column.key === 'createdAt'">
            <span class="time-text">{{ formatTime(record.createdAt) }}</span>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { getActivityLogs } from '@/api'

// ---- 筛选条件 ----
const filters = reactive({
  activityType: '',
  startDate: null,
  endDate: null
})

// ---- 表格数据 ----
const loading = ref(false)
const dataSource = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(15)

const pagination = computed(() => ({
  current: currentPage.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showQuickJumper: true,
  pageSizeOptions: ['10', '15', '20', '50'],
  showTotal: (t) => `共 ${t} 条`
}))

const columns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 165, sorter: false },
  { title: '操作人', dataIndex: 'creator', key: 'creator', width: 110 },
  { title: '类型', dataIndex: 'activityType', key: 'activityType', width: 80 },
  { title: '操作描述', dataIndex: 'actionDesc', key: 'actionDesc', width: 150 },
  { title: '目标对象', dataIndex: 'targetName', key: 'targetName', width: 140, ellipsis: true },
  { title: '对象类型', dataIndex: 'targetType', key: 'targetType', width: 90 },
  { title: '结果', dataIndex: 'result', key: 'result', width: 70 },
  { title: '耗时', dataIndex: 'durationMs', key: 'durationMs', width: 70 },
  { title: '操作IP', dataIndex: 'operatorIp', key: 'operatorIp', width: 130 }
]

// ---- 工具函数 ----
function typeLabel(type) {
  const map = { LOGIN: '登录', LOGOUT: '注销', OPERATION: '业务操作', ACCESS: '页面访问' }
  return map[type] || type || '-'
}

function typeTagColor(type) {
  const map = { LOGIN: 'green', LOGOUT: 'default', OPERATION: 'blue', ACCESS: 'purple' }
  return map[type] || 'default'
}

function actionColor(type, action) {
  if (type === 'LOGIN') return '#52c41a'
  if (type === 'LOGOUT') return '#8c8c8c'
  if (!action) return '#8c8c8c'
  if (action.includes('删除')) return '#ff4d4f'
  if (action.includes('检出')) return '#fa8c16'
  return '#1677ff'
}

function formatTime(timeStr) {
  if (!timeStr) return '-'
  try {
    const d = new Date(timeStr)
    if (isNaN(d.getTime())) return timeStr.substring(0, 19).replace('T', ' ')
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  } catch {
    return timeStr.substring(0, 19).replace('T', ' ')
  }
}

// ---- 加载数据 ----
async function loadLogs() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    if (filters.activityType) params.activityType = filters.activityType
    if (filters.startDate) params.startDate = filters.startDate.format('YYYY-MM-DD')
    if (filters.endDate) params.endDate = filters.endDate.format('YYYY-MM-DD')

    const res = await getActivityLogs(params)
    if (res.code === 200 && res.data) {
      dataSource.value = res.data.rows || []
      total.value = res.data.total || 0
    } else {
      dataSource.value = []
      total.value = 0
    }
  } catch {
    dataSource.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadLogs()
}

function handleReset() {
  filters.activityType = ''
  filters.startDate = null
  filters.endDate = null
  currentPage.value = 1
  loadLogs()
}

function handleTableChange(pag) {
  currentPage.value = pag.current
  pageSize.value = pag.pageSize
  loadLogs()
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped>
.operation-log-page {
  max-width: 100%;
}

.filter-card {
  margin-bottom: 16px;
  border-radius: 10px;
}

.filter-card :deep(.ant-form-item) {
  margin-bottom: 0;
}

.table-card {
  border-radius: 10px;
}

.total-count {
  font-size: 13px;
  color: #8c8c8c;
}

.time-text {
  font-size: 13px;
  color: #595959;
  font-family: 'Consolas', 'Monaco', monospace;
}
</style>
