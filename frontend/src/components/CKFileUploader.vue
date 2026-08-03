<template>
  <div class="cfu-root">
    <!-- 已选文件展示 -->
    <div v-if="currentFile" class="cfu-file-bar">
      <span class="cfu-file-icon">
        <LinkOutlined v-if="currentFile.sourceType === 'URL'" style="color:#13c2c2" />
        <FileOutlined v-else style="color:#1677ff" />
      </span>
      <div class="cfu-file-meta">
        <a-tooltip :title="currentFile.displayName || currentFile.fileName || currentFile.sourceUrl">
          <span class="cfu-file-name">{{ currentFile.displayName || currentFile.fileName || currentFile.sourceUrl }}</span>
        </a-tooltip>
        <span class="cfu-file-tag">
          <a-tag v-if="currentFile.sourceType === 'URL'" color="cyan" size="small">URL</a-tag>
          <a-tag v-else color="blue" size="small">本地文件</a-tag>
          <span v-if="currentFile.fileSize != null" class="cfu-file-size">{{ formatFileSize(currentFile.fileSize) }}</span>
        </span>
      </div>
      <a-button v-if="!disabled" type="text" danger size="small" @click="handleClear">
        <template #icon><CloseOutlined /></template>
      </a-button>
    </div>

    <!-- URL 输入模式 -->
    <div v-if="!disabled && mode === 'url' && !currentFile" class="cfu-url-box">
      <a-input-group compact style="display:flex;width:100%">
        <a-input
          v-model:value="urlInput"
          placeholder="请输入文档网络地址，如 https://example.com/doc.pdf"
          @keyup.enter="handleUrlSubmit"
          style="flex:1"
        />
        <a-button type="primary" :loading="uploading" @click="handleUrlSubmit">
          <template #icon><CheckOutlined /></template>
          确认
        </a-button>
      </a-input-group>
    </div>

    <!-- 本地上传区域 -->
    <a-upload-dragger
      v-if="!disabled && mode === 'local' && !currentFile"
      :before-upload="handleUpload"
      :show-upload-list="false"
      :accept="accept"
      :multiple="false"
    >
      <p class="cfu-drag-icon"><InboxOutlined style="font-size:28px;color:#1677ff" /></p>
      <p class="cfu-drag-text">点击或拖拽文件到此区域上传</p>
      <p class="cfu-drag-hint" v-if="accept">{{ acceptHint }}</p>
    </a-upload-dragger>

    <!-- 已选文件后重新选择 -->
    <a-upload
      v-else-if="!disabled && mode === 'local'"
      :before-upload="handleUpload"
      :show-upload-list="false"
      :accept="accept"
      :multiple="false"
    >
      <a-button :loading="uploading" size="small">
        <template #icon><UploadOutlined /></template>
        {{ currentFile ? '更换文件' : '选择文件' }}
      </a-button>
    </a-upload>

    <!-- 模式切换 -->
    <div v-if="!currentFile && !disabled" class="cfu-mode-switch">
      <a-radio-group v-model:value="mode" size="small" button-style="solid">
        <a-radio-button value="local"><CloudUploadOutlined /> 本地文件</a-radio-button>
        <a-radio-button value="url"><GlobalOutlined /> 网络地址</a-radio-button>
      </a-radio-group>
    </div>

    <!-- 错误提示 -->
    <a-alert v-if="uploadError" type="error" :message="uploadError" closable show-icon
      style="margin-top:4px" @close="uploadError = ''" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  UploadOutlined, InboxOutlined, CloseOutlined, FileOutlined,
  LinkOutlined, CheckOutlined, CloudUploadOutlined, GlobalOutlined,
} from '@ant-design/icons-vue'
import { uploadCKFile, createCKFileFromUrl, getCKFile } from '@/api'

const props = defineProps({
  value: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  accept: { type: String, default: '' },
})

const emit = defineEmits(['update:value'])

const uploading = ref(false)
const uploadError = ref('')
const fileInfo = ref(null)
const mode = ref('local')
const urlInput = ref('')

const currentFile = computed(() => {
  if (!fileInfo.value) return null
  return fileInfo.value
})

const acceptHint = computed(() => {
  if (!props.accept) return '支持所有格式'
  const labels = props.accept.split(',').map(p => {
    if (p === '.pdf') return 'PDF'; if (p === '.docx' || p === '.doc') return 'Word'
    if (p === '.xlsx' || p === '.xls') return 'Excel'; if (p === '.zip' || p === '.rar') return '压缩包'
    return p.trim()
  })
  return `支持 ${labels.join('、')}`
})

// 监听 value（oid）变化，自动查询 CKFile 信息用于显示
watch(() => props.value, async (oid) => {
  if (!oid) { fileInfo.value = null; return }
  try {
    const res = await getCKFile(oid)
    if (res.code === 200) fileInfo.value = res.data
    else fileInfo.value = null
  } catch { fileInfo.value = null }
}, { immediate: true })

// ========== 本地文件上传 ==========
async function handleUpload(file) {
  uploadError.value = ''
  uploading.value = true
  try {
    const res = await uploadCKFile(file)
    if (res.code === 200) {
      fileInfo.value = res.data
      emit('update:value', res.data.oid)
      message.success('上传成功')
    } else {
      uploadError.value = res.message || '上传失败'
    }
  } catch (e) {
    uploadError.value = e?.response?.data?.message || e?.message || '上传失败'
  } finally { uploading.value = false }
  return false
}

// ========== URL 录入 ==========
async function handleUrlSubmit() {
  const url = urlInput.value.trim()
  if (!url) return message.warning('请输入文档网络地址')
  uploadError.value = ''
  uploading.value = true
  try {
    const res = await createCKFileFromUrl(url)
    if (res.code === 200) {
      fileInfo.value = res.data
      emit('update:value', res.data.oid)
      urlInput.value = ''
      message.success('主文档 URL 已录入')
    } else {
      uploadError.value = res.message || '录入失败'
    }
  } catch (e) {
    uploadError.value = e?.response?.data?.message || e?.message || '录入失败'
  } finally { uploading.value = false }
}

// ========== 清除 ==========
function handleClear() {
  uploadError.value = ''
  fileInfo.value = null
  urlInput.value = ''
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
.cfu-root { display: flex; flex-direction: column; gap: 8px; }

.cfu-file-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 12px; border: 1px solid #d9d9d9; border-radius: 8px;
  background: #fafafa; max-width: 100%;
}
.cfu-file-icon { font-size: 22px; flex-shrink: 0; display: flex; align-items: center; }
.cfu-file-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cfu-file-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: #333; font-weight: 500; }
.cfu-file-tag { display: flex; align-items: center; gap: 6px; }
.cfu-file-size { font-size: 12px; color: #999; }

.cfu-drag-icon { margin-bottom: 8px; }
.cfu-drag-text { font-size: 14px; color: #333; margin: 0 0 4px; }
.cfu-drag-hint { font-size: 12px; color: #999; margin: 0; }

.cfu-url-box { width: 100%; }

.cfu-mode-switch { margin-top: 4px; }

::deep(.ant-upload-drag) { border-radius: 8px; }
::deep(.ant-upload-drag:hover) { border-color: #1677ff; }
</style>
