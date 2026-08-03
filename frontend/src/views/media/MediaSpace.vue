<template>
  <div class="media-space-page">
    <!-- 页头 -->
    <div class="ms-header">
      <div class="ms-header-left">
        <h3 class="ms-title">产品图册</h3>
        <span class="ms-subtitle">统一管理产品图片，供产品线、企业资源等复用</span>
      </div>
    </div>

    <!-- 统计栏 -->
    <div class="ms-stats-bar">
      <div class="ms-stat-item">
        <PictureOutlined class="ms-stat-icon" />
        <span class="ms-stat-value">{{ totalCount }}</span>
        <span class="ms-stat-label">图册总数</span>
      </div>
      <a-divider type="vertical" style="height:36px" />
      <div class="ms-stat-item">
        <CloudOutlined class="ms-stat-icon" />
        <span class="ms-stat-value">{{ totalStorage }}</span>
        <span class="ms-stat-label">存储空间</span>
      </div>
      <a-divider type="vertical" style="height:36px" />
      <div class="ms-stat-item">
        <ClockCircleOutlined class="ms-stat-icon" />
        <span class="ms-stat-value">{{ recentCount }}</span>
        <span class="ms-stat-label">近7天上传</span>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="ms-toolbar">
      <a-space>
        <a-upload
          :before-upload="handleBeforeUpload"
          :show-upload-list="false"
          accept="image/*"
        >
          <a-button type="primary" :loading="uploading">
            <template #icon><CloudUploadOutlined /></template>
            上传图片
          </a-button>
        </a-upload>
        <a-input-search
          v-model:value="searchKeyword"
          placeholder="搜索图片名称/描述..."
          style="width: 280px"
          @search="handleSearch"
          @change="onSearchChange"
        />
      </a-space>
    </div>

    <!-- 图片网格 -->
    <div class="ms-grid-wrapper" ref="gridWrapperRef" @scroll="handleScroll">
      <!-- 加载骨架 -->
      <div v-if="loading && mediaList.length === 0" class="ms-grid">
        <div v-for="i in 8" :key="'sk-'+i" class="ms-card ms-card-skeleton">
          <div class="ms-card-img" style="background:#f0f0f0; animation: skeleton-pulse 1.5s infinite;" />
          <div class="ms-card-info">
            <span class="ms-skel-text" style="width:80%"></span>
            <span class="ms-skel-text" style="width:40%"></span>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <a-empty
        v-else-if="!loading && mediaList.length === 0"
        description="暂无图片，点击「上传图片」开始添加"
      />

      <!-- 图片网格 -->
      <div v-else class="ms-grid">
        <div
          v-for="item in mediaList"
          :key="item.oid"
          class="ms-card"
          :class="{ 'ms-card-selectable': selectable, 'ms-card-selected': selectedOid === item.oid }"
          @click="handleCardClick(item)"
        >
          <div class="ms-card-img">
            <img :src="item.storagePath" :alt="item.originalName" loading="lazy" />
            <div class="ms-card-overlay">
              <a-space size="small">
                <a-tooltip title="预览">
                  <a-button
                    size="small"
                    type="text"
                    class="ms-overlay-btn"
                    @click.stop="previewImage(item)"
                  >
                    <template #icon><EyeOutlined /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip title="复制链接">
                  <a-button
                    size="small"
                    type="text"
                    class="ms-overlay-btn"
                    @click.stop="copyUrl(item)"
                  >
                    <template #icon><LinkOutlined /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip :title="inUseOids.has(item.oid) ? '已被产品线使用，不可删除' : '删除'">
                  <a-popconfirm
                    v-if="!inUseOids.has(item.oid)"
                    title="确定删除该图片？"
                    @confirm="handleDelete(item)"
                  >
                    <a-button
                      size="small"
                      type="text"
                      danger
                      class="ms-overlay-btn"
                      @click.stop
                    >
                      <template #icon><DeleteOutlined /></template>
                    </a-button>
                  </a-popconfirm>
                  <a-button
                    v-else
                    size="small"
                    type="text"
                    disabled
                    class="ms-overlay-btn"
                    style="color: rgba(255,255,255,0.3) !important;"
                    @click.stop
                  >
                    <template #icon><DeleteOutlined /></template>
                  </a-button>
                </a-tooltip>
              </a-space>
            </div>
          </div>
          <div class="ms-card-info">
            <a-tooltip :title="item.originalName">
              <span class="ms-card-name">{{ item.originalName }}</span>
            </a-tooltip>
            <span class="ms-card-size">{{ formatSize(item.fileSize) }}</span>
          </div>
        </div>
      </div>

      <!-- 加载更多提示 -->
      <div v-if="loading && mediaList.length > 0" class="ms-load-more">
        <a-spin size="small" />
        <span style="margin-left: 8px; color: #999;">加载更多...</span>
      </div>

      <!-- 已加载全部 -->
      <div v-if="!hasMore && mediaList.length > 0" class="ms-load-more" style="color: #ccc;">
        已加载全部图片
      </div>
    </div>

    <!-- 图片预览弹窗 -->
    <a-modal
      v-model:open="previewVisible"
      :footer="null"
      width="auto"
      :style="{ maxWidth: '90vw' }"
      centered
      @cancel="previewVisible = false"
    >
      <div style="text-align: center;">
        <img :src="previewUrl" :alt="previewName" style="max-width: 100%; max-height: 75vh; border-radius: 4px;" />
        <div style="margin-top: 12px; color: #8c8c8c; font-size: 13px;">{{ previewName }}</div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { message } from 'ant-design-vue'
