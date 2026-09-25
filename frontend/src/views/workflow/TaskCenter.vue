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
            <!-- 一行说清"办的是哪一条"：任务名 → 业务对象（编码/名称/大版本/生命周期状态）
                 → 流程名 · 任务下发时间。原来只有任务名 + 流程名，而同一个流程挂在几十个对象上，
                 两行长得一模一样，得点进去才知道办的是哪个零件 -->
            <div class="tc-cell">
              <div class="tc-task-name">{{ record.name }}</div>
              <div v-if="record._biz" class="tc-biz">
                <EntityRefLabel :label="record._biz" />
              </div>
              <div class="tc-meta">
                <span>{{ record.processDefinitionName || '—' }}</span>
                <template v-if="record.createTime">
                  <span class="tc-meta-sep">·</span>
                  <span>下发 {{ formatTime(record.createTime) }}</span>
                </template>
              </div>
            </div>
          </template>
          <template v-if="column.key === 'assignee'">
            <!-- 显示解析后的人名：assignee 可能是用户名，也可能是人员 oid（按 oid 指派的下游任务） -->
            {{ record.assigneeName || record.assignee || '—' }}
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
                <a-button size="small" type="primary" @click="openHandleWindow(record)">办理</a-button>
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

    <!--
      办理不再用弹窗：点「办理」在新窗口打开 /workflow/task/{id} 独立页面（见 openHandleWindow）。
      那边的渲染架构是「通用信息（TaskContextView）+ 任务表单模板（TaskFormRenderer + taskForms/）」，
      这里不保留第二份实现 —— 也不参与表单渲染。
    -->

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
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import api from '@/api'
import EntityRefLabel from '@/components/EntityRefLabel.vue'
import { useEntityLabels } from '@/composables/useEntityLabels'
// 办理表单（节点表单派发、槽位模型、提交）整体搬到了 components/task/ 下的渲染架构 ——
// 办理现在是独立页面，那份实现只有一处，这里不再引用表单注册表与槽位模型。

/** 行的「业务对象」标签：与流程监控同一份回查与拼装（后端只给实体引用，编码/名称/状态前端回查） */
const { attach: attachBiz } = useEntityLabels()


const activeTab = ref('todo')
const tasks = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const stats = reactive({ todoCount: 0, doneCount: 0, claimableCount: 0, overdueCount: 0 })

// 弹窗状态（办理已改为新窗口的独立页面，这里只剩委派 / 转办 / 评论）
const showDelegateModal = ref(false)
const showTransferModal = ref(false)
const showCommentModal = ref(false)
const currentTaskRecord = ref(null)
const comments = ref([])
const newComment = ref('')
const delegateTarget = ref('')
const transferTarget = ref('')

const baseColumns = [
  { title: '任务 / 业务对象 / 流程', key: 'process' },
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
    const rows = res.code === 200 ? (res.data || []) : []
    // 先算好业务对象再上屏：否则会先闪一下"没有对象信息"的旧样式
    await attachBiz(rows)
    tasks.value = rows
    total.value = rows.length >= size.value ? (page.value * size.value + 1) : page.value * size.value
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

/**
 * 办理：在**新窗口**打开独立页面（形态与业务对象详情页一致），不再用弹窗。
 *
 * <p>为什么换掉弹窗：办理时要一边填表单、一边核对"这条流程走到哪、别人提了什么意见"，
 * 还常要同时开着对象详情/图纸对照 —— 弹窗被表格夹在中间，多窗口对照时互相遮挡；
 * 独立窗口本身是个普通 URL，可以同时开多条任务比对、可以收藏。
 *
 * <p>被浏览器拦截时明确提示（而不是静默什么都不发生 —— 那最容易被当成"点了没反应"）。
 */
function openHandleWindow(record) {
  if (!record?.id) return
  const url = `${window.location.origin}/workflow/task/${record.id}`
  const win = window.open(url, '_blank')
  if (!win) {
    message.warning('浏览器拦截了新窗口，请允许本站弹出窗口后重试')
  }
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

/**
 * 切回本窗口时刷新列表。
 *
 * <p>办理在新窗口里完成，回到任务中心时如果还显示刚办掉的那条，用户会以为"没办成功"。
 * 用 focus 而不是定时轮询：只在真正回来看的时候刷一次，没有无谓请求。
 */
function onWindowFocus() {
  loadTasks()
  loadStats()
}

onMounted(() => {
  loadTasks()
  loadStats()
  window.addEventListener('focus', onWindowFocus)
})

onUnmounted(() => {
  window.removeEventListener('focus', onWindowFocus)
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

/* 第一列三层信息：任务名（要办什么）→ 业务对象（办的是哪一条）→ 流程 · 下发时间 */
.tc-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tc-task-name {
  font-weight: 500;
}

/* 业务对象行：具体的名称/编码/版本/状态样式在 EntityRefLabel 组件里（与流程监控共用） */
.tc-biz {
  font-size: 12px;
}

.tc-meta {
  font-size: 12px;
  color: #999;
}

.tc-meta-sep {
  margin: 0 6px;
}
</style>
