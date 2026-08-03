<template>
  <div class="iu-root">
    <!-- 缩略图预览 -->
    <div v-if="currentValue" class="iu-preview">
      <a-spin :spinning="imageLoading" size="small">
        <img
          :src="currentValue"
          alt="缩略图预览"
          @load="imageLoading = false"
          @error="handleImageError"
        />
      </a-spin>
      <!-- 悬浮操作栏 -->
      <div class="iu-preview-actions">
        <a-tooltip title="查看大图">
          <a-button
            type="text"
            size="small"
            class="iu-preview-btn"
            @click="showPreview = true"
          >
            <template #icon><EyeOutlined /></template>
          </a-button>
        </a-tooltip>
        <a-tooltip title="复制链接">
          <a-button
            type="text"
            size="small"
            class="iu-preview-btn"
            @click="copyUrl"
          >
            <template #icon><LinkOutlined /></template>
          </a-button>
        </a-tooltip>
        <a-tooltip v-if="!disabled" title="删除">
          <a-button
            type="text"
            danger
            size="small"
            class="iu-preview-btn"
            @click="handleClear"
          >
            <template #icon><CloseOutlined /></template>
          </a-button>
        </a-tooltip>
      </div>
    </div>

    <a-space v-if="!disabled">
      <!-- 上传按钮 -->
      <a-upload
        :before-upload="handleUpload"
        :show-upload-list="false"
        accept="image/*"
      >
        <a-button :loading="uploading">
          <template #icon><UploadOutlined /></template>
          {{ currentValue ? '重新上传' : '上传图片' }}
        </a-button>
      </a-upload>

      <!-- 从图片空间选择 -->
      <a-button @click="openMediaPicker">
        <template #icon><PictureOutlined /></template>
        从图片空间选择
      </a-button>
    </a-space>

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

    <!-- 图片空间选择弹窗 -->
    <a-modal
      v-model:open="pickerVisible"
      title="从图片空间选择"
      width="860px"
      :style="{ top: '30px' }"
      :body-style="{ padding: '16px', height: '500px', overflow: 'auto' }"
      :mask-closable="false"
      ok-text="确认选择"
      @ok="confirmPicker"
      @cancel="cancelPicker"
    >
      <MediaSpace
        ref="pickerRef"
        :selectable="true"
        @select="handleMediaSelect"
      />
    </a-modal>

    <!-- 大图预览弹窗 -->
    <a-modal
      v-model:open="showPreview"
      :footer="null"
      width="auto"
      :style="{ maxWidth: '90vw' }"
      centered
      @cancel="showPreview = false"
    >
      <div style="text-align: center;">
        <img :src="currentValue" alt="图片预览" style="max-width: 100%; max-height: 75vh; border-radius: 6px;" />
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined, PictureOutlined, CloseOutlined, EyeOutlined, LinkOutlined } from '@ant-design/icons-vue'
import { uploadMedia } from '@/api'
import MediaSpace from '@/views/media/MediaSpace.vue'

/**
 * 图片上传组件，对应后端 Media 实体（图片空间）。
 *
 * <p>上传成功后直接将 `storagePath` 通过 v-model 传出。
 * 支持直接上传和从图片空间选择两种模式。
 *
 * @fires update:value - 上传/选择/清除时触发，值为 storagePath 字符串或空字符串
 */

const props = defineProps({
  /** 图片路径（storagePath），用于双向绑定和回显 */
  value: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:value'])

const uploading = ref(false)
const imageLoading = ref(false)
const uploadError = ref('')
const currentValue = computed(() => props.value)

// 图片加载失败降级
const imageFailed = ref(false)
function handleImageError() {
  imageLoading.value = false
  imageFailed.value = true
}
// 切换 value 时重置状态
watch(() => props.value, () => {
  imageLoading.value = !!props.value
  imageFailed.value = false
  uploadError.value = ''
})

// ========== 大图预览 ==========
const showPreview = ref(false)

// ========== 复制链接 ==========
async function copyUrl() {
  const fullUrl = window.location.origin + currentValue.value
  try {
    await navigator.clipboard.writeText(fullUrl)
    message.success('链接已复制')
  } catch {
    const ta = document.createElement('textarea')
    ta.value = fullUrl
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    message.success('链接已复制')
  }
}

// ========== 直接上传 ==========
async function handleUpload(file) {
  uploadError.value = ''
  uploading.value = true
  try {
    const res = await uploadMedia(file)
    if (res.code === 200) {
      emit('update:value', res.data.storagePath)
      message.success('上传成功')
    } else {
      uploadError.value = res.message || '上传失败'
    }
  } catch (e) {
    uploadError.value = e?.response?.data?.message || e?.message || '上传失败，请检查网络后重试'
    console.error('图片上传失败:', e)
  } finally {
    uploading.value = false
  }
  return false
}

// ========== 清除 ==========
function handleClear() {
  uploadError.value = ''
  emit('update:value', '')
}

// ========== 图片空间选择 ==========
const pickerVisible = ref(false)
const pickerRef = ref(null)
const pendingItem = ref(null)

function openMediaPicker() {
  pendingItem.value = null
  pickerVisible.value = true
}

function handleMediaSelect(item) {
  pendingItem.value = item
}

function confirmPicker() {
  if (pendingItem.value) {
    emit('update:value', pendingItem.value.storagePath)
  }
  pickerVisible.value = false
}

function cancelPicker() {
  pendingItem.value = null
  pickerVisible.value = false
}
</script>

<style scoped>
.iu-root {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* ===== 预览卡片 ===== */
.iu-preview {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 140px;
  height: 105px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
}

.iu-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.iu-preview-actions {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.iu-preview:hover .iu-preview-actions {
  opacity: 1;
}

.iu-preview-btn {
  color: #fff !important;
  font-size: 15px;
}

.iu-preview-btn:hover {
  color: #e0e0e0 !important;
  background: rgba(255, 255, 255, 0.15) !important;
}
</style>
