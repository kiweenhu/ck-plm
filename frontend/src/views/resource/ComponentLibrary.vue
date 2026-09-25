<template>
  <div class="cl-page">
    <!-- 两栏布局：左分类树（来自分类管理绑定的子树） + 右元器件清单 -->
    <div class="cl-body">
      <!-- 左侧：分类树（只读，来自分类管理） -->
      <div class="cl-tree">
        <div class="cl-tree-scroll">
          <div v-if="!categoryTree" class="cl-tree-empty">
            <a-empty
              :image="aEmptyImage.PRESENTED_IMAGE_SIMPLE"
              description="尚未绑定元器件分类，请联系企业管理员在「系统配置 → 业务配置 → 元器件库分类」中绑定分类管理中的元器件分类节点"
            />
          </div>
          <template v-else>
            <div class="cl-tree-search">
              <a-input-search
                v-model:value="treeSearchKeyword"
                size="small"
                placeholder="搜索分类定位..."
                allow-clear
              />
            </div>
            <div
              class="cl-tree-node"
              :class="{ 'cl-tree-node--active': !selectedCategoryOids.length }"
              @click="selectCategory(null)"
            >
              <span class="cl-tree-toggle cl-tree-toggle--leaf"></span>
              <ApartmentOutlined class="cl-tree-icon" style="color:#1677ff" />
              <span class="cl-tree-name">{{ categoryTree.displayName || categoryTree.name }}（全部）</span>
            </div>
            <div
              class="cl-tree-branch"
              v-for="node in (categoryTree.children || [])"
              :key="node.oid"
            >
              <div
                class="cl-tree-node"
                :class="{
                  'cl-tree-node--active': isNodeSelected(node) && !expandedNodes.has(node.oid),
                  'cl-tree-node--match': isNodeMatch(node),
                }"
                :ref="el => setNodeRef(node.oid, el)"
                @click="selectCategory(node)"
              >
                <span
                  v-if="node.children?.length"
                  class="cl-tree-toggle"
                  @click.stop="toggleNode(node.oid)"
                >
                  <CaretDownOutlined :class="{ rotated: !expandedNodes.has(node.oid) }" />
                </span>
                <span v-else class="cl-tree-toggle cl-tree-toggle--leaf"></span>
                <FolderOutlined class="cl-tree-icon" />
                <span class="cl-tree-name">{{ node.displayName || node.name }}</span>
                <span class="cl-tree-count">{{ nodeCount(node) }}</span>
              </div>
              <template v-if="node.children?.length && expandedNodes.has(node.oid)">
                <div
                  v-for="child in flattenLevel(node.children)"
                  :key="child.oid"
                  class="cl-tree-node cl-tree-node--child"
                  :style="{ paddingLeft: `${24 + (child._depth - 1) * 14}px` }"
                  :class="{
                    'cl-tree-node--active': isNodeSelected(child) && !hasChildren(child),
                    'cl-tree-node--match': isNodeMatch(child),
                  }"
                  :ref="el => setNodeRef(child.oid, el)"
                  @click="selectCategory(child)"
                >
                  <span class="cl-tree-toggle cl-tree-toggle--leaf"></span>
                  <FolderOutlined class="cl-tree-icon" />
                  <span class="cl-tree-name">{{ child.displayName || child.name }}</span>
                  <span class="cl-tree-count">{{ nodeCount(child) }}</span>
                </div>
              </template>
            </div>
          </template>
        </div>
      </div>

      <!-- 右侧：元器件清单（ELECTRONIC 软类型的 Part） -->
      <div class="cl-content">
        <DataTable
          :columns="componentColumns"
          :data-source="componentList"
          :loading="componentLoading"
          :pagination="{ pageSize: 15, showSizeChanger: true }"
          row-key="oid"
          size="small"
          searchable
          search-placeholder="搜索编码/名称..."
          :search-fields="['number', 'name']"
        >
          <template #toolbar-left>
            <div class="cl-content-bar-inline">
              <FolderOutlined style="color:#faad14;font-size:14px" />
              <span style="color:#999;font-size:12px;margin-right:4px">当前位置：</span>
              <strong style="font-size:13px">{{ currentCategoryPath }}</strong>
              <a-button size="small" type="link" v-if="selectedCategoryOids.length" @click="selectCategory(null)">
                查看全部
              </a-button>
            </div>
          </template>
          <template #toolbar>
            <a-button type="primary" size="small" :disabled="!resourceCtx?.containerOid" @click="openCreateModal">
              <PlusOutlined /> {{ libLabel }}{{ libAction }}
            </a-button>
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'checkout_status'">
              <a-tooltip
                v-if="record.checkedOut"
                :title="`已检出：${record.checkedOutBy || '-'}${record.checkedOutComment ? '\n注释：' + record.checkedOutComment : ''}`"
              >
                <LockOutlined style="color:#fa8c16;font-size:13px" />
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'number'">
              <router-link
                class="cl-part-link"
                :to="`/part/${record.oid}`"
                :title="`查看 ${record.name || ''} 详情`"
              >{{ record.number || '-' }}</router-link>
            </template>
            <template v-else-if="column.key === 'displayVersion'">
              <a-tag color="blue" size="small">{{ record.displayVersion || '-' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.statusCode)" size="small">
                {{ record.statusName || record.statusCode || '-' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'checkout'">
              <a-tag v-if="record.checkedOut" color="orange" size="small">已检出: {{ record.checkedOutBy || '-' }}</a-tag>
              <a-tag v-else color="green" size="small">已检入</a-tag>
            </template>
            <template v-else-if="column.key === 'category'">
              <a-tag v-if="record.clsOid && categoryNameMap[record.clsOid]" size="small" color="geekblue">
                {{ categoryNameMap[record.clsOid] }}
              </a-tag>
              <span v-else style="color:#bfbfbf">-</span>
            </template>
            <template v-else-if="column.key === 'name'">
              <span style="color:#262626">{{ record.name }}</span>
            </template>
            <template v-else-if="column.key === 'action'">
              <!-- 复用 Part 行操作组件（元器件即 ELECTRONIC 软类型的 Part）：
                   无主文件故关闭下载；移动语义为「变更分类」 -->
              <PartRowActionMenu
                :record="record"
                :show-download="false"
                move-mode="category"
                @success="loadComponents"
              />
            </template>
          </template>
        </DataTable>
      </div>
    </div>

    <!-- 元器件申请弹窗（ELECTRONIC 软类型的 Part，规格参数按所选分类 IBA 动态渲染） -->
    <a-modal
      v-model:visible="createModalVisible"
      :title="`${libLabel}${libAction}`"
      ok-text="创建"
      cancel-text="取消"
      :confirm-loading="createSaving"
      width="640px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 160px)', overflowY: 'auto', padding: '12px 16px 16px' }"
      @ok="confirmCreate"
    >
      <div v-if="!resourceCtx?.containerOid" class="cl-create-warn">
        企业资源容器尚未初始化，请联系平台管理员检查后端初始化日志。
      </div>

      <!-- 申请类型固定为电子元器件（ELECTRONIC 软类型），不可修改 -->
      <div class="cl-create-type">
        <span class="cl-create-type-label">入库类型</span>
        <a-tag color="blue">{{ typeCodeName }}</a-tag>
        <code class="cl-create-type-code">{{ resourceCtx?.typeCode || 'ELECTRONIC' }}</code>
        <span class="cl-create-type-hint">使用{{ typeCodeName }}软类型的专属布局，编码留空时按编码规则自动生成</span>
      </div>

      <!-- Part 动态表单：entity-code 固定 ELECTRONIC，自动带出其页面布局与分类 IBA 字段 -->
      <DynamicForm
        v-if="createModalVisible && resourceCtx?.containerOid"
        ref="dynamicFormRef"
        :key="dynamicFormKey"
        :entity-code="resourceCtx?.typeCode || 'ELECTRONIC'"
        operation-code="create"
        fallback-entity-code="PART"
        :current-container-oid="resourceCtx.containerOid"
        :current-stage-oid="resourceCtx.stageOid"
        v-model="form"
      />
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { message, Empty } from 'ant-design-vue'
import { FolderOutlined, PlusOutlined, ApartmentOutlined, LockOutlined, CaretDownOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import DynamicForm from '@/components/DynamicForm.vue'
import PartRowActionMenu from '@/components/PartRowActionMenu.vue'
import {
  getLibraryCategoryTree, getElectronicComponents,
  createPart,
} from '@/api'

const aEmptyImage = Empty

const props = defineProps({
  /** 资源子库 code：COMPONENT（元器件库）/ STD_PART（标准件库）/ GEN_PART（通用件库） */
  libCode: { type: String, default: 'COMPONENT' },
})

/** 资源库中文名（用于按钮 / 弹窗文案） */
const libLabel = computed(() => {
  const map = { COMPONENT: '元器件', STD_PART: '标准件', GEN_PART: '通用件' }
  return map[props.libCode] || '元器件'
})

/**
 * 入库动作文案：
 * 元器件走「申请」（由设计/采购发起申请），标准件与通用件走「入库」（采购/标准化后直接入库）。
 */
const libAction = computed(() => (props.libCode === 'COMPONENT' ? '申请' : '入库'))

/** 当前资源库的对象类型中文名（基于后端返回的 typeCode） */
const typeCodeName = computed(() => {
  const code = resourceCtx.value?.typeCode || 'ELECTRONIC'
  const map = { ELECTRONIC: '电子元器件', STRUCTURAL: '结构件' }
  return map[code] || code
})

// ==================== 分类树（来自分类管理绑定的子树，只读） ====================
const categoryTree = ref(null)
const selectedCategoryOids = ref([])
const expandedNodes = ref(new Set())
/** clsOid → 分类名 映射（用于列表展示与分类路径） */
const categoryNameMap = ref({})
/** 分类树搜索关键字（高亮 + 展开 + 滚动定位） */
const treeSearchKeyword = ref('')
/** 节点 DOM 引用映射（用于滚动到首个匹配） */
const nodeRefs = new Map()
function setNodeRef(oid, el) {
  if (el) nodeRefs.set(oid, el)
  else nodeRefs.delete(oid)
}

async function loadCategoryTree() {
  try {
    const res = await getLibraryCategoryTree(props.libCode)
    categoryTree.value = res?.code === 200 ? (res.data || null) : null
    if (categoryTree.value) {
      const map = {}
      const walk = (n) => {
        if (!n) return
        map[n.oid] = n.displayName || n.name || n.code || ''
        for (const c of (n.children || [])) walk(c)
      }
      walk(categoryTree.value)
      categoryNameMap.value = map
    } else {
      categoryNameMap.value = {}
    }
  } catch { categoryTree.value = null }
}

function hasChildren(node) {
  return !!(node.children && node.children.length)
}

function toggleNode(oid) {
  const s = new Set(expandedNodes.value)
  s.has(oid) ? s.delete(oid) : s.add(oid)
  expandedNodes.value = s
}

function collectOids(node, acc = []) {
  if (!node) return acc
  acc.push(node.oid)
  for (const c of (node.children || [])) collectOids(c, acc)
  return acc
}

function nodeCount(node) {
  return collectOids(node).length - 1 || ''
}

function flattenLevel(children, depth = 1, acc = []) {
  for (const c of (children || [])) {
    acc.push({ ...c, _depth: depth })
    if (c.children?.length && expandedNodes.value.has(c.oid)) {
      flattenLevel(c.children, depth + 1, acc)
    }
  }
  return acc
}

function isNodeSelected(node) {
  return selectedCategoryOids.value.length === 1 && selectedCategoryOids.value[0] === node.oid
}

/** 节点 displayName/name 是否包含搜索关键字（大小写不敏感） */
function isNodeMatch(node) {
  const kw = treeSearchKeyword.value.trim().toLowerCase()
  if (!kw) return false
  return ((node.displayName || node.name || '') + '').toLowerCase().includes(kw)
}

/** 收集节点及其全部祖先的 oid 集合 */
function collectAncestors(node, root, acc = new Set()) {
  if (!node || !root) return acc
  acc.add(node.oid)
  if (!node.parentOid || node.oid === root.oid) return acc
  const find = (n) => {
    if (n.oid === node.parentOid) return n
    if (n.children) {
      for (const c of n.children) {
        const f = find(c)
        if (f) return f
      }
    }
    return null
  }
  const parent = find(root)
  if (parent && !acc.has(parent.oid)) collectAncestors(parent, root, acc)
  return acc
}

/** 在树中查找第一个匹配节点 oid（深度优先） */
function findFirstMatch(nodes, kw) {
  for (const n of (nodes || [])) {
    const text = ((n.displayName || n.name) || '').toLowerCase()
    if (text.includes(kw)) return n
    const childHit = findFirstMatch(n.children, kw)
    if (childHit) return childHit
  }
  return null
}

/** 搜索关键字变化：自动展开匹配节点的所有祖先 + 滚动到首个匹配 */
watch(treeSearchKeyword, async (kw) => {
  if (!categoryTree.value) return
  const text = (kw || '').trim().toLowerCase()
  if (!text) return
  // 展开所有匹配节点的所有祖先
  const collectMatches = (nodes, acc = []) => {
    for (const n of (nodes || [])) {
      if (((n.displayName || n.name) || '').toLowerCase().includes(text)) {
        acc.push(n)
      }
      collectMatches(n.children, acc)
    }
    return acc
  }
  const matches = collectMatches([categoryTree.value])
  for (const m of matches) {
    const ancestors = collectAncestors(m, categoryTree.value)
    ancestors.forEach(oid => expandedNodes.value.add(oid))
  }
  // 触发响应式刷新（Set 替换以保证 computed 重新触发）
  expandedNodes.value = new Set(expandedNodes.value)
  // 滚动到第一个匹配节点
  await nextTick()
  const first = matches[0]
  if (first && nodeRefs.has(first.oid)) {
    const el = nodeRefs.get(first.oid)
    const scrollEl = el?.closest('.cl-tree-scroll')
    if (scrollEl) {
      scrollEl.scrollTo({ top: el.offsetTop - 12, behavior: 'smooth' })
    }
  }
})

function selectCategory(node) {
  selectedCategoryOids.value = node ? collectOids(node) : []
  if (node) {
    const s = new Set(expandedNodes.value)
    s.add(node.oid)
    expandedNodes.value = s
  }
  loadComponents()
}

const currentCategoryPath = computed(() => {
  if (!categoryTree.value) return '未绑定分类'
  if (!selectedCategoryOids.value.length) return `${categoryTree.value.displayName || categoryTree.value.name}（全部）`
  const targetOid = selectedCategoryOids.value[0]
  const path = []
  const find = (nodes, chain) => {
    for (const n of nodes) {
      if (n.oid === targetOid) { path.push(...chain, n.displayName || n.name); return true }
      if (n.children?.length && find(n.children, [...chain, n.displayName || n.name])) return true
    }
    return false
  }
  find([categoryTree.value], [])
  return path.join(' / ') || `${categoryTree.value.displayName || categoryTree.value.name}（全部）`
})

const categorySelectData = computed(() => {
  if (!categoryTree.value) return []
  const mapNode = (n) => ({
    oid: n.oid,
    label: n.displayName || n.name,
    children: (n.children || []).map(mapNode),
  })
  return [mapNode(categoryTree.value)]
})

// ==================== 清单（ELECTRONIC 软类型的 Part） ====================
const componentColumns = [
  { title: '', dataIndex: 'checkedOut', key: 'checkout_status', width: 32, align: 'center' },
  { title: '编码', key: 'number', width: 170 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '版本', key: 'displayVersion', width: 70, align: 'center' },
  { title: '生命周期', key: 'status', width: 90 },
  { title: '检出', key: 'checkout', width: 110 },
  { title: '分类', key: 'category', width: 130 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 130, align: 'center' },
]

// 生命周期状态颜色：直接按 ck_part_iteration.status 存储的 code 着色
function statusColor(code) {
  const map = {
    // 兼容历史命名
    DRAFT: 'default', INWORK: 'processing', REVIEW: 'warning',
    APPROVED: 'success', RELEASED: 'blue', OBSOLETE: 'error',
    // 项目实际预置状态（LifecycleStatusInitializer）
    WORKING: 'processing', APPROVING: 'warning', PUBLISHED: 'success',
    OFFLINE: 'default', ARCHIVED: 'error',
  }
  return map[code] || 'default'
}

const componentList = ref([])
const componentLoading = ref(false)
const resourceCtx = ref(null)      // { containerOid, containerType, stageOid }

function fmtTime(str) {
  if (!str) return '-'
  return String(str).replace('T', ' ').substring(0, 19)
}

async function loadComponents() {
  componentLoading.value = true
  try {
    const res = await getElectronicComponents({
      resourceCode: props.libCode,
      categoryOids: selectedCategoryOids.value.length ? selectedCategoryOids.value.join(',') : undefined,
    })
    if (res?.code === 200) {
      const data = res.data || {}
      const items = data.items || []
      // 兼容 MyBatis 驼峰/下划线两种返回；补齐行操作组件与表格列所需的检出/版本/状态字段
      componentList.value = items.map(i => ({
        ...i,
        checkedOut: i.checkedOut ?? i.checked_out ?? false,
        checkedOutBy: i.checkedOutBy ?? i.checked_out_by ?? '',
        checkedOutComment: i.checkedOutComment ?? i.checked_out_comment ?? '',
        displayVersion: i.displayVersion ?? i.display_version ?? '-',
        statusCode: i.statusCode ?? i.status_code,
        statusName: i.statusName ?? i.status_name,
        createdAt: fmtTime(i.createdAt || i.created_at),
      }))
      resourceCtx.value = data.context || null
    } else {
      componentList.value = []
    }
  } catch { componentList.value = [] }
  finally { componentLoading.value = false }
}

// ==================== 元器件申请（Part 动态表单，类型固定 ELECTRONIC） ====================
const createModalVisible = ref(false)
const createSaving = ref(false)
const dynamicFormRef = ref(null)
const dynamicFormKey = ref(0)
const form = ref({
  typeDefinitionCode: 'ELECTRONIC', // 固定：电子元器件软类型（不可修改）
  clsOid: null,
})

function openCreateModal() {
  if (!resourceCtx.value?.containerOid) {
    message.warning('企业资源容器未就绪，请检查后端初始化')
    return
  }
  // 重置表单：类型固定 ELECTRONIC，分类默认取当前选中分类（若只选中一个）
  form.value = {
    typeDefinitionCode: 'ELECTRONIC',
    clsOid: selectedCategoryOids.value.length === 1 ? selectedCategoryOids.value[0] : null,
  }
  dynamicFormKey.value += 1 // 强制重建 DynamicForm，重新加载 ELECTRONIC 布局
  createModalVisible.value = true
}

async function confirmCreate() {
  const ctx = resourceCtx.value
  if (!ctx?.containerOid) {
    message.warning('企业资源容器未就绪，请检查后端初始化')
    return
  }
  createSaving.value = true
  try {
    // 动态表单数据（含布局字段与实体 IBA）
    const payload = { ...(form.value || {}) }
    // 分类 IBA 值需从顶层剔除并放到 clsIbaValues（由 PartController 落 ck_cls_iba_data）
    if (dynamicFormRef.value) {
      const clsIbaFieldNames = dynamicFormRef.value.getClsIbaFieldNames() || []
      clsIbaFieldNames.forEach(k => delete payload[k])
      const clsIbaValues = dynamicFormRef.value.getClsIbaValues() || {}
      if (Object.keys(clsIbaValues).length) payload.clsIbaValues = clsIbaValues
    }
    // 固定归属：当前资源子库节点；类型取该库对应的对象软类型
    payload.typeDefinitionCode = resourceCtx.value?.typeCode || 'ELECTRONIC'
    payload.containerOid = ctx.containerOid
    payload.containerType = ctx.containerType || 'CORP_RESOURCE'
    payload.stageOid = ctx.stageOid
    payload.folderOid = null
    if (!payload.name?.trim()) {
      message.warning('请输入元器件名称')
      return
    }
    payload.name = payload.name.trim()

    const res = await createPart(payload)
    if (res?.code === 200) {
      message.success(`元器件申请已提交：${res.data?.number || res.data?.name || ''}`)
      createModalVisible.value = false
      await loadComponents()
    } else {
      message.error(res?.message || '提交失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || '提交失败')
  } finally {
    createSaving.value = false
  }
}

onMounted(async () => {
  await loadCategoryTree()
  await loadComponents()
})
</script>

<style scoped>
.cl-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 两栏布局（参考阶段页面文件夹区） ===== */
.cl-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 240px 1fr;
  grid-template-rows: minmax(0, 1fr);
  gap: 16px;
}

/* ===== 左侧分类树（只读，来自分类管理） ===== */
.cl-tree {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.cl-tree-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  /* 左右留白 8px：与「封装·图符」文件夹树一致，使选中高亮块不贴面板边缘 */
  padding: 6px 8px 8px;
}
.cl-tree-empty {
  padding: 24px 12px;
}
.cl-tree-branch {
  display: block;
}
/* ===== 节点行（对齐「封装·图符」文件夹树的选中效果） ===== */
.cl-tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #1a1a2e;
  white-space: nowrap;
  transition: background .15s;
}
.cl-tree-node:hover {
  background: #f0f5ff;
}
/*
 * 选中态：浅蓝底 + 左侧 3px 主题色竖条 + 名称蓝色加粗。
 * 竖条用 inset box-shadow 而非 border-left —— 子节点缩进是用内联 padding-left 控制的，
 * 若用 border-left 会挤占内边距、导致缩进错位（需额外做 padding 补偿）。
 */
.cl-tree-node--active,
.cl-tree-node--active:hover {
  background: #e6f4ff;
  box-shadow: inset 3px 0 0 #1677ff;
}
.cl-tree-node--active .cl-tree-name {
  color: #1677ff;
  font-weight: 500;
}
.cl-tree-toggle {
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 12px;
  color: #8c8c8c;
  cursor: pointer;
}
.cl-tree-toggle .rotated { transform: rotate(-90deg); transition: transform .15s; }
.cl-tree-toggle--leaf {
  visibility: hidden;
}
.cl-tree-icon {
  color: #faad14;
  font-size: 15px;
  flex-shrink: 0;
}
.cl-tree-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cl-tree-count {
  color: #bfbfbf;
  font-size: 11px;
  flex-shrink: 0;
}

/* 分类树搜索框 */
.cl-tree-search {
  padding: 6px 8px 8px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}
/* 分类树节点搜索匹配高亮 */
.cl-tree-node--match .cl-tree-name {
  color: #fa8c16;
  font-weight: 600;
  background: #fff7e6;
  padding: 0 4px;
  border-radius: 3px;
}

/* ===== 右侧内容 ===== */
.cl-content {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.cl-content-bar-inline {
  display: flex;
  align-items: center;
  gap: 4px;
}
.cl-part-link {
  font-size: 12px;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  color: #1677ff;
  text-decoration: none;
}
.cl-part-link:hover {
  background: #e6f4ff;
  text-decoration: underline;
}
.cl-create-warn {
  color: #fa8c16;
  font-size: 12px;
  background: #fff7e6;
  border: 1px solid #ffe58f;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
}
/* 申请类型固定展示（电子元器件，不可修改） */
.cl-create-type {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  margin-bottom: 12px;
}
.cl-create-type-label {
  color: #8c8c8c;
  font-size: 12px;
  white-space: nowrap;
}
.cl-create-type-code {
  font-size: 12px;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  color: #595959;
}
.cl-create-type-hint {
  color: #bfbfbf;
  font-size: 12px;
}
</style>
