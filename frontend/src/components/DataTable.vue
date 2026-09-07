<template>
  <div ref="wrapperRef" class="data-table-wrapper" :class="{ 'data-table-wrapper--stretch': stretch }">
    <!-- 工具栏 -->
    <div v-if="toolbarVisible" class="dt-toolbar">
      <div class="dt-toolbar-left">
        <slot name="toolbar-left" />
      </div>
      <div class="dt-toolbar-right">
        <slot name="toolbar" />
        <a-input-search
          v-if="searchable"
          v-model:value="searchText"
          :placeholder="searchPlaceholder"
          style="width: 220px"
          size="small"
          allow-clear
          @search="handleSearch"
          @change="handleSearchChange"
        />
        <a-popover
          v-if="showColumnToggle && columns.length > 0"
          trigger="click"
          placement="bottomRight"
          overlay-class-name="dt-column-popover"
        >
          <template #content>
            <div class="dt-column-toggle-list">
              <a-checkbox
                :checked="allVisible"
                :indeterminate="indeterminate"
                @change="handleCheckAll"
              >全选</a-checkbox>
              <a-divider style="margin: 8px 0" />
              <a-checkbox
                v-for="col in columnVisList"
                :key="col.key"
                :checked="col.visible"
                @change="toggleColumn(col.key)"
              >{{ col.title }}</a-checkbox>
              <a-divider style="margin: 8px 0" />
              <a-button size="small" block @click="resetColumns">恢复默认</a-button>
            </div>
          </template>
          <a-button size="small">
            <template #icon><SettingOutlined /></template>
            视图
          </a-button>
        </a-popover>
      </div>
    </div>

    <!-- 表格 -->
    <a-table
      :columns="computedColumns"
      :data-source="displayData"
      :loading="loading"
      :pagination="computedPagination"
      :row-key="rowKey"
      :size="size"
      :scroll="computedScroll"
      :expandable="computedExpandable"
      :row-selection="rowSelection"
      :children-column-name="childrenColumnName"
      :default-expand-all-rows="!!childrenColumnName"
      @change="handleTableChange"
    >
      <template #headerCell="{ column }">
        <div class="dt-header-cell">
          <span class="dt-header-title">{{ column.title }}</span>
          <span
            v-if="enableResize"
            class="dt-resize-handle"
            @mousedown.prevent="startResize($event, column)"
          />
        </div>
      </template>
      <template #bodyCell="scope">
        <slot name="bodyCell" v-bind="scope" />
      </template>
      <template v-if="$slots.expandedRowRender" #expandedRowRender="scope">
        <slot name="expandedRowRender" v-bind="scope" />
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick, useSlots } from 'vue'
import { SettingOutlined } from '@ant-design/icons-vue'

// ==================== Props ====================
const props = defineProps({
  columns: { type: Array, required: true },
  dataSource: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  rowKey: { type: [String, Function], default: 'oid' },
  pagination: { type: [Object, Boolean], default: () => ({}) },
  paginationMode: { type: String, default: 'frontend' },
  searchable: { type: Boolean, default: true },
  searchPlaceholder: { type: String, default: '搜索...' },
  searchFields: { type: Array, default: null },
  showColumnToggle: { type: Boolean, default: true },
  enableResize: { type: Boolean, default: true },
  size: { type: String, default: 'middle' },
  total: { type: Number, default: 0 },
  emptyText: { type: String, default: '暂无数据' },
  showQuickJumper: { type: Boolean, default: false },
  showTotal: { type: Function, default: (t) => `共 ${t} 条` },
  scrollX: { type: Number, default: undefined },
  /** 表格内容区最大高度（px），超出后表体可滚动，表头/分页固定 */
  maxHeight: { type: Number, default: undefined },
  /** 撑满父容器高度：表体自动滚动、分页器固定在底部（优先级高于 maxHeight） */
  stretch: { type: Boolean, default: false },
  expandedRowRender: { type: Function, default: null },
  rowSelection: { type: Object, default: null },
  /** 树形数据子节点字段名（如 'children'），设置后启用树形表格 */
  childrenColumnName: { type: String, default: undefined },
})

