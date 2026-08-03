<template>
  <div class="process-monitor">
    <div class="page-header">
      <h2>流程监控</h2>
    </div>

    <a-card>
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="all-running" tab="运行中流程" />
        <a-tab-pane key="my-running" tab="我发起的" />
        <a-tab-pane key="my-involved" tab="我参与的" />
      </a-tabs>

      <a-table :columns="columns" :dataSource="instances" :loading="loading" rowKey="id"
        :pagination="{ current: page, pageSize: size, showSizeChanger: false, onChange: onPageChange }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'process'">
            <div>
              <div style="font-weight:500;">{{ record.processDefinitionName || record.processDefinitionKey }}</div>
              <div style="font-size:12px;color:#999;">{{ record.businessKey || '实例: ' + record.id?.substring(0, 8) }}</div>
            </div>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ statusText(record.status) }}
            </a-tag>
          </template>
          <template v-if="column.key === 'startTime'">
            {{ formatTime(record.startTime) }}
          </template>
          <template v-if="column.key === 'endTime'">
            {{ record.endTime ? formatTime(record.endTime) : '—' }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space v-if="record.status === 'running'">
              <a-button size="small" @click="viewDetail(record)">详情</a-button>
              <a-popconfirm title="确认挂起此流程实例？" @confirm="suspendInstance(record)">
                <a-button size="small">挂起</a-button>
              </a-popconfirm>
              <a-popconfirm title="确认终止此流程实例？" @confirm="terminateInstance(record)">
                <a-button size="small" danger>终止</a-button>
              </a-popconfirm>
            </a-space>
            <a-space v-else-if="record.status === 'suspended'">
              <a-button size="small" type="primary" @click="activateInstance(record)">激活</a-button>
              <a-popconfirm title="确认删除此流程实例？" @confirm="deleteInstance(record)">
                <a-button size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
            <a-space v-else>
              <a-button size="small" @click="viewDetail(record)">详情</a-button>
              <a-popconfirm title="确认删除此流程实例？" @confirm="deleteInstance(record)">
                <a-button size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 详情弹窗 -->
    <a-modal v-model:visible="showDetailModal" title="流程实例详情" width="640px" :footer="null">
      <a-descriptions bordered :column="2" v-if="currentDetail">
        <a-descriptions-item label="实例 ID">{{ currentDetail.id?.substring(0, 12) }}...</a-descriptions-item>
        <a-descriptions-item label="流程名称">{{ currentDetail.processDefinitionName }}</a-descriptions-item>
        <a-descriptions-item label="流程 Key">{{ currentDetail.processDefinitionKey }}</a-descriptions-item>
        <a-descriptions-item label="业务 Key">{{ currentDetail.businessKey || '—' }}</a-descriptions-item>
        <a-descriptions-item label="发起人">{{ currentDetail.startUserId }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="statusColor(currentDetail.status)">{{ statusText(currentDetail.status) }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ formatTime(currentDetail.startTime) }}</a-descriptions-item>
        <a-descriptions-item label="结束时间">{{ currentDetail.endTime ? formatTime(currentDetail.endTime) : '—' }}</a-descriptions-item>
        <a-descriptions-item label="终止原因" :span="2">{{ currentDetail.deleteReason || '—' }}</a-descriptions-item>
      </a-descriptions>

      <a-divider v-if="currentDetail?.variables && Object.keys(currentDetail.variables).length">流程变量</a-divider>
      <div v-if="currentDetail?.variables && Object.keys(currentDetail.variables).length"
        style="background:#f5f5f5;padding:12px;border-radius:4px;max-height:200px;overflow:auto;">
        <div v-for="(val, key) in currentDetail.variables" :key="key" style="margin-bottom:4px;">
          <strong>{{ key }}</strong>: {{ val }}
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import api from '@/api'

const activeTab = ref('all-running')
const instances = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)

const showDetailModal = ref(false)
const currentDetail = ref(null)

const apiMap = {
  'all-running': '/workflow/instance/all-running',
  'my-running': '/workflow/instance/my-running',
  'my-involved': '/workflow/instance/my-involved'
}

const columns = [
  { title: '所属流程 / 业务标识', key: 'process' },
  { title: '发起人', dataIndex: 'startUserId', width: 100 },
  { title: '状态', key: 'status', width: 80, align: 'center' },
  { title: '开始时间', key: 'startTime', width: 160 },
  { title: '结束时间', key: 'endTime', width: 160 },
  { title: '操作', key: 'action', width: 220 }
]

function statusColor(status) {
  return status === 'running' ? 'blue' : status === 'suspended' ? 'orange' : status === 'completed' ? 'green' : 'red'
}

function statusText(status) {
  return status === 'running' ? '运行中' : status === 'suspended' ? '已挂起' : status === 'completed' ? '已完成' : '已终止'
}

async function loadInstances() {
  loading.value = true
  try {
    const res = await api.get(apiMap[activeTab.value], { params: { page: page.value, size: size.value } })
    if (res.code === 200) instances.value = res.data || []
  } finally {
    loading.value = false
  }
}

function onPageChange(p) {
  page.value = p
  loadInstances()
}

async function viewDetail(record) {
  try {
    const res = await api.get(`/workflow/instance/${record.id}`)
    if (res.code === 200) {
      currentDetail.value = res.data
      showDetailModal.value = true
    }
  } catch { message.error('获取详情失败') }
}

async function suspendInstance(record) {
  try {
    await api.post(`/workflow/instance/${record.id}/suspend`)
    message.success('流程实例已挂起')
    loadInstances()
  } catch { message.error('操作失败') }
}

async function activateInstance(record) {
  try {
    await api.post(`/workflow/instance/${record.id}/activate`)
    message.success('流程实例已激活')
    loadInstances()
  } catch { message.error('操作失败') }
}

async function terminateInstance(record) {
  try {
    await api.post(`/workflow/instance/${record.id}/terminate`)
    message.success('流程实例已终止')
    loadInstances()
  } catch { message.error('操作失败') }
}

async function deleteInstance(record) {
  try {
    await api.delete(`/workflow/instance/${record.id}`)
    message.success('流程实例已删除')
    loadInstances()
  } catch { message.error('删除失败') }
}

function formatTime(t) {
  if (!t) return '—'
  return new Date(t).toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadInstances)
</script>

<style scoped>
.process-monitor {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}
</style>
