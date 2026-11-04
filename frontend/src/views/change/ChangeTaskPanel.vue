<template>
  <div class="chg-panel">
    <!-- 口径条（与产品页「物料清单」同一版式）：左边说清这段列表是什么，右边给条数 -->
    <div class="ck-list-bar">
      <span>变更任务（ECT）：一行一项待办；验收完成后，所属变更通告才能关闭</span>
      <span class="ck-list-bar__count">共 {{ total }} 条</span>
    </div>

    <DataTable
      scroll-x="max-content"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      pagination-mode="backend"
      :pagination="{ current: page, pageSize }"
      :total="total"
      search-placeholder="搜索任务编号 / 标题..."
      :search-fields="['code', 'name']"
      row-key="oid"
      size="small"
      empty-text="暂无变更任务"
      @change="handleTableChange"
      @update:search="handleSearch"
    >
      <!-- 状态筛选在页头（ChangeControl 的状态概览标签）；工具栏左侧只留「只看我的」这个口径开关 -->
      <template #toolbar-left>
        <a-checkbox :checked="onlyMine" @change="handleOnlyMineChange">只看我的</a-checkbox>
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'code'">
          <a-tag size="small">{{ record.code }}</a-tag>
        </template>
        <template v-else-if="column.key === 'notice'">
          <span>{{ record.noticeCode || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'taskType'">
          <span>{{ TASK_TYPE_LABEL[record.taskType] || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="CHANGE_TASK_STATUS_COLOR[record.status] || 'default'" size="small">
            {{ CHANGE_TASK_STATUS_LABEL[record.status] || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space size="small">
            <a-button
              v-for="action in availableTaskActions(record.status)"
              :key="action"
              type="link"
              size="small"
              :disabled="taskRequiresOwner(action) && record.assignee !== currentUsername"
              @click="openTransition(record, action)"
            >
              {{ CHANGE_TASK_ACTION_LABEL[action] }}
            </a-button>
          </a-space>
        </template>
      </template>
    </DataTable>

    <a-modal
      v-model:open="flow.visible"
      :title="flow.action ? CHANGE_TASK_ACTION_LABEL[flow.action] : '任务流转'"
      width="480px"
      centered
      ok-text="确定"
      cancel-text="取消"
      :confirm-loading="flow.saving"
      @ok="handleFlow"
    >
      <a-form layout="vertical">
        <a-form-item label="任务">
          <span>{{ flow.code }} · {{ flow.name }}</span>
        </a-form-item>
        <a-form-item label="说明" :required="taskRequiresComment(flow.action)">
          <a-textarea v-model:value="flow.comment" :rows="3" :maxlength="2000" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import DataTable from '@/components/DataTable.vue'
import { listMyChangeTasks, transitionChangeTask } from '@/api'
import {
  CHANGE_TASK_ACTION_LABEL,
  CHANGE_TASK_STATUS_COLOR,
  CHANGE_TASK_STATUS_LABEL,
  TASK_TYPE_LABEL,
  availableTaskActions,
  taskRequiresComment,
  taskRequiresOwner
} from '@/utils/changeFlow'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const currentUsername = computed(() => userStore.username || '')

const columns = [
  { title: '编号', dataIndex: 'code', key: 'code', width: 110 },
  { title: '标题', dataIndex: 'name', key: 'name', width: 260 },
  { title: '所属通告', key: 'notice', width: 130 },
  { title: '类型', key: 'taskType', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '责任人', dataIndex: 'assignee', key: 'assignee', width: 110 },
  { title: '计划完成', dataIndex: 'planEnd', key: 'planEnd', width: 110 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' }
]

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

/**
 * 状态筛选由**页头**的状态概览控制（ChangeControl 的胶囊标签）；「只看我的」也由父级持有 ——
 * 页头的计数要按同一口径取（只看我的 = 数我的任务），子组件自己存状态就同步不过去。
 */
const props = defineProps({
  status: { type: String, default: undefined },
  onlyMine: { type: Boolean, default: true }
})
const emit = defineEmits(['update:status', 'update:onlyMine', 'changed'])

const onlyMine = computed(() => props.onlyMine)
const filters = reactive({ status: props.status || undefined, keyword: '' })

const flow = reactive({ visible: false, saving: false, oid: '', code: '', name: '', action: '', comment: '' })

/** 页头换了筛选（或清空）：同步内部状态并回到第一页重取 */
watch(() => props.status, (value) => {
  const next = value || undefined
  if (next === (filters.status || undefined)) return
  filters.status = next
  page.value = 1
  loadList()
})

/** 「只看我的」切换：交给父级改口径，再由 props 回来触发重取（父级还要按同口径刷新页头计数） */
function handleOnlyMineChange(event) {
  emit('update:onlyMine', event.target.checked)
}

watch(() => props.onlyMine, () => {
  page.value = 1
  loadList()
})

async function loadList() {
  loading.value = true
  try {
    const res = await listMyChangeTasks({
      assignee: onlyMine.value ? currentUsername.value : undefined,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    if (res.code === 200) {
      rows.value = res.data.rows || []
      total.value = res.data.total || 0
      // 列表变了 → 让父级重取页头的状态计数
      emit('changed')
    } else {
      message.error(res.message || '加载失败')
    }
  } catch {
    message.error('加载任务失败')
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  loadList()
}

function handleSearch(value) {
  filters.keyword = value || ''
  reload()
}

function handleTableChange({ pagination }) {
  if (pagination) {
    page.value = pagination.current || 1
    pageSize.value = pagination.pageSize || 20
  }
  loadList()
}

function openTransition(record, action) {
  flow.oid = record.oid
  flow.code = record.code
  flow.name = record.name
  flow.action = action
  flow.comment = ''
  flow.visible = true
}

async function handleFlow() {
  if (taskRequiresComment(flow.action) && !flow.comment.trim()) {
    message.warning('请填写说明')
    return
  }
  flow.saving = true
  try {
    const res = await transitionChangeTask(flow.oid, { action: flow.action, comment: flow.comment })
    if (res.code === 200) {
      message.success('已' + CHANGE_TASK_ACTION_LABEL[flow.action])
      flow.visible = false
      loadList()
    } else {
      message.error(res.message || '流转失败')
    }
  } catch (e) {
    message.error(e?.message || '流转失败')
  } finally {
    flow.saving = false
  }
}

onMounted(loadList)

defineExpose({ reload })
</script>

<style scoped>
.chg-panel { display: flex; flex-direction: column; height: 100%; }
</style>