const emit = defineEmits(['update:pagination', 'update:search', 'change'])

// ==================== 搜索 ====================
const searchText = ref('')

const searchFieldList = computed(() => {
  if (props.searchFields) return props.searchFields
  return props.columns
    .filter((c) => c.dataIndex && c.dataIndex !== 'action')
    .map((c) => c.dataIndex)
})

const filteredData = computed(() => {
  if (props.paginationMode !== 'frontend' || !searchText.value.trim()) {
    return props.dataSource
  }
  const kw = searchText.value.trim().toLowerCase()
  const fields = searchFieldList.value
  if (fields.length === 0) return props.dataSource
  return props.dataSource.filter((row) =>
    fields.some((field) => {
      const val = row[field]
      return val != null && String(val).toLowerCase().includes(kw)
    })
  )
})

function handleSearch() {
  if (props.paginationMode === 'frontend') {
    innerPagination.current = 1
  }
  emit('update:search', searchText.value)
}

function handleSearchChange(e) {
  if (!e.target.value) {
    if (props.paginationMode === 'frontend') {
      innerPagination.current = 1
    }
    emit('update:search', '')
  }
}

// ==================== 排序（前端） ====================
const sortState = ref({ field: null, order: null })

const sortedData = computed(() => {
  const list = filteredData.value
  if (props.paginationMode !== 'frontend' || !sortState.value.field) return list
  const { field, order } = sortState.value
  return [...list].sort((a, b) => {
    const va = a[field] ?? ''
    const vb = b[field] ?? ''
    let result = 0
    if (typeof va === 'number' && typeof vb === 'number') {
      result = va - vb
    } else {
      result = String(va).localeCompare(String(vb), 'zh-CN')
    }
    return order === 'descend' ? -result : result
  })
})

// ==================== 分页 ====================
const innerPagination = reactive({ current: 1, pageSize: 10 })

// 只监听 pagination 的原始值（pageSize / current），而不是对象引用。
// 原因：父组件通常传对象字面量（如 :pagination="{ pageSize: 10 }"），每次渲染
// 都会产生新引用。若 watch 对象引用，用户切换每页条数后父组件一重渲染，
// pageSize 就会被重置回父组件的值，导致"选择 20/page 没反应"。
watch(
  () => (props.pagination && typeof props.pagination === 'object') ? props.pagination.pageSize : undefined,
  (v) => { if (v != null) innerPagination.pageSize = v },
  { immediate: true }
)
watch(
  () => (props.pagination && typeof props.pagination === 'object') ? props.pagination.current : undefined,
  (v) => { if (v != null) innerPagination.current = v },
  { immediate: true }
)

// 前端分页后的数据总数（用于分页组件显示页码）
const frontendTotal = computed(() => {
  if (props.paginationMode !== 'frontend') return 0
  if (props.childrenColumnName) return 0
  return sortedData.value.length
})

const pagedData = computed(() => {
  if (props.pagination === false) return sortedData.value
  const list = sortedData.value
  const start = (innerPagination.current - 1) * innerPagination.pageSize
  return list.slice(start, start + innerPagination.pageSize)
})

// 最终展示数据（树形数据不分页）
const displayData = computed(() => {
  if (props.childrenColumnName) return props.dataSource
  if (props.paginationMode === 'backend') return props.dataSource
  return pagedData.value
})

