<template>
  <!--
    「数模转换中心」—— 跨容器、跨人的全局转换队列。

    为什么需要这个页面：「产品数模」页签是按产品系列/型号组织的，只能回答
    "这个产品有哪些数模"；批量提交上线后，操作员真正要盯的是
    "全公司现在排了多少张、哪些在转、谁提交的那批失败了"，
    按产品一层层点进去看不见队列本身。

    这也是「多人批量提交」的前提：没有全局队列，批量提交只会变成
    "我提交了 30 张，然后不知道它们去哪了"。
  -->
  <div class="cad-center" :class="{ 'is-embedded': embedded }">
    <div class="page-header" :class="{ 'is-embedded': embedded }">
      <!-- 嵌进「业务配置 → 轻量化转换」的页签里时，页签标题已经写着「数模转换」，
           这里再放一个 h2 就是同一句话说两遍；只留刷新按钮，副标题让给页签 -->
      <div v-if="!embedded" class="cc-title">
        <h2>数模转换</h2>
        <span class="cc-subtitle">全公司图纸的轻量化转换队列 —— 排队位次、当前阶段、失败原因都在这里</span>
      </div>
      <a-button size="small" :loading="loading" @click="load()">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <!-- 顶部计数：点一下等于切筛选 —— 看到"失败 3"的第一反应就是想看是哪三条 -->
    <div class="cc-stats">
      <div
        v-for="card in statCards"
        :key="card.key"
        class="cc-stat"
        :class="{ 'is-active': filter === card.filter && card.filter !== '' }"
        @click="switchFilter(card.filter)"
      >
        <div class="cc-stat-value" :class="card.tone">{{ card.value }}</div>
        <div class="cc-stat-label">{{ card.label }}</div>
      </div>
    </div>

    <a-card :body-style="{ padding: '12px 16px 4px' }">
      <div class="cc-filter">
        <!-- 顺序：先"正在发生的事"（进行中 / 成功 / 失败 / 跳过），「全部」放最后。
             2026-10-26 用户要求「全部移动到最后」—— 最常看的几类排前面，混在一起的汇总留在末位；
             默认值仍是 all，不因位置改动而改变进页时看到的内容 -->
        <a-radio-group v-model:value="filter" button-style="solid" @change="onFilterChange">
          <a-radio-button value="active">进行中</a-radio-button>
          <a-radio-button value="success">已成功</a-radio-button>
          <a-radio-button value="failed">失败</a-radio-button>
          <a-radio-button value="skipped">已取消 / 跳过</a-radio-button>
          <a-radio-button value="all">全部</a-radio-button>
        </a-radio-group>
        <a-input-search
          v-model:value="keyword"
          placeholder="文件名 / 工程数据编码或名称"
          allow-clear
          style="width: 300px"
          @search="onFilterChange"
        />
      </div>

      <a-table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        row-key="visOid"
        size="middle"
        :pagination="pagination"
        :scroll="{ x: 1180 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'file'">
            <div class="cc-file" :title="record.sourceFileName || ''">{{ record.sourceFileName || '—' }}</div>
            <div class="cc-sub">
              <span v-if="record.sourceSize">{{ formatSize(record.sourceSize) }}</span>
              <span v-if="record.iterationVersion"> · {{ record.iterationVersion }}</span>
              <span v-if="record.durationMs && record.status === 'SUCCESS'"> · 耗时 {{ formatDuration(record.durationMs) }}</span>
            </div>
          </template>

          <template v-else-if="column.key === 'product'">
            <div class="cc-file">{{ record.containerName || '—' }}</div>
            <div class="cc-sub">{{ containerTypeText(record.containerType) }}</div>
          </template>

          <template v-else-if="column.key === 'doc'">
            <div class="cc-file mono">{{ record.engDocNumber || '—' }}</div>
            <div class="cc-sub" :title="record.engDocName || ''">{{ record.engDocName || '' }}</div>
          </template>

          <template v-else-if="column.key === 'creator'">
            {{ record.creator || '—' }}
          </template>

          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusMeta(record).color">{{ statusMeta(record).text }}</a-tag>
          </template>

          <template v-else-if="column.key === 'progress'">
            <!-- 进行中：进度条 + 阶段（阶段由转换器上报，见 CadConvertProgress 的说明） -->
            <CadConvertProgress
              v-if="inFlight(record)"
              compact
              :status="record.status"
              :progress="record.progress ?? null"
              :stage="record.stage || ''"
              :queue-position="record.queuePosition ?? null"
              :updated-at="record.updatedAt"
            />
            <div v-else-if="record.status === 'SUCCESS'" class="cc-sub">
              {{ formatCount(record.triangleCount) }} 面片 · {{ record.partCount ?? '—' }} 零件
            </div>
            <div v-else-if="record.status === 'CANCELLED'" class="cc-sub">用户已取消</div>
            <div v-else class="cc-error" :title="record.errorMessage || ''">
              {{ record.errorMessage || '—' }}
            </div>
          </template>

          <template v-else-if="column.key === 'submittedAt'">
            <div>{{ formatTime(record.submittedAt) }}</div>
            <div v-if="record.status === 'SUCCESS'" class="cc-sub">
              完成 {{ formatTime(record.convertedAt) }}
            </div>
          </template>

          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button
                v-if="record.status === 'SUCCESS' && record.iterationOid"
                type="link"
                size="small"
                @click="openViewer(record)"
              >三维浏览</a-button>
              <a-button
                v-if="retryable(record)"
                type="link"
                size="small"
                :loading="retrying === record.visOid"
                @click="retryOne(record)"
              >重新转换</a-button>
              <!-- 取消是批量误提交后的唯一止损手段：排队中的直接出队，转换中的让转换器杀子进程 -->
              <a-button
                v-if="inFlight(record)"
                type="link"
                size="small"
                danger
                :loading="cancelling === record.visOid"
                @click="cancelOne(record)"
              >取消</a-button>
              <a-tooltip v-if="record.status === 'SKIPPED'" title="转换器判定该文件无需/无法转换，重试不会改变结果">
                <span class="cc-sub">不可重试</span>
              </a-tooltip>
            </a-space>
          </template>
        </template>

        <template #emptyText>
          <a-empty :image="false" description="没有符合条件的转换任务" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { getCadConvertJobs, retryCadVisualization, cancelCadVisualization } from '@/api'