import {
  PictureOutlined, CloudOutlined, ClockCircleOutlined,
  CloudUploadOutlined, EyeOutlined, LinkOutlined, DeleteOutlined,
} from '@ant-design/icons-vue'
import { getMediaList, uploadMedia, deleteMedia, checkMediaUsage } from '@/api'

const props = defineProps({
  /** 是否开启选择模式（用于嵌入产品线缩略图选择） */
  selectable: { type: Boolean, default: false },
})

const emit = defineEmits(['select'])

// ==================== 状态 ====================
const loading = ref(false)
const uploading = ref(false)
const searchKeyword = ref('')
const mediaList = ref([])
const allMedia = ref([])          // 全量数据（加载到内存后分页渲染）
const selectedOid = ref(null)
const inUseOids = ref(new Set())
const gridWrapperRef = ref(null)

// 分页
const PAGE_SIZE = 24
const pageIndex = ref(0)
const hasMore = computed(() => mediaList.value.length < allMedia.value.length)

// 总计数（全量）
const totalCount = computed(() => allMedia.value.length)

// 预览
const previewVisible = ref(false)
const previewUrl = ref('')
const previewName = ref('')

// ==================== 防抖搜索 ====================
let searchTimer = null
function onSearchChange() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    loadList(searchKeyword.value || undefined)
  }, 400)
}

// ==================== 统计 ====================
const totalStorage = computed(() => {
  const bytes = allMedia.value.reduce((sum, m) => sum + (m.fileSize || 0), 0)
  return formatSize(bytes)
})

const recentCount = computed(() => {
  const sevenDaysAgo = Date.now() - 7 * 24 * 60 * 60 * 1000
  return allMedia.value.filter(m => {
    const created = m.createdAt ? new Date(m.createdAt).getTime() : 0
    return created > sevenDaysAgo
  }).length
})

// ==================== 数据加载 ====================
async function loadList(keyword) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await getMediaList(keyword || undefined)
    if (res.code === 200) {
      allMedia.value = res.data || []
      pageIndex.value = 0
      mediaList.value = allMedia.value.slice(0, PAGE_SIZE)

      // 批量检查引用状态
      if (allMedia.value.length > 0) {
        try {
          const oids = allMedia.value.map(m => m.oid)
          const usageRes = await checkMediaUsage(oids)
          if (usageRes.code === 200 && usageRes.data) {
            const used = new Set()
            for (const [oid, usedFlag] of Object.entries(usageRes.data)) {
              if (usedFlag) used.add(oid)
            }
            inUseOids.value = used
          }
        } catch {
          inUseOids.value = new Set()
        }
      }
    }
  } catch {
    message.error('加载图片列表失败')
    allMedia.value = []
    mediaList.value = []
  } finally {
    loading.value = false
  }
}

