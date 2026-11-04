<template>
  <a-drawer
    :title="doc?.name || '文档详情'"
    :open="visible"
    :width="960"
    placement="right"
    @close="close"
    :destroy-on-close="true"
  >
    <!-- 文档信息 -->
    <div class="dv-info-bar">
      <a-descriptions size="small" :column="3" bordered>
        <a-descriptions-item label="编码">{{ doc?.code || '-' }}</a-descriptions-item>
        <a-descriptions-item label="文档类型">{{ doc?.typeDefinitionName || '-' }}</a-descriptions-item>
        <a-descriptions-item label="版本">{{ doc?.displayVersion || '-' }}</a-descriptions-item>
        <a-descriptions-item label="文件名">
          <span v-if="ckFile?.fileName" style="font-size:12px">{{ ckFile.fileName }}</span>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="文件大小">
          <span v-if="ckFile?.fileSize">{{ formatSize(ckFile.fileSize) }}</span>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="检出状态">
          <a-tag v-if="doc?.checkedOut" color="orange" size="small">已检出: {{ doc.checkedOutBy }}</a-tag>
          <a-tag v-else color="green" size="small">已检入</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="生命周期">
          <a-tag :color="statusColor(doc?.statusCode)" size="small">{{ doc?.statusName || doc?.statusCode || '-' }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ doc?.createdAt || '-' }}</a-descriptions-item>
      </a-descriptions>
    </div>

    <!-- 工程数据 → 零件：被哪些零件版本引用（EPMBuildRule 反向视图） -->
    <div v-if="isEngDoc" class="dv-reverse">
      <a-divider orientation="left" class="dv-reverse-divider">
        关联零件（{{ engPartLinks.length }}）
      </a-divider>
      <a-spin :spinning="engPartLinksLoading">
        <a-empty
          v-if="!engPartLinks.length"
          description="尚未被任何零件版本引用"
          :image-style="{ height: '32px' }"
        />
        <a-table
          v-else
          :columns="engPartColumns"
          :data-source="engPartLinks"
          :pagination="false"
          row-key="oid"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'part'">
              <a class="dv-part-link" @click="openPartDetail(record)">{{ record.partNumber || '-' }}</a>
              <span class="dv-muted">{{ record.partName }}</span>
            </template>
            <template v-else-if="column.key === 'partVersion'">
              <span>{{ record.partVersion || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'assocType'">
              <a-tag :color="ASSOC_TYPE_COLOR[record.assocType] || 'default'">
                {{ ASSOC_TYPE_LABEL[record.assocType] || record.assocType }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'buildRules'">
              <a-space v-if="hasBuildRule(record)" size="small" wrap>
                <a-tag v-if="record.buildStructure" color="blue">结构</a-tag>
                <a-tag v-if="record.buildAttribute" color="purple">属性</a-tag>
                <a-tag v-if="record.buildRepresentation" color="geekblue">表示</a-tag>
              </a-space>
              <span v-else class="dv-muted">—</span>
            </template>
          </template>
        </a-table>
      </a-spin>
    </div>

    <a-divider />

    <!-- 文档预览/编辑区域 -->
    <div class="dv-viewer" :class="{ 'dv-viewer--full': isOfficeDoc }">
      <!-- ★ 内容解析/渲染遮罩：数据已就绪、但 vue-office 组件尚未 @rendered（大 PPTX 解析可达数十秒，
           之前这段是黑屏无提示）—— 覆盖在预览内容上，渲染完成后自动消失 -->
      <div v-if="contentLoading" class="dv-content-mask">
        <a-spin size="large" :tip="officeLoadTip" />
      </div>

      <!-- 无主文件 -->
      <template v-if="!doc?.ckfileOid">
        <a-empty description="该文档暂无主文件" />
      </template>

      <!-- 正在加载 -->
      <template v-else-if="fileLoading || officeLoading">
        <a-spin :tip="officeLoading ? officeLoadTip : '正在加载文件...'" />
        <div v-if="officeLoading && ckFile" style="margin-top:12px;color:#999;font-size:12px;text-align:center">
          <p>文件较大时解析需要较长时间，请耐心等待...</p>
          <a-button size="small" type="link" @click="skipPreview">跳过预览，直接下载</a-button>
        </div>
      </template>

      <!-- 加载失败 -->
      <template v-else-if="fileError">
        <a-empty :description="fileError" />
        <div style="text-align:center;margin-top:12px">
          <a :href="previewUrl" target="_blank">
            <a-button size="small">直接下载</a-button>
          </a>
        </div>
      </template>

      <!-- Word 文档 -->
      <template v-else-if="isWord">
        <div class="dv-office-header">
          <a-tag color="blue">{{ fileExtension?.toUpperCase() }}</a-tag>
          <span style="font-size:12px;color:#999">离线预览</span>
          <a-button size="small" @click="downloadFile" style="margin-left:auto">下载</a-button>
        </div>
        <vue-office-docx
          v-if="docData"
          :src="docData"
          class="dv-office-body"
          @rendered="onOfficeRendered"
          @error="handleOfficeError"
        />
      </template>

      <!-- Excel 文档 -->
      <template v-else-if="isExcel">
        <div class="dv-office-header">
          <a-tag color="green">{{ fileExtension?.toUpperCase() }}</a-tag>
          <span style="font-size:12px;color:#999">离线预览</span>
          <a-button size="small" @click="downloadFile" style="margin-left:auto">下载</a-button>
        </div>
        <vue-office-excel
          v-if="docData"
          :src="docData"
          class="dv-office-body"
          @rendered="onOfficeRendered"
          @error="handleOfficeError"
        />
      </template>

      <!-- PPT 文档 -->
      <template v-else-if="isPpt">
        <div class="dv-office-header">
          <a-tag color="orange">{{ fileExtension?.toUpperCase() }}</a-tag>
          <span style="font-size:12px;color:#999">离线预览</span>
          <a-button size="small" @click="downloadFile" style="margin-left:auto">下载</a-button>
        </div>
        <vue-office-pptx
          v-if="docData"
          :src="docData"
          class="dv-office-body"
          @rendered="onOfficeRendered"
          @error="handleOfficeError"
        />
      </template>

      <!-- CAD 三维数模 -->
      <template v-else-if="isCad">
        <div v-if="cadReady" class="dv-cad-body">
          <CadViewer
            :glb-url="cadGlbUrl"
            :structure-url="cadStructureUrl"
            :stats="cadStats"
          />
        </div>
        <div v-else class="dv-cad-entry">
          <template v-if="!cadAvailable">
            <a-empty description="未部署 CAD 转换器，无法生成三维预览">
              <template #children>
                <div style="font-size:12px;color:#999;margin-top:8px">
                  需独立部署转换器（CK-CADForge）并在配置中启用
                </div>
              </template>
            </a-empty>
          </template>
          <a-spin v-else-if="cadWorking && !cadVis" :tip="cadTip" />
          <CadConvertProgress
            v-else-if="cadWorking"
            :status="cadVis?.status || 'QUEUED'"
            :progress="cadVis?.progress ?? null"
            :stage="cadVis?.stage || ''"
            :updated-at="cadVis?.updatedAt"
          />
          <template v-else>
            <a-empty :description="cadMessage || '该数模尚未生成三维预览'">
              <a-button type="primary" size="small" @click="startConvert">生成三维预览</a-button>
            </a-empty>
          </template>
        </div>
      </template>

      <!-- 原生数模：只归档，转换器读不了（需要预览就另存 STEP 再上传） -->
      <template v-else-if="isNativeCad">
        <div class="dv-cad-entry">
          <a-empty description="原生数模（CATIA / Creo / NX / SolidWorks）不支持直接预览">
            <template #children>
              <div style="text-align:center;margin-top:8px;font-size:12px;color:#999">
                在 CAD 中另存为 STEP / IGES / STL 后重新上传，即可生成轻量化三维预览
              </div>
            </template>
          </a-empty>
        </div>
      </template>

      <!-- PDF -->
      <template v-else-if="isPdf">
        <iframe :src="previewUrl" class="dv-iframe" frameborder="0" />
      </template>

      <!-- 图片 -->
      <template v-else-if="isImage">
        <img :src="previewUrl" class="dv-image" alt="预览" />
      </template>

      <!-- 纯文本 -->
      <template v-else-if="isText">
        <iframe :src="previewUrl" class="dv-iframe" frameborder="0" />
      </template>

      <!-- 不支持 -->
      <template v-else>
        <a-empty description="不支持预览此文件格式">
          <template #children>
            <div style="text-align:center;margin-top:8px">
              <a :href="previewUrl" target="_blank">
                <a-button type="primary" size="small">直接下载文件</a-button>
              </a>
            </div>
          </template>
        </a-empty>
      </template>
    </div>
  </a-drawer>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount, defineAsyncComponent } from 'vue'
import VueOfficeDocx from '@vue-office/docx'
import VueOfficeExcel from '@vue-office/excel'
import VueOfficePptx from '@vue-office/pptx'
import '@vue-office/docx/lib/index.css'
import '@vue-office/excel/lib/index.css'
import { getCKFile, getCadViewerAvailable, convertCadModel, getLatestCadVisualization, ckFileStreamUrl, getEngDocPartLinks, resolveEntityApiPath } from '@/api'
import { useRouter } from 'vue-router'
import axios from 'axios'
/**
 * 三维查看器懒加载。
 *
 * three.js 打包后接近 4 MB，静态 import 会被打进本组件的主 chunk ——
 * 那意味着用户哪怕是预览一个 PDF，也要先下载整个渲染引擎。
 * 改成异步组件后，three 只在真正打开 CAD 数模时才加载。
 */
const CadViewer = defineAsyncComponent(() => import('@/components/cad/CadViewer.vue'))
// 进度条很轻（无第三方依赖），静态引入 —— 它恰恰要在"还没拿到模型"时出现
import CadConvertProgress from '@/components/cad/CadConvertProgress.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  doc: { type: Object, default: null },
  filePreviewBase: { type: String, default: '/api/ckfiles' }
})

