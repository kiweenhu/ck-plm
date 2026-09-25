<template>
  <div class="home-page">
    <!-- 欢迎区 -->
    <div class="welcome-row">
      <div class="welcome-text">
        <h2>你好，{{ userStore.userDisplayName }}</h2>
        <span>{{ currentDate }}</span>
      </div>

      <!-- 右侧速览：个人中心最常问的两个数（在等我的事 / 我锁着的对象） -->
      <div class="welcome-stats">
        <div class="welcome-stat" title="去任务中心办理" @click="goTaskCenter">
          <span class="welcome-stat-value welcome-stat-value-todo">{{ todoCount }}</span>
          <span class="welcome-stat-label">
            待办任务
            <em v-if="overdueCount" class="welcome-stat-overdue">逾期 {{ overdueCount }}</em>
          </span>
        </div>
        <div class="welcome-stat" title="查看我的检出" @click="scrollToCheckouts">
          <span class="welcome-stat-value welcome-stat-value-checkout">{{ checkoutCount }}</span>
          <span class="welcome-stat-label">我的检出</span>
        </div>
      </div>
    </div>

    <!-- 两栏：左 3/5（我经手的东西）+ 右 2/5（我最近待过的地方）
         左列按"该我动手的紧急度"排序：等我办的（待办）→ 我锁着的（检出）→ 我动过的（对象） -->
    <a-row :gutter="16" class="main-row">
      <!-- ===== 左栏 3/5 =====
           用 flex 百分比而不是 :span：24 栅格表达不了 3/5（14/24=58.3%），
           而 :flex="3" 会被解析成 "3 3 auto"（basis 自动，比例随内容漂）→ 都不是精确 3:2 -->
      <a-col :flex="'60%'" class="col-stack">
        <!-- 我的待办：左栏第一张 —— 要"我"动手的事排最前 -->
        <MyTodoTasks :limit="5" />

        <a-card id="my-checkouts" size="small" class="section-card">
          <template #title>
            <LockOutlined style="color:#fa8c16;margin-right:4px" />我的检出
            <a-tag v-if="myCheckouts.length > 0" color="orange" size="small" style="margin-left:8px">{{ myCheckouts.length }}</a-tag>
          </template>
          <a-table
            :columns="checkoutColumns"
            :data-source="myCheckouts"
            :pagination="false"
            size="small"
            row-key="oid"
            :loading="checkoutLoading"
            :locale="{ emptyText: '暂无检出对象' }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'code'">
                <a-tag color="blue" size="small">{{ record.code || '-' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'name'">
                <a @click="goToEntity(record)">{{ record.name || '-' }}</a>
              </template>
              <template v-else-if="column.key === 'entityType'">
                <a-tag :color="entityTypeColor(record.entityType)" size="small">{{ record.entityTypeName || record.entityType }}</a-tag>
              </template>
            </template>
          </a-table>
        </a-card>

        <!-- 我操作的对象（最近创建/修改） -->
        <MyRecentObjects :limit="20" />
      </a-col>

      <!-- ===== 右栏 2/5 ===== -->
      <a-col :flex="'40%'" class="col-stack">
        <a-card size="small" class="section-card">
          <template #title>
            <EyeOutlined class="card-title-icon" />最近访问
          </template>
          <template #extra>
            <a-button type="link" size="small">查看全部</a-button>
          </template>
          <!--
            2/5 窄栏里排不下"名称 / 类型 / 时间"三列（时间列会被挤到最右侧、还常折行）。
            改成：名称一行，类型 + 相对时间作为第二行的灰字（悬停显示精确时间）。
            扫读顺序变成"去了哪、什么时候"，一行一个人，列头也就不需要了。
          -->
          <a-table
            :columns="accessColumns"
            :data-source="recentAccess.slice(0, RECENT_LIMIT)"
            :pagination="false"
            :show-header="false"
            size="small"
            :row-key="accessRowKey"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'name'">
                <div class="access-item">
                  <a class="access-name" @click="handleClickAccess(record)">{{ record.name }}</a>
                  <span class="access-meta">
                    <a-tag :color="record.typeColor" size="small">{{ record.type }}</a-tag>
                    <a-tooltip :title="fmtTime(record.time, true)">
                      <span class="access-time">{{ record.displayTime }}</span>
                    </a-tooltip>
                  </span>
                </div>
              </template>
            </template>
          </a-table>
        </a-card>

        <a-card size="small" class="section-card">
          <template #title>
            <ThunderboltOutlined class="card-title-icon card-title-icon-warn" />最近操作
          </template>
          <template #extra>
            <a-button type="link" size="small" @click="$router.push('/system/log')">全部日志</a-button>
          </template>
          <!-- 与「最近访问」同一套排法：一行一件事 —— 操作名一行，目标 + 相对时间做第二行灰字。
               颜色（登录绿 / 检出橙 / 删除红…）保留在操作名前的小圆点上，不再靠时间轴的颜色 -->
          <a-table
            :columns="operationColumns"
            :data-source="recentOperations.slice(0, RECENT_LIMIT)"
            :pagination="false"
            :show-header="false"
            size="small"
            :row-key="opRowKey"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <div class="access-item">
                  <span class="access-name">
                    <i class="op-dot" :style="{ background: record.color }"></i>{{ record.action }}
                  </span>
                  <span class="access-meta">
                    <span class="op-target">{{ record.target || '系统' }}</span>
                    <span class="access-time">{{ record.displayTime }}</span>
                  </span>
                </div>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { message } from 'ant-design-vue'
import { EyeOutlined, LockOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { getRecentAccess, getRecentOperations, recordAccess, fetchFromServer } from '@/composables/useActivity'
import { getMyCheckouts, getMyTaskStats } from '@/api'
import MyTodoTasks from '@/components/MyTodoTasks.vue'
import MyRecentObjects from '@/components/MyRecentObjects.vue'

const router = useRouter()
const userStore = useUserStore()

const currentDate = computed(() => {
  const now = new Date()
  const weekMap = ['日', '一', '二', '三', '四', '五', '六']
  return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 星期${weekMap[now.getDay()]}`
})

// 单列即可：类型与时间都并进这一行的第二个小行（见模板注释）
const accessColumns = [
  { title: '名称', dataIndex: 'name', key: 'name' }
]

// 最近操作同法：一行一件事，列头隐藏
const operationColumns = [
  { title: '操作', key: 'action' }
]

/**
 * 右栏「最近访问 / 最近操作」各自显示前 4 条。
 *
 * <p>取同一个值不是偷懒：这两张卡片并排，条数不同就会一高一矮；
 * 数据侧本来就各取了 20 条，这里只决定"露出多少"，更多记录从卡片右上进去看。
 */
const RECENT_LIMIT = 4

// 行 key：访问项没有 id（用路径），远端流水也没有 id（用动作 + 时间）——
// 不给稳的 key，表格会退化成按下标复用行，合并本地/远端数据后容易串行
const accessRowKey = (record) => record.path || record.name
const opRowKey = (record) => record.id || `${record.action}-${record.time}`

const recentAccess = computed(() => getRecentAccess())
const recentOperations = computed(() => getRecentOperations())

const handleClickAccess = (record) => {
  if (record.path) router.push(record.path)
  else message.info(`跳转到 ${record.type}: ${record.name}`)
}

const checkoutLoading = ref(false)
const myCheckouts = ref([])

/** 顶部速览：待办数取/统计接口（与任务中心、待办卡片同一口径）；检出数直接用下面那张表的数据 */
const todoCount = ref(0)
const overdueCount = ref(0)
const checkoutCount = computed(() => myCheckouts.value.length)

async function loadStats() {
  try {
    const res = await getMyTaskStats()
    if (res?.code === 200 && res.data) {
      todoCount.value = res.data.todoCount || 0
      overdueCount.value = res.data.overdueCount || 0
    }
  } catch {
    // 统计拿不到就显示 0：顶部速览不该把页面其余内容拖下水
  }
}

function goTaskCenter() {
  router.push('/workflow/task')
}

/** 检出数点了就地滚到「我的检出」卡片 —— 本来就在同屏，跳页反而多一步 */
function scrollToCheckouts() {
  document.getElementById('my-checkouts')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

// 列宽按"左栏只占 3/5"来配：编码/类型这种标识类窄一点，名称留出弹性
const checkoutColumns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 110 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '类型', dataIndex: 'entityType', key: 'entityType', width: 70 },
  { title: '检出时间', dataIndex: 'checkedOutAt', key: 'checkedOutAt', width: 140 }
]

function entityTypeColor(type) {
  const map = { DOCUMENT: 'blue', PART: 'purple' }
  return map[type] || 'default'
}

function goToEntity(record) {
  if (record.linkPath) router.push(record.linkPath)
  else message.info(`尚未配置跳转: ${record.name}`)
}

/**
 * 时间格式化。
 *
 * <p>默认到<b>分钟</b>：带秒会在窄栏里折成两行（"2026-09-10" / "22:38:42"）。
 * 需要精确时刻时（如悬停提示）传 {@code withSeconds}。
 */
function fmtTime(t, withSeconds = false) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) {
    return String(t).substring(0, withSeconds ? 19 : 16).replace('T', ' ')
  }
  const p = (n) => String(n).padStart(2, '0')
  const base = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  return withSeconds ? `${base}:${p(d.getSeconds())}` : base
}

async function loadMyCheckouts() {
  checkoutLoading.value = true
  try {
    const res = await getMyCheckouts()
    if (res.code === 200) {
      myCheckouts.value = (res.data || []).map(c => ({
        ...c,
        checkedOutAt: fmtTime(c.checkedOutAt)
      }))
    }
  } catch { myCheckouts.value = [] }
  finally { checkoutLoading.value = false }
}

onMounted(async () => {
  await fetchFromServer()
  recordAccess({ name: '个人中心', type: '系统', path: '/home' })
  loadMyCheckouts()
  loadStats()
})
</script>

<style scoped>
.home-page {
  max-width: 100%;
}

/* 欢迎区 */
.welcome-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 20px;
  background: linear-gradient(135deg, #f0f5ff 0%, #e6f4ff 50%, #f6ffed 100%);
  border-radius: 8px;
  border: 1px solid #e6f0ff;
}

.welcome-text h2 {
  margin: 0 0 2px;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.welcome-text span {
  font-size: 12px;
  color: #8c8c8c;
}

/* 顶部速览：两个数字块，白底半透明压在渐变条上，不抢欢迎语的主次 */
.welcome-stats {
  display: flex;
  gap: 12px;
}

.welcome-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 96px;
  padding: 6px 16px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid #e6f0ff;
  border-radius: 6px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}

.welcome-stat:hover {
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.15);
  transform: translateY(-1px);
}

.welcome-stat-value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.3;
}

.welcome-stat-value-todo {
  color: #1677ff;
}

.welcome-stat-value-checkout {
  color: #fa8c16;
}

.welcome-stat-label {
  font-size: 12px;
  color: #595959;
}

.welcome-stat-overdue {
  font-style: normal;
  color: #ff4d4f;
  margin-left: 4px;
}

/* 卡片 */
.main-row {
  margin-bottom: 0;
}

/*
 * 每一栏内部纵向排列：卡片之间的间距由这一处统一给（gap），
 * 不再让每张卡片各自 margin-top —— 那种写法一换栏就得重算间距。
 */
.col-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 最近访问：一行一个人 —— 名称一行，类型 + 相对时间做第二行灰字 */
.access-item {
  display: flex;
  flex-direction: column;
  line-height: 1.6;
}

.access-name {
  font-size: 13px;
  color: #262626;
}

.access-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8c8c8c;
}

.access-time {
  color: #8c8c8c;
  cursor: default;
}

.section-card {
  border-radius: 8px;
}

/* 卡片标题图标：与「我的检出 / 我的待办 / 我操作的对象」保持同一种呈现 */
.card-title-icon {
  color: #1677ff;
  margin-right: 4px;
}

.card-title-icon-warn {
  color: #fa8c16;
}

/* 最近操作：操作名前的颜色点（登录绿 / 检出橙 / 删除红…），
   代替原来时间轴的颜色语义 —— 一行一件事之后，时间轴没了，颜色信息不能跟着丢 */
.op-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}

.op-target {
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

</style>
