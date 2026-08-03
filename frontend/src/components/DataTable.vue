<template>
  <div class="data-table-wrapper">
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
          style="width: 320px"
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
import { ref, reactive, computed, watch, onBeforeUnmount, useSlots } from 'vue'
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

watch(
  () => props.pagination,
  (val) => {
    if (val && typeof val === 'object') {
      if (val.current != null) innerPagination.current = val.current
      if (val.pageSize != null) innerPagination.pageSize = val.pageSize
    }
  },
  { immediate: true, deep: true }
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

const computedScroll = computed(() => {
  // 仅当用户显式传入 scrollX 时设置 scroll.x，避免自动计算与 pagination 冲突
  const x = props.scrollX || undefined
  const y = props.maxHeight || undefined

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
}

.dt-toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: 4px;
}

.dt-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-right: 4px;
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