import CadConvertProgress from '@/components/cad/CadConvertProgress.vue'

defineProps({
  /**
   * 嵌入模式：作为「业务配置 → 轻量化转换」的第一个页签使用时置 true。
   * 只影响"页头是否重复显示标题"，取数与轮询逻辑一字不改 —— 两种用法是同一个组件，
   * 免得队列口径在两处各写一遍、慢慢漂移。
   */
  embedded: { type: Boolean, default: false },
})

const router = useRouter()

const rows = ref([])
const stats = ref({})
const loading = ref(false)
const filter = ref('all')
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const retrying = ref('')
const cancelling = ref('')

/** 进行中才轮询：队列空了还每 4 秒打一次接口，是纯噪声 */
const POLL_MS = 4000
let pollTimer = null

// 列宽合计 1180 = 表格 scroll.x：再窄就横向滚，但「操作」列固定在右侧 ——
// 窄屏下把"重新转换"裁掉，等于用户对着一条失败记录却点不到按钮
const columns = [
  { title: '文件', key: 'file', width: 220, ellipsis: true },
  { title: '所属产品', key: 'product', width: 130, ellipsis: true },
  { title: '工程数据', key: 'doc', width: 150, ellipsis: true },
  { title: '提交人', key: 'creator', width: 90 },
  { title: '状态', key: 'status', width: 90 },
  { title: '进度 / 结果', key: 'progress', width: 220 },
  { title: '提交时间', key: 'submittedAt', width: 150 },
  { title: '操作', key: 'action', width: 130, fixed: 'right' },
]

const STATUS_META = {
  QUEUED: { text: '排队中', color: 'default' },
  RUNNING: { text: '转换中', color: 'processing' },
  SUCCESS: { text: '已生成', color: 'success' },
  FAILED: { text: '失败', color: 'error' },
  TIMEOUT: { text: '超时', color: 'error' },
  SKIPPED: { text: '已跳过', color: 'warning' },
  CANCELLED: { text: '已取消', color: 'default' },
}

function statusMeta(record) {
  return STATUS_META[record?.status] || { text: record?.status || '—', color: 'default' }
}

function inFlight(record) {
  return record?.status === 'QUEUED' || record?.status === 'RUNNING'
}

/**
 * 失败 / 超时 / 已取消 都可以重试。
 * SKIPPED 不行 —— 那是"转换器说这文件不该转"，重试只是白烧 CPU。
 */
function retryable(record) {
  return record?.status === 'FAILED'
    || record?.status === 'TIMEOUT'
    || record?.status === 'CANCELLED'
}

const statCards = computed(() => [
  { key: 'queued', label: '排队中', value: stats.value.queued ?? 0, filter: 'active', tone: 'tone-blue' },
  { key: 'running', label: '转换中', value: stats.value.running ?? 0, filter: 'active', tone: 'tone-blue' },
  { key: 'successToday', label: '今日成功', value: stats.value.successToday ?? 0, filter: 'success', tone: 'tone-green' },
  { key: 'failed', label: '失败 / 超时', value: stats.value.failed ?? 0, filter: 'failed', tone: 'tone-red' },
  // 「已跳过」与「已取消」都是"不再处理"，合并一张卡：卡片再多就看不清重点了。
  // 后端 filter=skipped 同时返回这两种状态
  {
    key: 'closed',
    label: '已取消 / 跳过',
    value: (stats.value.skipped ?? 0) + (stats.value.cancelled ?? 0),
    filter: 'skipped',
    tone: 'tone-gray',
  },
])

const pagination = computed(() => ({
  current: page.value,
  pageSize: size.value,
  total: total.value,
  showSizeChanger: false,
  showTotal: (t) => `共 ${t} 条`,
  onChange: onPageChange,
}))

/**
 * 拉一页。
 * @param {boolean} silent 轮询时静默刷新：不显示 loading，避免表格每隔 4 秒闪一次
 */
