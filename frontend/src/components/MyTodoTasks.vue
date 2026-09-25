<template>
  <a-card size="small" class="todo-card">
    <template #title>
      <ClockCircleOutlined class="todo-title-icon" />
      我的待办任务
      <a-tag v-if="todoCount" color="blue" size="small" class="todo-title-tag">{{ todoCount }}</a-tag>
      <a-tag v-if="stats.overdueCount" color="red" size="small" class="todo-title-tag">
        逾期 {{ stats.overdueCount }}
      </a-tag>
    </template>
    <template #extra>
      <a-button type="link" size="small" @click="goTaskCenter">查看全部</a-button>
    </template>

    <a-table
      :columns="columns"
      :data-source="tasks"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="small"
      :locale="{ emptyText: '当前没有待办任务' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'task'">
          <div class="todo-task">
            <span class="todo-task-name">{{ record.name || '任务' }}</span>
            <span class="todo-task-flow">{{ record.processDefinitionName || '-' }}</span>
          </div>
        </template>

        <template v-else-if="column.key === 'createTime'">
          {{ fmtTime(record.createTime) }}
        </template>

        <template v-else-if="column.key === 'dueDate'">
          <template v-if="record.dueDate">
            <span :class="{ 'todo-overdue': isOverdue(record) }">{{ fmtTime(record.dueDate) }}</span>
            <a-tag v-if="isOverdue(record)" color="red" size="small" class="todo-overdue-tag">已逾期</a-tag>
          </template>
          <span v-else class="todo-none">—</span>
        </template>

        <template v-else-if="column.key === 'action'">
          <!-- 办理要按节点表单渲染、还带委派/转办/评论，整套在任务中心里；
               这里只做"把人送过去"，不复制一套弹框（复制出来两处必然漂移） -->
          <a-button type="primary" size="small" @click="goTaskCenter">去办理</a-button>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ClockCircleOutlined } from '@ant-design/icons-vue'
import { getMyTaskStats, getMyTodoTasks } from '@/api'

/**
 * 「我的待办任务」卡片 —— 个人中心首屏用。
 *
 * <p>数据源与「任务中心 → 待办任务」是同一个接口（{@code /workflow/task/todo}），
 * 所以两处永远不会对不上；这里只取前 {@code limit} 条，"查看全部 / 去办理"都回到任务中心。
 *
 * <p>数量徽标取<b>统计接口</b>而不是本页条数：卡片只显示 5 条，但用户要知道"总共还欠多少件"。
 */
const props = defineProps({
  /** 卡片里最多列几条（超出部分在任务中心看） */
  limit: { type: Number, default: 5 },
})

const router = useRouter()

const loading = ref(false)
const tasks = ref([])
const stats = ref({ todoCount: 0, claimableCount: 0, overdueCount: 0, doneCount: 0 })

/** 徽标用统计值；统计拿不到时退化为"本页条数"，总比不显示强 */
const todoCount = computed(() => stats.value.todoCount ?? tasks.value.length)

// 列宽按"个人中心左栏 3/5"配：时间列给到秒就太浪费宽度了
const columns = [
  { title: '待办任务 / 所属流程', key: 'task' },
  { title: '到达时间', key: 'createTime', width: 150 },
  { title: '截止时间', key: 'dueDate', width: 165 },
  { title: '操作', key: 'action', width: 90 },
]

async function load() {
  loading.value = true
  try {
    const [listRes, statsRes] = await Promise.all([
      getMyTodoTasks({ page: 1, size: props.limit }),
      getMyTaskStats(),
    ])
    tasks.value = (listRes?.code === 200 && Array.isArray(listRes.data)) ? listRes.data : []
    if (statsRes?.code === 200 && statsRes.data) {
      stats.value = { ...stats.value, ...statsRes.data }
    }
  } catch {
    // 取不到就空列表：个人中心其余卡片不受影响（失败提示由响应拦截器统一给）
    tasks.value = []
  } finally {
    loading.value = false
  }
}

/** 后端 overdue 是本租户口径；这里再按截止时间兜一层，避免时间刚过而标记未刷新 */
function isOverdue(record) {
  if (record?.overdue === true) return true
  return !!record?.dueDate && new Date(record.dueDate).getTime() < Date.now()
}

/** 精确到分钟即可：待办看的是"什么时候到我手上"，秒没有信息量，却要占一列宽度 */
function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return String(t).substring(0, 16).replace('T', ' ')
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function goTaskCenter() {
  router.push('/workflow/task')
}

onMounted(load)

defineExpose({ reload: load })
</script>

<style scoped>
.todo-card {
  border-radius: 8px;
}

.todo-title-icon {
  color: #1677ff;
  margin-right: 4px;
}

.todo-title-tag {
  margin-left: 8px;
}

.todo-task {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
}

.todo-task-name {
  font-size: 13px;
  font-weight: 500;
  color: #262626;
}

.todo-task-flow {
  font-size: 12px;
  color: #8c8c8c;
}

.todo-overdue {
  color: #ff4d4f;
}

.todo-overdue-tag {
  margin-left: 6px;
}

.todo-none {
  color: #bfbfbf;
}
</style>