const emit = defineEmits(['update:visible', 'close'])

// ---- 关联零件（工程数据 → 零件，EPMBuildRule 反向视图） ----
const router = useRouter()

/** 关联类型文案 / 配色：与零件详情「关联文档 → 工程数据」保持一致 */
const ASSOC_TYPE_LABEL = { OWNER: '主数据', CONTENT: '内容', CONTRIBUTING_IMAGE: '贡献图纸', IMAGE: '附图' }
const ASSOC_TYPE_COLOR = { OWNER: 'red', CONTENT: 'blue', CONTRIBUTING_IMAGE: 'orange', IMAGE: 'default' }
const engPartColumns = [
  { title: '零件', key: 'part', ellipsis: true },
  { title: '版本', key: 'partVersion', width: 90 },
  { title: '关联类型', key: 'assocType', width: 110 },
  { title: '构建规则', key: 'buildRules', width: 170 },
]

/** 当前对象是否工程数据 —— 只有工程数据才有「被哪些零件版本引用」 */
const isEngDoc = computed(() => {
  const code = props.doc?.typeDefinitionCode || props.doc?.entityType
  return !!code && resolveEntityApiPath(code) === 'eng-documents'
})

const engPartLinks = ref([])
const engPartLinksLoading = ref(false)

function hasBuildRule(record) {
  return !!(record?.buildStructure || record?.buildAttribute || record?.buildRepresentation)
}