const computedPagination = computed(() => {
  if (props.childrenColumnName) return false // 树形数据不分页
  if (props.pagination === false) return false
  if (props.paginationMode === 'backend') {
    return {
      current: innerPagination.current,
      pageSize: innerPagination.pageSize,
      total: props.total,
      showSizeChanger: true,
      showQuickJumper: props.showQuickJumper,
      showTotal: props.showTotal,
      hideOnSinglePage: false,
    }
  }
  return {
    current: innerPagination.current,
    pageSize: innerPagination.pageSize,
    total: frontendTotal.value,
    showSizeChanger: true,
    showQuickJumper: props.showQuickJumper,
    showTotal: props.showTotal,
    hideOnSinglePage: false,
  }
})

function handleTableChange(pag, filters, sorter) {
  if (pag) {
    innerPagination.current = pag.current
    innerPagination.pageSize = pag.pageSize
  }
  // 前端排序 —— 仅在排序字段或方向变化时才更新，避免触发 computedScroll 重建
  if (props.paginationMode === 'frontend' && sorter) {
    const nextField = sorter.order ? (sorter.field || sorter.columnKey) : null
    const nextOrder = sorter.order || null
    if (sortState.value.field !== nextField || sortState.value.order !== nextOrder) {
      sortState.value = { field: nextField, order: nextOrder }
    }
  }
  emit('change', {
    pagination: { current: innerPagination.current, pageSize: innerPagination.pageSize },
    filters,
    sorter,
  })
}

// ==================== 滚动 ====================
const _scrollCache = ref({ x: undefined, y: undefined })

// 撑满模式：动态测量 wrapper 可用高度，得到表体高度（表头/分页器固定）
const wrapperRef = ref(null)
const stretchScrollY = ref(undefined)
let _resizeObserver = null

function measureStretchHeight() {
  if (!props.stretch || !wrapperRef.value) return
  const wrapper = wrapperRef.value
  const wrapperH = wrapper.clientHeight
  if (wrapperH <= 0) return
  const toolbar = wrapper.querySelector('.dt-toolbar')
  const toolbarH = toolbar ? toolbar.offsetHeight : 0
  // 表头预估（small ~39, middle ~47），渲染后用实测值
  const header = wrapper.querySelector('.ant-table-thead')
  const estHeaderH = props.size === 'small' ? 39 : 47
  const headerH = header ? header.offsetHeight : estHeaderH
  // 分页器：offsetHeight + 上下 margin（antd 分页器默认 margin:16px 0，
  // 漏算 margin 会让 a-table 总高超出容器，产生多余的垂直滚动条）
  const pagination = wrapper.querySelector('.ant-table-pagination')
  const estPaginationH = props.size === 'small' ? 48 : 64
  let paginationH = estPaginationH
  if (pagination) {
    const cs = getComputedStyle(pagination)
    paginationH = pagination.offsetHeight
      + (parseFloat(cs.marginTop) || 0)
      + (parseFloat(cs.marginBottom) || 0)
  }
  const bodyH = wrapperH - toolbarH - headerH - paginationH
  if (bodyH > 40) stretchScrollY.value = bodyH
}

onMounted(() => {
  if (props.stretch && wrapperRef.value) {
    _resizeObserver = new ResizeObserver(() => measureStretchHeight())
    _resizeObserver.observe(wrapperRef.value)
    // 工具栏内容可能换行导致高度变化，需一并监听以重新计算表体高度
    const toolbar = wrapperRef.value.querySelector('.dt-toolbar')
    if (toolbar) _resizeObserver.observe(toolbar)
    nextTick(() => {
      measureStretchHeight()
      // 布局稳定（工具栏换行、分页器渲染完成）后再校正一次，消除残留滚动
      setTimeout(() => measureStretchHeight(), 150)
    })
  }
})

// 数据/分页变化时表头、分页器高度可能改变（如分页器从无到有），需重新测量
watch(
  () => [props.dataSource, props.pagination, props.total],
  () => {
    if (props.stretch) nextTick(() => measureStretchHeight())
  }
)

onBeforeUnmount(() => {
  if (_resizeObserver) {
    _resizeObserver.disconnect()
    _resizeObserver = null
  }
})

