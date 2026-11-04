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
                <a-button size="small" type="primary" @click="openHandle(record)">办理</a-button>
                <!-- 只保留转办：Flowable 的委派需要 resolve 交还原办理人，本项目没有这条链路，
                     委派出去的任务谁也办不完（详见 ProcessService#transferTask 的注释） -->
                <a-button size="small" @click="openTransferModal(record)">转办</a-button>
                <a-button size="small" @click="openAddSignerModal(record)">加签</a-button>
              </template>
              <template v-if="activeTab === 'claimable'">
                <a-button size="small" type="primary" @click="claimTask(record)">认领</a-button>
              </template>
              <template v-if="activeTab === 'done'">
                <a-tag color="green">已完成</a-tag>
              </template>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!--
      办理不再用弹窗：点「办理」在当前窗口跳到 /workflow/task/{id} 独立页面（见 openHandle）。
      那边的渲染架构是「通用信息（TaskContextView）+ 任务表单模板（TaskFormRenderer + taskForms/）」，
      这里不保留第二份实现 —— 也不参与表单渲染。
    -->

    <!-- 转办弹窗：选人而不是手打 username —— 手打时拼错一个字，任务就指给了一个不存在的账号 -->
    <a-modal v-model:visible="showTransferModal" title="转办任务" @ok="transferTask" width="420px">
      <a-form layout="vertical">
        <a-form-item label="新负责人" required>
          <a-select
            v-model:value="transferTarget"
            show-search
            allow-clear
            :options="userOptions"
            :filter-option="filterUser"
            :loading="userLoading"
            placeholder="搜索姓名或账号"
            style="width:100%"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!--
      加签弹窗：语义由后端按节点类型决定 —— 会签节点是"追加一个实例，必须办完"，
      单实例是"转候选，谁先办谁算过"。两种后果差别很大，弹窗里先都说清。
    -->
    <a-modal v-model:visible="showAddSignerModal" title="加签" @ok="addSigner" width="460px">
      <a-alert
        type="info"
        show-icon
        style="margin-bottom: 12px"
        message="加签是「加人」，不是「换人」"
        description="会签节点：追加一名会签人，所有人都办完本节点才结束；普通节点：任务转为候选，您和加签人谁先办谁算过。"
      />
      <a-form layout="vertical">
        <a-form-item label="加签人" required>
          <a-select
            v-model:value="addSignerTarget"
            show-search
            allow-clear
            :options="userOptions"
            :filter-option="filterUser"
            :loading="userLoading"
            placeholder="搜索姓名或账号"
            style="width:100%"
          />
        </a-form-item>
      </a-form>
    </a-modal>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import api from '@/api'
import EntityRefLabel from '@/components/EntityRefLabel.vue'
import { useEntityLabels } from '@/composables/useEntityLabels'
import { loadOptions } from '@/flow-designer/app/panel/options'
import { useRouter } from 'vue-router'
// 办理表单（节点表单派发、槽位模型、提交）整体搬到了 components/task/ 下的渲染架构 ——
// 办理现在是独立页面，那份实现只有一处，这里不再引用表单注册表与槽位模型。

const router = useRouter()

/** 行的「业务对象」标签：与流程监控同一份回查与拼装（后端只给实体引用，编码/名称/状态前端回查） */
const { attach: attachBiz } = useEntityLabels()


const activeTab = ref('todo')
const tasks = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const stats = reactive({ todoCount: 0, doneCount: 0, claimableCount: 0, overdueCount: 0 })

// 弹窗状态（办理已改为独立页面，这里只剩转办 / 加签）
const showTransferModal = ref(false)
const showAddSignerModal = ref(false)
const addSignerTarget = ref('')
const currentTaskRecord = ref(null)
const transferTarget = ref('')