async function loadEngPartLinks() {
  const docOid = props.doc?.oid
  if (!props.visible || !isEngDoc.value || !docOid) {
    engPartLinks.value = []
    return
  }
  engPartLinksLoading.value = true
  try {
    const res = await getEngDocPartLinks({ engDocumentOid: docOid })
    const data = res?.data
    engPartLinks.value = Array.isArray(data) ? data : (Array.isArray(res) ? res : [])
  } catch {
    engPartLinks.value = []
  } finally {
    engPartLinksLoading.value = false
  }
}

// 打开/切换对象时刷新；关掉时清空，避免下次打开先闪出上一份文档的零件
watch(
  () => [props.visible, props.doc?.oid, isEngDoc.value],
  () => { loadEngPartLinks() },
  { immediate: true }
)

function openPartDetail(record) {
  if (!record?.partOid) return
  emit('update:visible', false)
  emit('close')
  router.push({ name: 'PartDetail', params: { oid: record.partOid } })
}

// ---- CKFile 文件详情 ----
const ckFile = ref(null)
const fileLoading = ref(false)
const fileError = ref('')

// ---- Office 文档数据 ----
const docData = ref(null)
const officeLoading = ref(false)
const officeLoadTip = ref('内容加载中，请稍候...')
/**
 * 内容解析/渲染中：数据（Blob）已就绪、但 vue-office 组件尚未触发 @rendered。
 * 2026-10-11 新增 —— 原先 officeLoading 在"数据就绪"时就关了，之后组件解析
 * 大 PPTX（可达数十秒）期间没有任何提示，用户看到的是黑屏（用户反馈）。
 */
