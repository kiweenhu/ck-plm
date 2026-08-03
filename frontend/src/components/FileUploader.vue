<template>
  <div class="fu-root">
    <!-- 已选文件展示 -->
    <div v-if="currentFile" class="fu-file-bar">
      <span class="fu-file-icon">{{ fileTypeIcon }}</span>
      <div class="fu-file-meta">
        <a-tooltip :title="currentFile.name || currentFile.path">
          <span class="fu-file-name">{{ currentFile.name || currentFile.path }}</span>
        </a-tooltip>
        <span v-if="currentFile.size != null" class="fu-file-size">{{ formatFileSize(currentFile.size) }}</span>
      </div>
      <a-button
        v-if="!disabled"
        type="text"
        danger
        size="small"
        class="fu-remove-btn"
        @click="handleClear"
      >
        <template #icon><CloseOutlined /></template>
      </a-button>
    </div>

    <!-- 上传按钮 -->
    <a-upload-dragger
      v-if="!disabled && !currentFile"
      :before-upload="handleUpload"
      :show-upload-list="false"
      :accept="accept"
      :multiple="false"
    >
      <p class="fu-drag-icon">
        <InboxOutlined style="font-size: 28px; color: #1677ff;" />
      </p>
      <p class="fu-drag-text">点击或拖拽文件到此区域上传</p>
      <p class="fu-drag-hint" v-if="accept">{{ acceptHint }}</p>
    </a-upload-dragger>

    <a-upload
      v-else-if="!disabled"
      :before-upload="handleUpload"
      :show-upload-list="false"
      :accept="accept"
      :multiple="false"
    >
      <a-button :loading="uploading">
        <template #icon><UploadOutlined /></template>
        {{ currentFile ? '重新上传' : '上传附件' }}
      </a-button>
    </a-upload>

    <!-- 上传错误提示 -->
    <a-alert
      v-if="uploadError"
      type="error"
      :message="uploadError"
      closable
      show-icon
      style="margin-top: 4px;"
      @close="uploadError = ''"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined, InboxOutlined, CloseOutlined, FileOutlined, FilePdfOutlined, FileExcelOutlined, FileWordOutlined, FileImageOutlined, FileZipOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import { uploadAttachment, getAttachment } from '@/api'

/**
 * 附件上传组件，对应后端 CKAttachment 实体。
 *
 * <p>上传后返回 CKAttachment.oid，组件将 oid 作为 v-model 值，
 * 供 DocumentIteration 通过 ownerOid 关联使用。
 * 显示时自动查询 CKAttachment 信息并展示文件名/大小。
 *
 * @fires update:value - 上传后值为 CKAttachment.oid 字符串，清除后为空字符串
 */

const props = defineProps({
  /** 当前值 — CKAttachment.oid */
  value: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  /** 允许的文件类型，如 "application/pdf,.docx" */
  accept: { type: String, default: '' },
})

const emit = defineEmits(['update:value'])

const uploading = ref(false)
const uploadError = ref('')
/** 已查询到的 CKFile 信息（用于显示） */
const fileInfo = ref(null)

/** MIME 类型 → 图标映射 */
const MIME_ICON_MAP = {
  'application/pdf': FilePdfOutlined,
  'application/msword': FileWordOutlined,
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': FileWordOutlined,
  'application/vnd.ms-excel': FileExcelOutlined,
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': FileExcelOutlined,
  'application/zip': FileZipOutlined,
  'application/x-rar-compressed': FileZipOutlined,
  'application/x-7z-compressed': FileZipOutlined,
  'text/plain': FileTextOutlined,
  'text/csv': FileTextOutlined,
  'image/': FileImageOutlined,
}

/** 当前显示用的文件信息对象 */
const currentFile = computed(() => {
  if (!fileInfo.value) return null
  const fi = fileInfo.value
  return {
    name: fi.fileName,
    size: fi.fileSize,
    path: fi.storagePath,
    mimeType: fi.mimeType,
  }
})

/** 监听 value 变化（oid），自动查询 CKAttachment 信息用于显示 */
watch(
  () => props.value,
  async (oid) => {
    if (!oid) { fileInfo.value = null; return }
    if (oid.startsWith('{')) { fileInfo.value = null; return }
    try {
      const res = await getAttachment(oid)
      if (res.code === 200) {
        fileInfo.value = res.data
      } else {
        fileInfo.value = null
      }
    } catch { fileInfo.value = null }
  },
  { immediate: true }
)

/** 根据 MIME 类型动态选取图标 */
const fileTypeIcon = computed(() => {
  if (!currentFile.value) return FileOutlined
  const mime = currentFile.value.mimeType || ''
  for (const [prefix, icon] of Object.entries(MIME_ICON_MAP)) {
    if (mime.startsWith(prefix)) return icon
  }
  // 根据扩展名 fallback
  const name = (currentFile.value.name || '').toLowerCase()
  if (name.endsWith('.pdf')) return FilePdfOutlined
  if (name.endsWith('.xls') || name.endsWith('.xlsx')) return FileExcelOutlined
  if (name.endsWith('.doc') || name.endsWith('.docx')) return FileWordOutlined
  if (name.endsWith('.zip') || name.endsWith('.rar') || name.endsWith('.7z')) return FileZipOutlined
  return FileOutlined
})

/** 根据 accept 生成友好的提示文案 */
const acceptHint = computed(() => {
  if (!props.accept) return '支持所有格式'
  const parts = props.accept.split(',').map(s => s.trim())
  const labels = parts.map(p => {
    if (p === '.pdf') return 'PDF'
    if (p === '.docx' || p === '.doc') return 'Word'
    if (p === '.xlsx' || p === '.xls') return 'Excel'
    if (p === '.zip' || p === '.rar') return '压缩包'
    return p
  })
  return `支持 ${labels.join('、')}`
})

// ========== 上传 ==========
async function handleUpload(file) {
  uploadError.value = ''
  uploading.value = true
  try {
    const res = await uploadAttachment(file)
    if (res.code === 200) {
      const att = res.data
      fileInfo.value = att
      emit('update:value', att.oid)
      message.success('上传成功')
    } else {
      uploadError.value = res.message || '上传失败'
    }
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '上传失败，请检查网络后重试'
    uploadError.value = msg
    console.error('附件上传失败:', e)
  } finally {
    uploading.value = false
  }
  return false
}

// ========== 清除 ==========
function handleClear() {
  uploadError.value = ''
  fileInfo.value = null
  emit('update:value', '')
}

function formatFileSize(bytes) {
  if (bytes == null) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped>
.fu-root {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.fu-file-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  background: #fafafa;
  max-width: 100%;
}

.fu-file-icon {
  font-size: 22px;
  color: #1677ff;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.fu-file-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.fu-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #333;
  font-weight: 500;
}

.fu-file-size {
  font-size: 12px;
  color: #999;
}

.fu-remove-btn {
  flex-shrink: 0;
}

/* 拖拽上传区域 */
.fu-drag-icon {
  margin-bottom: 8px;
}

.fu-drag-text {
  font-size: 14px;
  color: #333;
  margin: 0 0 4px;
}

.fu-drag-hint {
  font-size: 12px;
  color: #999;
  margin: 0;
}

/* 拖拽区域 hover 增强 */
:deep(.ant-upload-drag) {
  border-radius: 8px;
}

:deep(.ant-upload-drag:hover) {
  border-color: #1677ff;
}
</style>
