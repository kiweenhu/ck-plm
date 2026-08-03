<template>
  <div class="task-center">
    <div class="page-header">
      <h2>任务中心</h2>
      <a-space>
        <a-badge :count="stats.todoCount" :overflowCount="999">
          <a-tag color="blue">待办</a-tag>
        </a-badge>
        <a-badge :count="stats.claimableCount" :overflowCount="999">
          <a-tag color="orange">可认领</a-tag>
        </a-badge>
        <a-badge :count="stats.overdueCount" :overflowCount="999">
          <a-tag color="red">逾期</a-tag>
        </a-badge>
        <a-badge :count="stats.doneCount" :overflowCount="999">
          <a-tag color="green">已办</a-tag>
        </a-badge>
      </a-space>
    </div>

    <a-card>
      <a-tabs v-model:activeKey="activeTab" @change="onTabChange">
        <a-tab-pane key="todo" tab="待办任务" />
        <a-tab-pane key="claimable" tab="可认领任务" />
        <a-tab-pane key="done" tab="已办任务" />
      </a-tabs>

      <a-table :columns="currentColumns" :dataSource="tasks" :loading="loading" rowKey="id"
        :pagination="{ current: page, pageSize: size, total: total, showSizeChanger: false, onChange: onPageChange }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'process'">
            <div>
              <div style="font-weight:500;">{{ record.name }}</div>
              <div style="font-size:12px;color:#999;">{{ record.processDefinitionName || '-' }}</div>
            </div>
          </template>
          <template v-if="column.key === 'assignee'">
            {{ record.assignee || '—' }}
          </template>
          <template v-if="column.key === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
          <template v-if="column.key === 'dueDate'">
            <span :style="{ color: isOverdue(record.dueDate) ? '#f5222d' : '' }">
              {{ record.dueDate ? formatTime(record.dueDate) : '—' }}
            </span>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <template v-if="activeTab === 'todo'">
                <a-button size="small" type="primary" @click="openCompleteModal(record)">办理</a-button>
                <a-button size="small" @click="openDelegateModal(record)">委派</a-button>
                <a-button size="small" @click="openTransferModal(record)">转办</a-button>
              </template>
              <template v-if="activeTab === 'claimable'">
                <a-button size="small" type="primary" @click="claimTask(record)">认领</a-button>
              </template>
              <template v-if="activeTab === 'done'">
                <a-tag color="green">已完成</a-tag>
              </template>
              <a-button size="small" @click="viewComments(record)">评论</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 办理弹窗 -->
    <a-modal v-model:visible="showCompleteModal" title="办理任务" @ok="completeTask" width="520px">
      <a-form layout="vertical">
        <a-form-item label="审批动作">
          <a-radio-group v-model:value="completeForm.action">
            <a-radio value="approve">同意</a-radio>
            <a-radio value="reject">驳回</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="审批意见">
          <a-textarea v-model:value="completeForm.comment" :rows="3" placeholder="请输入审批意见..." />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 委派弹窗 -->
    <a-modal v-model:visible="showDelegateModal" title="委派任务" @ok="delegateTask" width="400px">
      <a-form layout="vertical">
        <a-form-item label="受托人用户名" required>
          <a-input v-model:value="delegateTarget" placeholder="请输入受托人用户名" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 转办弹窗 -->
    <a-modal v-model:visible="showTransferModal" title="转办任务" @ok="transferTask" width="400px">
      <a-form layout="vertical">
        <a-form-item label="新负责人用户名" required>
          <a-input v-model:value="transferTarget" placeholder="请输入新负责人用户名" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 评论弹窗 -->
    <a-modal v-model:visible="showCommentModal" title="任务评论" :footer="null" width="520px">
      <div style="margin-bottom:12px;max-height:300px;overflow:auto;">
        <div v-for="c in comments" :key="c.id" style="padding:8px 0;border-bottom:1px solid #f0f0f0;">
          <div style="display:flex;justify-content:space-between;">
            <strong>{{ c.userId || '系统' }}</strong>
            <span style="color:#999;font-size:12px;">{{ formatTime(c.time) }}</span>
          </div>
          <div style="margin-top:4px;color:#555;">{{ c.message }}</div>
        </div>
        <a-empty v-if="comments.length === 0" description="暂无评论" />
      </div>
      <a-space style="width:100%;">
        <a-input v-model:value="newComment" placeholder="输入评论..." style="flex:1;" @pressEnter="addComment(currentTaskRecord)" />
        <a-button type="primary" @click="addComment(currentTaskRecord)">发送</a-button>
      </a-space>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import api from '@/api'

