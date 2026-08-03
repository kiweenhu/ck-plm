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

    <a-divider />

    <!-- 文档预览/编辑区域 -->
    <div class="dv-viewer" :class="{ 'dv-viewer--full': isOfficeDoc }">
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
          @rendered="officeLoading = false"
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
          @rendered="officeLoading = false"
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
          @rendered="officeLoading = false"
          @error="handleOfficeError"
        />
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
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import VueOfficeDocx from '@vue-office/docx'
import VueOfficeExcel from '@vue-office/excel'
import VueOfficePptx from '@vue-office/pptx'
import '@vue-office/docx/lib/index.css'
import '@vue-office/excel/lib/index.css'
import { getCKFile } from '@/api'
import axios from 'axios'

const props = defineProps({
  visible: { type: Boolean, default: false },
  doc: { type: Object, default: null },
  filePreviewBase: { type: String, default: '/api/ckfiles' }
})

const emit = defineEmits(['update:visible', 'close'])

// ---- CKFile 文件详情 ----
const ckFile = ref(null)
const fileLoading = ref(false)
const fileError = ref('')

// ---- Office 文档数据 ----
const docData = ref(null)
const officeLoading = ref(false)
const officeLoadTip = ref('正在解析文档...')
let blobUrl = null  // 用于释放 Blob URL
let officeTimeoutId = null  // 大文件超时保护

watch(() => props.doc?.ckfileOid, (newOid, oldOid) => {
  // 清理旧 Blob URL
  if (blobUrl) { URL.revokeObjectURL(blobUrl); blobUrl = null }
  // 清理超时
  if (officeTimeoutId) { clearTimeout(officeTimeoutId); officeTimeoutId = null }
  ckFile.value = null
  docData.value = null
  fileError.value = ''
  officeLoading.value = false
  officeLoadTip.value = '正在解析文档...'
  if (newOid) loadCKFile(newOid)
}, { immediate: true })

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
    officeLoadTip.value = '正在解析文档...'
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
      blobUrl = URL.createObjectURL(resp.data)
      docData.value = null
      return
    }
    
    blobUrl = URL.createObjectURL(resp.data)
    docData.value = blobUrl
    officeLoading.value = false  // 数据就绪，交给组件渲染
  } catch (err) {
    if (officeTimeoutId) { clearTimeout(officeTimeoutId); officeTimeoutId = null }
    officeLoading.value = false
    console.error('Office 文档加载失败:', err)
    fileError.value = err.response?.status === 401 
      ? '权限不足，无法加载文档' 
      : '文档加载失败，请下载后查看'
  }
}

function skipPreview() {
  if (officeTimeoutId) { clearTimeout(officeTimeoutId); officeTimeoutId = null }
  officeLoading.value = false
  docData.value = null
  fileError.value = '已跳过预览，可使用下方按钮下载文件查看'
}

function handleOfficeError(err) {
  if (officeTimeoutId) { clearTimeout(officeTimeoutId); officeTimeoutId = null }
  officeLoading.value = false
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
})
</script>

<style scoped>
.dv-info-bar { margin-bottom: 8px; }
.dv-viewer {
  width: 100%; min-height: 400px; background: #f5f5f5; border-radius: 4px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.dv-viewer--full {
  align-items: stretch; justify-content: flex-start; background: #fff;
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
</style>