const baseColumns = [
  { title: '任务 / 业务对象 / 流程', key: 'process' },
  { title: '负责人', key: 'assignee', width: 120 },
  { title: '发起时间', key: 'createTime', width: 160 },
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
 * 办理：跳到独立页面 /workflow/task/{id}（仍是独立页面，不是弹窗）。
 *
 * <p><b>2026-10-26 改为当前窗口内跳转</b>（用户要求）：原来 {@code window.open(..., '_blank')}。
 * 新开窗口的理由是"办理时要比对对象详情/图纸"，但实际使用里它带来三个麻烦 ——
 * 弹窗拦截（点了没反应）、返回时找不到列表、多条任务开一堆窗口。
 * 现在走路由，办理页自带「返回」，浏览器后退也能回列表。
 */
function openHandle(record) {
  if (!record?.id) return
  router.push(`/workflow/task/${record.id}`)
}

/**
 * 人员下拉选项（value = username，label = 姓名）。
 *
 * <p>原来两个弹窗都是手打 username 的输入框：拼错一个字，任务就指给了一个不存在的账号，
 * 而且校验要到提交时才发现。这里复用流程设计器那份 {@code loadOptions('USERS')}
 * —— 它已经把「username 才是 Flowable 的 assignee 口径」这件事收口好了，还带模块级缓存，
 * 第二次打开弹窗不会再请求。
 */
const userOptions = ref([])
const userLoading = ref(false)

async function ensureUserOptions() {
  if (userOptions.value.length) return
  userLoading.value = true
  try {
    userOptions.value = await loadOptions('USERS')
  } catch {
    // 选人下拉没有候选就没法选人，必须说清楚，而不是让人对着空下拉发呆
    userOptions.value = []
    message.warning('人员清单加载失败，请关闭后重试')
  } finally {
    userLoading.value = false
  }
}

/** 姓名与账号都能命中（有人按姓名找、有人按 Kiween.Hu 找） */
function filterUser(input, option) {
  const keyword = String(input || '').trim().toLowerCase()
  if (!keyword) return true
  return String(option?.label || '').toLowerCase().includes(keyword)
    || String(option?.value || '').toLowerCase().includes(keyword)
}

function openTransferModal(record) {
  transferTarget.value = ''
  currentTaskRecord.value = record
  showTransferModal.value = true
  ensureUserOptions()
}

async function transferTask() {
  if (!transferTarget.value) { message.warning('请选择新责任人'); return }
  try {
    await api.post(`/workflow/task/${currentTaskRecord.value.id}/transfer`, { targetAssignee: transferTarget.value })
    message.success('转办成功')
    showTransferModal.value = false
    loadTasks()
  } catch { message.error('转办失败') }
}

function openAddSignerModal(record) {
  addSignerTarget.value = ''
  currentTaskRecord.value = record
  showAddSignerModal.value = true
  ensureUserOptions()
}

/**
 * 加签。后端按节点类型返回两种模式：
 * - COUNTERSIGN（会签节点）：往多实例追加一个实例，我的待办还在，新人也必须办完；
 * - OR_SIGN（普通节点）：任务转候选，我的待办会消失、进「可认领」—— 提示必须说清，否则像丢了任务。
 */
async function addSigner() {
  if (!addSignerTarget.value) { message.warning('请选择加签人'); return }
  // 响应拦截器对 HTTP 错误 / 业务 code!=200 都已弹过提示，这里不再重复弹，
  // 也绝不能在失败时继续走"加签成功"的提示
  const res = await api.post(`/workflow/task/${currentTaskRecord.value.id}/add-signer`, {
    targetAssignee: addSignerTarget.value,
  }).catch(() => null)
  if (!res || res.code !== 200) return

  if (res.data?.mode === 'COUNTERSIGN') {
    const total = res.data.participantCount
    message.success(
      total > 0
        ? `已加入会签：本节点共 ${total} 人，全部办理完才结束`
        : '已加入会签：所有人都办理完，本节点才结束',
    )
  } else {
    message.success('加签成功：任务已转为候选，您可在「可认领」里继续办理')
  }
  showAddSignerModal.value = false
  loadTasks()
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