const activeTab = ref('todo')
const tasks = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const stats = reactive({ todoCount: 0, doneCount: 0, claimableCount: 0, overdueCount: 0 })

// 弹窗状态
const showCompleteModal = ref(false)
const showDelegateModal = ref(false)
const showTransferModal = ref(false)
const showCommentModal = ref(false)
const currentTaskRecord = ref(null)
const comments = ref([])
const newComment = ref('')
const delegateTarget = ref('')
const transferTarget = ref('')
const completeForm = reactive({ action: 'approve', comment: '' })

const baseColumns = [
  { title: '任务名称 / 所属流程', key: 'process' },
  { title: '负责人', key: 'assignee', width: 120 },
  { title: '创建时间', key: 'createTime', width: 160 },
  { title: '截止时间', key: 'dueDate', width: 160 },
  { title: '操作', key: 'action', width: 280 }
]

const currentColumns = computed(() => {
  if (activeTab.value === 'done') {
    return baseColumns.filter(c => c.key !== 'assignee')
  }
  return baseColumns
})

const apiMap = {
  todo: '/workflow/task/todo',
  done: '/workflow/task/done',
  claimable: '/workflow/task/claimable'
}

async function loadTasks() {
  loading.value = true
  try {
    const res = await api.get(apiMap[activeTab.value], { params: { page: page.value, size: size.value } })
    if (res.code === 200) {
      tasks.value = res.data || []
      total.value = tasks.value.length >= size.value ? (page.value * size.value + 1) : page.value * size.value
    }
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await api.get('/workflow/task/stats')
    if (res.code === 200 && res.data) {
      Object.assign(stats, res.data)
    }
  } catch { /* ignore */ }
}

function onTabChange() {
  page.value = 1
  loadTasks()
}

function onPageChange(p) {
  page.value = p
  loadTasks()
}

async function claimTask(record) {
  try {
    await api.post(`/workflow/task/${record.id}/claim`)
    message.success('任务认领成功')
    loadTasks()
    loadStats()
  } catch { message.error('认领失败') }
}

function openCompleteModal(record) {
  currentTaskRecord.value = record
  completeForm.action = 'approve'
  completeForm.comment = ''
  showCompleteModal.value = true
}

async function completeTask() {
  try {
    await api.post(`/workflow/task/${currentTaskRecord.value.id}/complete`, {
      action: completeForm.action,
      comment: completeForm.comment
    })
    message.success(completeForm.action === 'approve' ? '审批通过' : '已驳回')
    showCompleteModal.value = false
    loadTasks()
    loadStats()
  } catch { message.error('操作失败') }
}

function openDelegateModal(record) {
  delegateTarget.value = ''
  currentTaskRecord.value = record
  showDelegateModal.value = true
}

async function delegateTask() {
  if (!delegateTarget.value) { message.warning('请输入受托人'); return }
  try {
    await api.post(`/workflow/task/${currentTaskRecord.value.id}/delegate`, { targetAssignee: delegateTarget.value })
    message.success('委派成功')
    showDelegateModal.value = false
    loadTasks()
  } catch { message.error('委派失败') }
}

function openTransferModal(record) {
  transferTarget.value = ''
  currentTaskRecord.value = record
  showTransferModal.value = true
}

async function transferTask() {
  if (!transferTarget.value) { message.warning('请输入新责任人'); return }
  try {
    await api.post(`/workflow/task/${currentTaskRecord.value.id}/transfer`, { targetAssignee: transferTarget.value })
    message.success('转办成功')
    showTransferModal.value = false
    loadTasks()
  } catch { message.error('转办失败') }
}

async function viewComments(record) {
  currentTaskRecord.value = record
  try {
    const res = await api.get(`/workflow/task/${record.id}/comment`)
    comments.value = (res.code === 200 && res.data) ? res.data : []
  } catch {
    comments.value = []
  }
  newComment.value = ''
  showCommentModal.value = true
}

async function addComment(record) {
  if (!newComment.value.trim()) return
  try {
    await api.post(`/workflow/task/${record.id}/comment`, { comment: newComment.value })
    newComment.value = ''
    await viewComments(record)
  } catch { message.error('添加评论失败') }
}

function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  return d.toLocaleString('zh-CN', { hour12: false })
}

function isOverdue(dueDate) {
  if (!dueDate) return false
  return new Date(dueDate) < new Date()
}

onMounted(() => {
  loadTasks()
  loadStats()
})
</script>

<style scoped>
.task-center {
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