const contentLoading = ref(false)
let blobUrl = null  // 用于释放 Blob URL
let officeTimeoutId = null  // 大文件超时保护

// 文档切换 → 重载文件与 CAD 状态。该 watch 带 immediate，会同步执行
// loadCadState() → stopCadPolling()，依赖下方 CAD 状态（cadPollTimer 等）已初始化，
// 故定义在 setup 末尾（见文件底部 "文档加载入口"）。

async function loadCKFile(oid) {
  fileLoading.value = true
  try {
    const res = await getCKFile(oid)
    if (res.code === 200 && res.data) {
      ckFile.value = res.data
      await loadOfficeDataIfNeeded()
    } else {
      fileError.value = '无法获取文件信息'
    }
  } catch {
    fileError.value = '获取文件信息失败'
  } finally {
    fileLoading.value = false
  }
}

/** Office 文档预加载为 Blob URL（stream 端点已返回正确 MIME） */
async function loadOfficeDataIfNeeded() {
  if (!isOfficeDoc.value) return
  officeLoading.value = true
  
  // 根据文件大小调整提示
  const sizeMB = (ckFile.value?.fileSize || 0) / (1024 * 1024)
  if (sizeMB > 10) {
    officeLoadTip.value = `正在解析大文件 (${sizeMB.toFixed(1)} MB)，请耐心等待...`
    // 超过30秒仍未完成，提示用户
    officeTimeoutId = setTimeout(() => {
      if (officeLoading.value) {
        officeLoadTip.value = '文件较大，解析可能需要更长时间，建议下载查看'
      }
    }, 30000)
  } else {
    officeLoadTip.value = '内容加载中，请稍候...'
  }
  
  try {
    const resp = await axios.get(previewUrl.value, {
      responseType: 'blob'
    })
    
    // 清除超时
    if (officeTimeoutId) { clearTimeout(officeTimeoutId); officeTimeoutId = null }
    
    // PPTX 大文件保护：超过 15MB 建议下载
    if (isPpt.value && sizeMB > 15) {
      fileError.value = `PPTX 文件过大 (${sizeMB.toFixed(1)} MB)，浏览器端预览可能非常缓慢或失败，建议下载后使用 PowerPoint 查看`
      officeLoading.value = false
      contentLoading.value = false  // 不渲染预览，无需遮罩
      blobUrl = URL.createObjectURL(resp.data)
      docData.value = null
      return
    }
    
    blobUrl = URL.createObjectURL(resp.data)
    docData.value = blobUrl
    officeLoading.value = false   // 数据加载完成
    contentLoading.value = true   // ★ 进入解析/渲染阶段：遮罩显示"内容加载中…"，直到组件 @rendered
  } catch (err) {
    if (officeTimeoutId) { clearTimeout(officeTimeoutId); officeTimeoutId = null }
    officeLoading.value = false
    contentLoading.value = false
    console.error('Office 文档加载失败:', err)
    fileError.value = err.response?.status === 401 
      ? '权限不足，无法加载文档' 
      : '文档加载失败，请下载后查看'
  }
}

