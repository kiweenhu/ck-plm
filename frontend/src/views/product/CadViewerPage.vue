<template>
  <!--
    三维浏览独立页（不是弹窗）。
    装配体一复杂，弹窗的可视区域和结构树宽度都不够用，还会被页面滚动条挤压；
    这里给一个占满内容区的工作台：顶部只留"返回 + 对象标识 + 下载"，
    其余高度全部让给画布。
  -->
  <div class="cvp">
    <div class="cvp-header">
      <div class="cvp-left">
        <a-button type="text" size="small" @click="goBack">
          <ArrowLeftOutlined /> 返回
        </a-button>
        <span class="cvp-divider" />
        <span v-if="docCode" class="cvp-code">{{ docCode }}</span>
        <span class="cvp-name" :title="fileName">{{ fileName }}</span>
        <a-tag v-if="versionText" color="blue">{{ versionText }}</a-tag>
      </div>
      <div class="cvp-right">
        <a-button v-if="sourceFileOid" size="small" @click="downloadSource">
          <DownloadOutlined /> 下载数模
        </a-button>
      </div>
    </div>

    <div class="cvp-body">
      <div v-if="loading" class="cvp-state">
        <a-spin tip="正在读取轻量化产物…" />
      </div>
      <!-- 转换中：给进度条，而不是一句"请稍后刷新本页" —— 让用户手动 F5 是最差的选择 -->
      <div v-else-if="!glbUrl && converting" class="cvp-state">
        <div class="cvp-waiting">
          <CadConvertProgress
            :status="record?.status || 'QUEUED'"
            :progress="record?.progress ?? null"
            :stage="record?.stage || ''"
            :updated-at="record?.updatedAt"
          />
          <div class="cvp-waiting-tip">转换完成后本页会自动显示模型，无需刷新</div>
        </div>
      </div>
      <div v-else-if="!glbUrl" class="cvp-state">
        <a-empty :image="false" :description="emptyText">
          <a-button size="small" @click="goBack">返回数模列表</a-button>
        </a-empty>
      </div>
      <CadViewer
        v-else
        :glb-url="glbUrl"
        :structure-url="structureUrl"
        :stats="stats"
        class="cvp-viewer"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import CadViewer from '@/components/cad/CadViewer.vue'
import CadConvertProgress from '@/components/cad/CadConvertProgress.vue'
import { getLatestCadVisualization, ckFileStreamUrl } from '@/api'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
/** 该迭代最近一次转换记录（CadVisualization） */
const record = ref(null)

const iterationOid = computed(() => String(route.params.iterationOid || ''))
/** 工程数据编码：列表跳转时带过来，直接进 URL 时可能没有 */
const docCode = computed(() => route.query.code || '')
const fileName = computed(() => record.value?.sourceFileName || route.query.title || '三维数模')
const versionText = computed(() => route.query.version || '')
const sourceFileOid = computed(() => record.value?.sourceCkfileOid || route.query.fileOid || '')

const glbUrl = computed(() =>
  record.value?.glbCkfileOid ? ckFileStreamUrl(record.value.glbCkfileOid) : '')
const structureUrl = computed(() =>
  record.value?.structureCkfileOid ? ckFileStreamUrl(record.value.structureCkfileOid) : '')
const stats = computed(() => ({
  triangles: record.value?.triangleCount,
  parts: record.value?.partCount,
  units: record.value?.units
}))

/** 没有 GLB 时把原因说清楚 —— "空空白"最容易让人以为页面坏了 */
const emptyText = computed(() => {
  if (!record.value) return '该数模还没有生成轻量化预览'
  const status = record.value.status
  if (status === 'QUEUED' || status === 'RUNNING') return '轻量化转换进行中，请稍后刷新本页'
  if (status === 'FAILED' || status === 'TIMEOUT') {
    return record.value.errorMessage || '轻量化转换失败，可在数模列表里重新转换'
  }
  if (status === 'SKIPPED') return '该格式不支持轻量化转换（原生数模仅归档，可下载源文件）'
  return '该数模还没有生成轻量化预览'
})

/** 是否正在转换（含排队）—— 决定是给进度条还是给空状态 */
const converting = computed(() =>
  record.value?.status === 'QUEUED' || record.value?.status === 'RUNNING')

/** 轮询：转换期间 2 秒一次，直到拿到终态（或 5 分钟上限，和文档预览弹窗同一口径） */
const CAD_POLL_INTERVAL = 2000
const CAD_POLL_MAX = 150
let pollTimer = null
let pollTicks = 0

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPolling() {
  if (pollTimer) return
  pollTicks = 0
  pollTimer = setInterval(async () => {
    pollTicks++
    if (pollTicks > CAD_POLL_MAX) {
      stopPolling()
      return
    }
    try {
      const res = await getLatestCadVisualization(iterationOid.value)
      if (res.code !== 200 || !res.data) return
      record.value = res.data
      // SUCCESS 后 glbUrl 才有值，模板会自动切到三维画布；失败则交给 emptyText 说原因
      if (!converting.value) stopPolling()
    } catch {
      /* 单次失败不打断，等下一拍 */
    }
  }, CAD_POLL_INTERVAL)
}

onMounted(async () => {
  if (!iterationOid.value) {
    loading.value = false
    return
  }
  try {
    const res = await getLatestCadVisualization(iterationOid.value)
    if (res.code === 200) record.value = res.data
    // 从列表点进来时任务可能已经在跑：直接接上进度，不要等用户自己刷新
    if (converting.value) startPolling()
  } catch (e) {
    /* 错误提示交给 axios 拦截器；本页退化为空状态 */
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(stopPolling)

function goBack() {
  // 从列表点进来的走浏览器返回（保留筛选/分页）；直接粘贴 URL 进来的回产品系列
  if (window.history.length > 1) router.back()
  else router.push('/product')
}

function downloadSource() {
  if (!sourceFileOid.value) return
  window.open(ckFileStreamUrl(sourceFileOid.value), '_blank')
}
</script>

<style scoped>
.cvp {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.cvp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
  flex-wrap: wrap;
}
.cvp-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.cvp-divider {
  width: 1px;
  height: 14px;
  background: #f0f0f0;
}
.cvp-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  color: #1f2937;
}
.cvp-name {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 46vw;
}

.cvp-body {
  flex: 1;
  min-height: 0;
  display: flex;
}
.cvp-viewer {
  flex: 1;
  min-width: 0;
  height: 100%;
}
.cvp-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
}
.cvp-waiting {
  width: 360px;
  max-width: 80%;
  text-align: left;
}
.cvp-waiting-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #bfbfbf;
  text-align: center;
}
</style>