const computedScroll = computed(() => {
  const x = props.scrollX || undefined
  // 撑满模式：优先用动态测量的表体高度
  let y = props.maxHeight || undefined
  if (props.stretch) y = stretchScrollY.value || undefined

  // 值未变时返回缓存引用，避免 a-table 因新对象引用而重置内部状态
  if (_scrollCache.value.x === x && _scrollCache.value.y === y) {
    return _scrollCache.value
  }
  const next = x != null || y != null ? { x, y } : undefined
  _scrollCache.value = next || { x: undefined, y: undefined }
  return next
})

// ==================== 展开行 ====================
const computedExpandable = computed(() => {
  if (props.expandedRowRender) {
    return { expandedRowRender: props.expandedRowRender }
  }
  return undefined
})

// ==================== 工具栏可见 ====================
const slots = useSlots()
const toolbarVisible = computed(() =>
  props.searchable || props.showColumnToggle || !!slots.toolbar || !!slots['toolbar-left']
)

// ==================== 列显隐 ====================
const storageKey = computed(() => {
  try {
    const path = window.location.hash.replace('#', '') || window.location.pathname
    return `dt-col-vis-${path}`
  } catch {
    return 'dt-col-vis-default'
  }
})

const columnVisList = reactive([])

function initColumnVis() {
  const existingKeys = new Set(props.columns.map((c) => c.key))
  for (let i = columnVisList.length - 1; i >= 0; i--) {
    if (!existingKeys.has(columnVisList[i].key)) {
      columnVisList.splice(i, 1)
    }
  }
  for (const col of props.columns) {
    if (!columnVisList.find((c) => c.key === col.key)) {
      columnVisList.push({ key: col.key, title: col.title, visible: true })
    }
  }
}

function loadColumnVis() {
  try {
    const raw = localStorage.getItem(storageKey.value)
    if (raw) {
      const saved = JSON.parse(raw)
      if (Array.isArray(saved)) {
        for (const col of columnVisList) {
          const match = saved.find((s) => s.key === col.key)
          if (match) col.visible = match.visible
        }
      }
    }
  } catch { /* ignore */ }
}

function saveColumnVis() {
  try {
    const data = columnVisList.map((c) => ({ key: c.key, visible: c.visible }))
    localStorage.setItem(storageKey.value, JSON.stringify(data))
  } catch { /* ignore */ }
}

function toggleColumn(key) {
  const col = columnVisList.find((c) => c.key === key)
  if (col) {
    col.visible = !col.visible
    saveColumnVis()
  }
}

function handleCheckAll(e) {
  const checked = e.target.checked
  columnVisList.forEach((c) => { c.visible = checked })
  saveColumnVis()
}

function resetColumns() {
  columnVisList.forEach((c) => { c.visible = true })
  saveColumnVis()
}

const allVisible = computed(() =>
  columnVisList.length > 0 && columnVisList.every((c) => c.visible)
)
const indeterminate = computed(() =>
  columnVisList.some((c) => c.visible) && !allVisible.value
)

// 实际渲染列（含显隐 + 宽度 + sorter）
const computedColumns = computed(() => {
  let cols = props.columns
  // 列显隐过滤（始终保留 action 列）
  if (props.showColumnToggle && columnVisList.length > 0) {
    const visibleKeys = new Set(
      columnVisList.filter((c) => c.visible).map((c) => c.key)
    )
    cols = cols.filter((c) => c.key === 'action' || visibleKeys.has(c.key))
  }
  // 应用拖拽宽度 + 排序
  return cols.map((c) => {
    const key = c.key || c.dataIndex
    const result = { ...c }
    if (props.enableResize && colWidths[key]) {
      result.width = colWidths[key]
    }
    // 自动为 dataIndex 列添加 sorter（除非显式设为 false）
    if (result.dataIndex && !result.key?.startsWith('action') && result.sorter !== false) {
      result.sorter = (a, b) => {
        const va = a[result.dataIndex] ?? ''
        const vb = b[result.dataIndex] ?? ''
        if (typeof va === 'number' && typeof vb === 'number') return va - vb
        return String(va).localeCompare(String(vb), 'zh-CN')
      }
      result.sortOrder = sortState.value.field === result.dataIndex ? sortState.value.order : null
    }
    return result
  })
})