/** vue-office 组件渲染完成（docx/xlsx/pptx 共用）：关闭"内容加载中"遮罩 */
function onOfficeRendered() {
  if (officeTimeoutId) { clearTimeout(officeTimeoutId); officeTimeoutId = null }
  officeLoading.value = false
  contentLoading.value = false
}

function skipPreview() {
  if (officeTimeoutId) { clearTimeout(officeTimeoutId); officeTimeoutId = null }
  officeLoading.value = false
  contentLoading.value = false
  docData.value = null
  fileError.value = '已跳过预览，可使用下方按钮下载文件查看'
}

function handleOfficeError(err) {
  if (officeTimeoutId) { clearTimeout(officeTimeoutId); officeTimeoutId = null }
  officeLoading.value = false
  contentLoading.value = false
  const sizeMB = (ckFile.value?.fileSize || 0) / (1024 * 1024)
  let msg = '文档解析失败'
  if (sizeMB > 10) msg += `（文件 ${sizeMB.toFixed(1)} MB 过大）`
  msg += '，内容可能已损坏、包含不支持的元素，或格式不兼容，建议下载查看'
  fileError.value = msg
  console.error('Office 文档解析错误:', err)
}

// ---- 格式判断 ----
const detectName = computed(() => ckFile.value?.fileName || props.doc?.name || '')
const fileExtension = computed(() => {
  const dotIdx = detectName.value.lastIndexOf('.')
  return dotIdx > -1 ? detectName.value.substring(dotIdx + 1) : ''
})
const mimeType = computed(() => ckFile.value?.mimeType || '')

const isWord = computed(() => {
  if (['application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'].includes(mimeType.value)) return true
  return ['doc', 'docx'].includes(fileExtension.value.toLowerCase())
})

const isExcel = computed(() => {
  if (['application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'].includes(mimeType.value)) return true
  return ['xls', 'xlsx'].includes(fileExtension.value.toLowerCase())
})

const isPpt = computed(() => {
  if (['application/vnd.ms-powerpoint', 'application/vnd.openxmlformats-officedocument.presentationml.presentation'].includes(mimeType.value)) return true
  return ['ppt', 'pptx'].includes(fileExtension.value.toLowerCase())
})

const isOfficeDoc = computed(() => isWord.value || isExcel.value || isPpt.value)

const isPdf = computed(() => mimeType.value === 'application/pdf' || fileExtension.value.toLowerCase() === 'pdf')

const isImage = computed(() => {
  if (mimeType.value.startsWith('image/')) return true
  return ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg'].includes(fileExtension.value.toLowerCase())
})

const isText = computed(() => {
  const mimes = ['text/plain', 'text/html', 'text/css', 'text/javascript', 'application/json', 'application/xml', 'text/xml', 'text/csv']
  if (mimes.includes(mimeType.value)) return true
  return ['txt', 'html', 'htm', 'css', 'js', 'json', 'xml', 'csv', 'log', 'md'].includes(fileExtension.value.toLowerCase())
})

const previewUrl = computed(() => {
  if (!props.doc?.ckfileOid) return ''
  return `${props.filePreviewBase}/${props.doc.ckfileOid}/stream`
})

// ==================== CAD 三维预览 ====================

/** 是否 CAD 数模（由扩展名或 MIME 判定） */
const isCad = computed(() => {
  if (['step', 'stp', 'iges', 'igs', 'stl'].includes(fileExtension.value.toLowerCase())) return true
  return ['application/step', 'application/sla', 'application/iges'].includes(mimeType.value)
})

