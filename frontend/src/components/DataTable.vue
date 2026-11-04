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
      :locale="{ emptyText }"
      :scroll="computedScroll"
      :expanded-row-render="expandedRowRender"
      :row-expandable="rowExpandable"
      :expanded-row-keys="expandedRowKeys"
      :row-selection="rowSelection"
      @expand="handleExpand"
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
  /**
   * 横向滚动宽度：传数字 = 表宽至少这么多 px；传 'max-content' = 随列宽自然变宽、
   * 超出容器时表体自己出横向滚动条（宽表请用后者 —— 列宽合计超过容器时，
   * 不传会被直接裁掉右半边，连滚动条都没有）。
   */
  scrollX: { type: [Number, String], default: undefined },
  /** 表格内容区最大高度（px），超出后表体可滚动，表头/分页固定 */
  maxHeight: { type: Number, default: undefined },
  /** 撑满父容器高度：表体自动滚动、分页器固定在底部（优先级高于 maxHeight） */
  stretch: { type: Boolean, default: false },
  expandedRowRender: { type: Function, default: null },
  /**
   * 哪些行可展开 —— 不传 = 全部行都可展开（沿用 a-table 默认）。
   * 典型用法：阶段门表格里只有「技术门」能展开看它名下的评审，决策门没有下级内容，
   * 给每个决策门都挂一个点了没反应的箭头只会误导。
   */
  rowExpandable: { type: Function, default: null },
  /**
   * 受控的展开行 key 列表。传了就由调用方决定展开哪些行（`@expand` 回来自己改）。
   * 不传 = a-table 内部状态（点一下展一下）。
   */
  expandedRowKeys: { type: Array, default: null },
  rowSelection: { type: Object, default: null },
  /** 树形数据子节点字段名（如 'children'），设置后启用树形表格 */
  childrenColumnName: { type: String, default: undefined },
})

const emit = defineEmits(['update:pagination', 'update:search', 'change', 'expand'])

/** 插槽：展开行内容可以由调用方用 #expandedRowRender 给（不必再传函数 prop） */
const slots = useSlots()

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

// 数据变少时收敛页码 —— 否则会切出空表，且症状极易被误判成"筛选没生效"。
// 真实案例：46 条 / 每页 10 条，用户翻到第 2~5 页后再选阶段筛选；总数掉到 5 条，
// 但 current 仍停在第 2 页 → slice(10,20) 切一个只有 5 条的数组 → 表体 "No data"，
// 而分页器因总数变小只画得出 1 页、还写着"共 5 条" —— 三个现象同时出现。
// 只在"前端分页"模式下收敛；后端分页由服务端负责（这里不动）。
watch(
  () => [frontendTotal.value, innerPagination.pageSize],
  () => {
    if (props.paginationMode !== 'frontend') return
    if (props.pagination === false) return
    const maxPage = Math.max(1, Math.ceil(frontendTotal.value / innerPagination.pageSize))
    if (innerPagination.current > maxPage) innerPagination.current = maxPage
  },
  { immediate: true }
)

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
/**
 * 展开行的开法（a-table v3 是**扁平 props**，没有 React 那套 `expandable` 对象）：
 * 给 `expandedRowRender`（函数 prop 或 `#expandedRowRender` 插槽）即出展开列；
 * `rowExpandable` 只让部分行可展开；`expandedRowKeys` 受控"哪几行展开"。
 *
 * <p>曾经的 `:expandable="{...}"` 绑定是无效的 —— rc-table 不认这个 prop，
 * 展开列从未真正出现过（本项目此前也没有调用方用到它）。改成扁平写法后才生效。
 */
function handleExpand(expanded, record) {
  emit('expand', expanded, record)
}

// ==================== 工具栏可见 ====================
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
  /* 仅兜底：没传 scroll-x 的窄表若偶尔溢出，由 wrapper 滚（工具栏有 sticky 吸附兜着）。
     传了 scroll-x 的宽表走 a-table 内部滚动（见下方对 min-width 的说明），
     wrapper 不会产生横向滚动条。 */
  overflow-x: auto;
}

/* 撑满模式：靠 JS 精确控制 a-table 的 scroll.y，使 a-table 总高
   (header + scroll.y + 分页器) 恰好填满 wrapper 剩余空间。
   不在 a-table 内部做 flex hack，保持 a-table 自然 block 布局，
   分页器作为 a-table 内部元素在底部自然显示。 */
.data-table-wrapper--stretch {
  /* 保持 flex column 让 toolbar + a-table 堆叠 */
}

/* 列溢出交给 a-table 内部滚动（scroll.x → .ant-table-content/.ant-table-body 出横向
   滚动条，表头由 vc-table 自动同步）。此前这里写 min-width: fit-content 把 .ant-table
   撑到内容宽、让 wrapper 滚 —— 工具栏跟着被拖走，右侧的筛选/搜索/视图按钮不再与
   可视区右缘对齐（用户反馈），故移除。 */
.data-table-wrapper :deep(.ant-table) {
  min-width: 0;
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
  /* wrapper 兜底横向滚动时（无 scroll-x 的表格），工具栏吸附在可视区左缘 ——
     右侧的筛选/搜索/视图按钮始终与可视区右缘对齐，不随表格被拖走 */
  position: sticky;
  left: 0;
  z-index: 1;
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