async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await getCadConvertJobs({
      filter: filter.value === 'all' ? undefined : filter.value,
      keyword: keyword.value?.trim() || undefined,
      page: page.value,
      size: size.value,
    })
    if (res.code !== 200) return
    const data = res.data || {}
    rows.value = data.list || []
    total.value = data.total || 0
    stats.value = data.stats || {}
    // 删掉几条后当前页可能已经越界：退一页再拉，别让用户对着空表格以为筛选坏了
    if (!rows.value.length && page.value > 1) {
      page.value -= 1
      await load(silent)
      return
    }
    syncPolling()
  } finally {
    loading.value = false
  }
}

function syncPolling() {
  const active = (stats.value.queued || 0) + (stats.value.running || 0) > 0
  if (active && !pollTimer) {
    pollTimer = setInterval(() => load(true), POLL_MS)
  } else if (!active && pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function onFilterChange() {
  page.value = 1
  load()
}

function switchFilter(value) {
  if (filter.value === value) return
  filter.value = value
  onFilterChange()
}

function onPageChange(p) {
  page.value = p
  load()
}

async function retryOne(record) {
  retrying.value = record.visOid
  try {
    const res = await retryCadVisualization(record.visOid)
    if (res.code !== 200) {
      message.error(res.message || '重试失败')
      return
    }
    message.success('已重新提交转换')
    await load(true)
    syncPolling()
  } finally {
    retrying.value = ''
  }
}

/**
 * 取消一条在途任务。
 *
 * <p>必须二次确认：取消是不可撤销的（记录停在 CANCELLED，要重跑得再点一次重新转换），
 * 而且转换中的任务会被直接杀进程 —— 手滑点错的代价不小。
 */
function cancelOne(record) {
  const running = record.status === 'RUNNING'
  Modal.confirm({
    title: '取消这次转换？',
    content: running
      ? `「${record.sourceFileName || '该文件'}」正在转换（${record.stage || '进行中'}），取消会终止已开始的转换进程。`
      : `「${record.sourceFileName || '该文件'}」还在排队，取消后会从队列中移除。`,
    okText: '取消转换',
    okType: 'danger',
    cancelText: '再等等',
    async onOk() {
      cancelling.value = record.visOid
      try {
        const res = await cancelCadVisualization(record.visOid)
        if (res.code !== 200) {
          message.error(res.message || '取消失败')
          return
        }
        message.success('已取消')
        await load(true)
      } finally {
        cancelling.value = ''
      }
    },
  })
}

/** 三维浏览走独立页（与「产品数模」列表同一入口，弹窗放不下装配结构树） */
function openViewer(record) {
  router.push({
    name: 'CadViewer',
    params: { iterationOid: record.iterationOid },
    query: {
      code: record.engDocNumber || '',
      title: record.sourceFileName || '',
      version: record.iterationVersion || '',
      fileOid: '',
    },
  })
}

// ---- 展示辅助 ----

function containerTypeText(type) {
  if (type === 'PRODUCT_LINE') return '产品系列'
  if (type === 'PRODUCT_MODEL') return '产品型号'
  return ''
}

function formatSize(bytes) {
  const n = Number(bytes)
  if (!Number.isFinite(n) || n <= 0) return '—'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

function formatCount(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : '—'
}

function formatDuration(ms) {
  const n = Number(ms)
  if (!Number.isFinite(n) || n <= 0) return '—'
  if (n < 1000) return `${n} ms`
  const s = Math.round(n / 1000)
  if (s < 60) return `${s} 秒`
  return `${Math.floor(s / 60)} 分 ${s % 60} 秒`
}

/** 后端给的是 LocalDateTime（可能是 "yyyy-MM-dd HH:mm:ss" 或数组），统一按"原样截断"最稳 */
function formatTime(value) {
  if (!value) return '—'
  if (Array.isArray(value) && value.length >= 5) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = value
    const pad = (v) => String(v).padStart(2, '0')
    return `${y}-${pad(mo)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(s)}`
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(load)
onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.cad-center {
  padding: 0 4px;
}
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
/* 嵌入页签时没有左侧标题，刷新按钮靠右对齐（否则会孤零零贴在左边） */
.page-header.is-embedded {
  justify-content: flex-end;
  margin-bottom: 8px;
}
.cad-center.is-embedded {
  padding: 0;
}
.cc-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}
.cc-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.cc-subtitle {
  font-size: 12px;
  color: #8c8c8c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cc-stats {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.cc-stat {
  flex: 1 1 140px;
  padding: 10px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.cc-stat:hover {
  border-color: #91caff;
}
.cc-stat.is-active {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.08);
}
.cc-stat-value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.cc-stat-label {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 2px;
}
.tone-blue { color: #1677ff; }
.tone-green { color: #389e0d; }
.tone-red { color: #cf1322; }
.tone-gray { color: #8c8c8c; }
.cc-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.cc-file {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cc-file.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
.cc-sub {
  font-size: 12px;
  color: #8c8c8c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cc-error {
  font-size: 12px;
  color: #cf1322;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