/** 原生数模（CATIA / Creo / NX / SolidWorks）：只归档，轻量化转换器读不了这些格式 */
const NATIVE_CAD_EXT = ['catpart', 'catproduct', 'prt', 'asm', 'sldprt', 'sldasm']
const isNativeCad = computed(() => NATIVE_CAD_EXT.includes(fileExtension.value.toLowerCase()))

const cadAvailable = ref(false)
const cadVis = ref(null)
const cadWorking = ref(false)
const cadTip = ref('正在生成三维预览...')
const cadMessage = ref('')
let cadPollTimer = null
/** 轮询上限：150 次 × 2 秒 = 5 分钟，超时就停（转换不该让用户无限等） */
let cadPollTicks = 0
const CAD_POLL_MAX = 150

const cadReady = computed(() =>
  cadVis.value?.status === 'SUCCESS' && !!cadVis.value?.glbCkfileOid)

const cadGlbUrl = computed(() =>
  cadReady.value ? ckFileStreamUrl(cadVis.value.glbCkfileOid) : '')

const cadStructureUrl = computed(() =>
  cadVis.value?.structureCkfileOid ? ckFileStreamUrl(cadVis.value.structureCkfileOid) : '')

const cadStats = computed(() => ({
  triangles: cadVis.value?.triangleCount,
  parts: cadVis.value?.partCount,
  units: cadVis.value?.units
}))

/** 打开文档时先看有没有现成产物，有就直接渲染，没有才提示生成 */
async function loadCadState() {
  stopCadPolling()
  cadVis.value = null
  cadMessage.value = ''
  if (!isCad.value || !props.doc?.oid) return

  try {
    const r = await getCadViewerAvailable()
    cadAvailable.value = !!(r.code === 200 && r.data?.available)
  } catch {
    cadAvailable.value = false
  }
  if (!cadAvailable.value) return

  try {
    const res = await getLatestCadVisualization(props.doc.oid)
    if (res.code === 200 && res.data) {
      cadVis.value = res.data
      // 上一轮转换还在跑：接着等，不要让用户再点一次"生成"
      if (res.data.status === 'QUEUED' || res.data.status === 'RUNNING') {
        cadWorking.value = true
        cadTip.value = '转换进行中，请稍候...'
        startCadPolling()
      } else if (res.data.status === 'FAILED' || res.data.status === 'SKIPPED') {
        cadMessage.value = res.data.errorMessage || '上次转换未成功'
      }
    }
  } catch {
    /* 查不到就当作没有产物，保持"生成三维预览"入口 */
  }
}

async function startConvert() {
  if (!props.doc?.oid || !props.doc?.ckfileOid) return
  cadWorking.value = true
  cadTip.value = '正在提交转换任务...'
  cadMessage.value = ''
  try {
    const res = await convertCadModel({
      engDocIterationOid: props.doc.oid,
      sourceCkfileOid: props.doc.ckfileOid
    })
    if (res.code !== 200 || !res.data) {
      cadWorking.value = false
      cadMessage.value = '提交转换失败'
      return
    }
    cadVis.value = res.data
    if (res.data.status === 'SUCCESS') { cadWorking.value = false; return }
    if (res.data.status === 'SKIPPED') {
      cadWorking.value = false
      cadMessage.value = res.data.errorMessage || '该文件无法转换'
      return
    }
    cadTip.value = '转换中，装配体可能需要几十秒...'
    startCadPolling()
  } catch {
    cadWorking.value = false
    cadMessage.value = '提交转换失败，请检查转换器是否运行'
  }
}

function startCadPolling() {
  stopCadPolling()
  cadPollTicks = 0
  cadPollTimer = setInterval(async () => {
    cadPollTicks++
    if (cadPollTicks > CAD_POLL_MAX) {
      stopCadPolling()
      cadWorking.value = false
      cadMessage.value = '转换超时，请稍后重试'
      return
    }
    try {
      const res = await getLatestCadVisualization(props.doc.oid)
      if (res.code !== 200 || !res.data) return
      cadVis.value = res.data
      const st = res.data.status
      if (st === 'SUCCESS') {
        stopCadPolling()
        cadWorking.value = false
      } else if (st === 'FAILED' || st === 'SKIPPED' || st === 'TIMEOUT') {
        stopCadPolling()
        cadWorking.value = false
        cadMessage.value = res.data.errorMessage || '转换失败'
      }
    } catch {
      /* 单次轮询失败不打断，等下一拍 */
    }
  }, 2000)
}