/** 滚动加载更多 */
function handleScroll() {
  if (!hasMore.value || loading.value) return
  const el = gridWrapperRef.value
  if (!el) return
  // 距底部 100px 时触发加载
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 100) {
    loadMore()
  }
}

function loadMore() {
  const start = pageIndex.value * PAGE_SIZE
  const end = start + PAGE_SIZE
  const next = allMedia.value.slice(start, end)
  if (next.length > 0) {
    mediaList.value = [...mediaList.value, ...next]
    pageIndex.value++
  }
}

function handleSearch(val) {
  clearTimeout(searchTimer)
  loadList(val)
}

// ==================== 上传 ====================
function handleBeforeUpload(file) {
  uploadFile(file)
  return false
}

async function uploadFile(file) {
  uploading.value = true
  try {
    const res = await uploadMedia(file)
    if (res.code === 200) {
      message.success('上传成功')
      await loadList(searchKeyword.value || undefined)
    } else {
      message.error(res.message || '上传失败')
    }
  } catch {
    message.error('上传失败，请检查网络后重试')
  } finally {
    uploading.value = false
  }
}

// ==================== 操作 ====================
async function handleDelete(item) {
  try {
    const res = await deleteMedia(item.oid)
    if (res.code === 200) {
      message.success('已删除')
      if (selectedOid.value === item.oid) {
        selectedOid.value = null
      }
      await loadList(searchKeyword.value || undefined)
    } else {
      message.error(res.message || '删除失败')
    }
  } catch {
    message.error('删除失败，请重试')
  }
}

function previewImage(item) {
  previewUrl.value = item.storagePath
  previewName.value = item.originalName
  previewVisible.value = true
}

async function copyUrl(item) {
  const fullUrl = window.location.origin + item.storagePath
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

function handleCardClick(item) {
  if (props.selectable) {
    selectedOid.value = selectedOid.value === item.oid ? null : item.oid
    emit('select', selectedOid.value ? item : null)
  }
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadList()
})

onBeforeUnmount(() => {
  clearTimeout(searchTimer)
})
</script>

<style scoped>
.media-space-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 页头 ===== */
.ms-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 20px;
  flex-shrink: 0;
}

.ms-header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.ms-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1a1a2e;
}

.ms-subtitle {
  font-size: 13px;
  color: #8c8c8c;
}

/* ===== 统计栏 ===== */
.ms-stats-bar {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 16px 24px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  flex-shrink: 0;
  margin-bottom: 16px;
}

.ms-stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ms-stat-icon {
  font-size: 22px;
  color: #1677ff;
}

.ms-stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}

.ms-stat-label {
  font-size: 13px;
  color: #8c8c8c;
}

/* ===== 操作栏 ===== */
.ms-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  flex-shrink: 0;
}

/* ===== 图片网格 ===== */
.ms-grid-wrapper {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.ms-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}

.ms-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s;
  cursor: default;
}

.ms-card-selectable {
  cursor: pointer;
}

.ms-card-selectable:hover {
  border-color: #1677ff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.15);
}

.ms-card-selected {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.2);
}

.ms-card-img {
  position: relative;
  width: 100%;
  height: 140px;
  overflow: hidden;
  background: #fafafa;
}

.ms-card-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ms-card-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.ms-card:hover .ms-card-overlay {
  opacity: 1;
}

.ms-overlay-btn {
  color: #fff !important;
  font-size: 16px;
}

.ms-overlay-btn:hover {
  color: #e8edf5 !important;
}

.ms-card-info {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ms-card-name {
  font-size: 13px;
  color: #262626;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-card-size {
  font-size: 12px;
  color: #8c8c8c;
}

/* ===== 加载更多 ===== */
.ms-load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
  font-size: 13px;
}

/* ===== 骨架屏 ===== */
.ms-card-skeleton {
  pointer-events: none;
}

.ms-skel-text {
  display: block;
  height: 12px;
  background: #f0f0f0;
  border-radius: 2px;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
  margin-top: 4px;
}

@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
</style>