// ==================== 列拖拽调整宽度 ====================
const colWidths = reactive({})

let resizingCol = null
let startX = 0
let startWidth = 0

function startResize(e, column) {
  if (!props.enableResize) return
  const th = e.target.closest('th')
  if (!th) return
  resizingCol = column
  startX = e.clientX
  startWidth = parseFloat(getComputedStyle(th).width)
  document.addEventListener('mousemove', handleResizeMove)
  document.addEventListener('mouseup', handleResizeEnd)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function handleResizeMove(e) {
  if (!resizingCol) return
  const delta = e.clientX - startX
  const key = resizingCol.key || resizingCol.dataIndex
  colWidths[key] = Math.max(40, startWidth + delta)
}

function handleResizeEnd() {
  resizingCol = null
  document.removeEventListener('mousemove', handleResizeMove)
  document.removeEventListener('mouseup', handleResizeEnd)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', handleResizeMove)
  document.removeEventListener('mouseup', handleResizeEnd)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
})

// ==================== 初始化列显隐 ====================
watch(
  () => props.columns,
  () => {
    initColumnVis()
    loadColumnVis()
  },
  { immediate: true }
)

// ==================== 对外暴露 ====================
defineExpose({
  resetColumns,
  searchText,
})
</script>

<style scoped>
.data-table-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow-x: auto;
}

/* 撑满模式：靠 JS 精确控制 a-table 的 scroll.y，使 a-table 总高
   (header + scroll.y + 分页器) 恰好填满 wrapper 剩余空间。
   不在 a-table 内部做 flex hack，保持 a-table 自然 block 布局，
   分页器作为 a-table 内部元素在底部自然显示。 */
.data-table-wrapper--stretch {
  /* 保持 flex column 让 toolbar + a-table 堆叠 */
}

/* 确保 a-table 自身不裁剪分页; 列溢出由父容器 overflow-x 处理 */
.data-table-wrapper :deep(.ant-table) {
  min-width: fit-content;
}


.dt-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0 8px;
  flex-shrink: 0;
  /* 内容过多时换行，避免工具栏撑宽容器产生横向滚动条 */
  flex-wrap: wrap;
  gap: 8px 12px;
}

.dt-toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: 4px;
  flex-wrap: wrap;
  min-width: 0;
}

.dt-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-right: 4px;
  flex-wrap: wrap;
  min-width: 0;
}

/* 列头拖拽手柄 */
.dt-header-cell {
  display: flex;
  align-items: center;
  width: 100%;
  position: relative;
  min-height: 20px;
}

.dt-header-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dt-resize-handle {
  position: absolute;
  right: -8px;
  top: -8px;
  bottom: -8px;
  width: 16px;
  cursor: col-resize;
  user-select: none;
  z-index: 1;
}

.dt-resize-handle::after {
  content: '';
  position: absolute;
  right: 6px;
  top: 8px;
  bottom: 8px;
  width: 4px;
  border-radius: 2px;
  background: transparent;
  transition: background 0.15s;
}

.dt-resize-handle:hover::after {
  background: #1677ff44;
}

.dt-resize-handle:active::after {
  background: #1677ff88;
}

/* 列显隐面板 */
.dt-column-toggle-list {
  min-width: 180px;
  max-height: 360px;
  overflow-y: auto;
}

.dt-column-toggle-list :deep(.ant-checkbox-wrapper) {
  display: flex;
  margin: 4px 0;
  padding: 3px 2px;
}
</style>