function stopCadPolling() {
  if (cadPollTimer) {
    clearInterval(cadPollTimer)
    cadPollTimer = null
  }
}

// ---- 工具函数 ----
function statusColor(code) {
  const map = { DRAFT: 'default', INWORK: 'processing', REVIEW: 'warning', APPROVED: 'success', RELEASED: 'blue', OBSOLETE: 'error' }
  return map[code] || 'default'
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function downloadFile() {
  if (previewUrl.value) window.open(previewUrl.value, '_blank')
}

function close() {
  emit('update:visible', false)
  emit('close')
}

onBeforeUnmount(() => {
  if (blobUrl) { URL.revokeObjectURL(blobUrl); blobUrl = null }
  if (officeTimeoutId) { clearTimeout(officeTimeoutId); officeTimeoutId = null }
  stopCadPolling()
})

// ==================== 文档加载入口 ====================

/**
 * 打开/切换文档时重载文件与 CAD 状态。
 *
 * 必须留在 setup 末尾：immediate 会同步执行 loadCadState() → stopCadPolling()，
 * 而 cadPollTimer / cadVis / cadMessage / cadTip 等用 const/let 声明，提前访问会踩
 * TDZ（Cannot access 'cadPollTimer' before initialization）。
 */
watch(() => props.doc?.ckfileOid, (newOid) => {
  // 清理旧 Blob URL
  if (blobUrl) { URL.revokeObjectURL(blobUrl); blobUrl = null }
  // 清理超时
  if (officeTimeoutId) { clearTimeout(officeTimeoutId); officeTimeoutId = null }
  ckFile.value = null
  docData.value = null
  fileError.value = ''
  officeLoading.value = false
  contentLoading.value = false
  officeLoadTip.value = '内容加载中，请稍候...'
  if (newOid) loadCKFile(newOid)
  loadCadState()
}, { immediate: true })
</script>

<style scoped>
.dv-info-bar { margin-bottom: 8px; }
/* 反向视图：工程数据被哪些零件版本引用 */
.dv-reverse { margin-bottom: 4px; }
.dv-reverse-divider { margin: 4px 0 8px; font-size: 13px; }
.dv-part-link { color: #1677ff; }
.dv-muted { color: #8c8c8c; font-size: 12px; margin-left: 6px; }
.dv-viewer {
  position: relative;   /* 内容加载遮罩的定位基准（2026-10-11） */
  width: 100%; min-height: 400px; background: #f5f5f5; border-radius: 4px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.dv-viewer--full {
  align-items: stretch; justify-content: flex-start; background: #fff;
}
/* 内容解析/渲染遮罩：覆盖预览区（大 PPTX 解析期原来是一片黑屏，无任何提示），
   vue-office 组件 @rendered 后自动消失（见 onOfficeRendered） */
.dv-content-mask {
  position: absolute; inset: 0; z-index: 10;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255, 255, 255, 0.96);
}
.dv-office-header {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; background: #fafafa; border-bottom: 1px solid #f0f0f0;
}
.dv-office-body {
  flex: 1; min-height: 60vh; overflow: auto;
}
.dv-iframe { width: 100%; height: 70vh; border: none; }
.dv-image { max-width: 100%; max-height: 70vh; object-fit: contain; }
/* CAD 三维预览：给了更大高度 —— 看装配体时垂直空间不够会非常难受 */
.dv-cad-body { width: 100%; height: 72vh; min-height: 460px; }
.dv-cad-entry {
  width: 100%; min-height: 340px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
</style>
