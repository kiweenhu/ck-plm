<template>
  <div>
    <!-- 弹窗模式（默认） -->
    <div v-if="showInlineButton" style="display:flex;align-items:center;gap:8px">
      <a-button @click="openModal" :disabled="disabled" :loading="loading">
        <template #icon><ApartmentOutlined /></template>
        {{ selectedLabel || placeholder }}
      </a-button>
      <a-button v-if="allowClear && selectedValue" size="small" @click="clearSelection" :disabled="disabled">
        <template #icon><CloseOutlined /></template>
      </a-button>
    </div>

    <!-- 内联 TreeSelect 模式 -->
    <a-tree-select
      v-else
      v-model:value="selectedValue"
      :tree-data="treeData"
      :loading="loading"
      :tree-default-expand-all="true"
      :show-search="true"
      :tree-node-filter-prop="'title'"
      :allow-clear="allowClear"
      :placeholder="placeholder"
      :disabled="disabled"
      :field-names="{ label: 'title', key: 'oid', value: 'oid', children: 'children' }"
      style="width: 100%"
      @change="onSelectChange"
    >
      <template #title="nodeData">
        <span>{{ nodeData.displayName || nodeData.name }}</span>
        <code v-if="nodeData.code" style="margin-left:6px;font-size:11px;color:#8c8c8c">{{ nodeData.code }}</code>
      </template>
    </a-tree-select>

    <!-- 弹窗模式 -->
    <a-modal v-model:open="modalVisible" title="选择分类" width="520px" :footer="null">
      <a-spin :spinning="loading" size="small">
        <a-empty v-if="!loading && treeData.length === 0" description="暂无可用的分类，请先到分类管理模块创建" :image-style="{ height: '32px' }" />
        <div v-if="treeData.length > 0">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
            <a-input-search v-model:value="searchText" placeholder="搜索分类" allow-clear size="small" style="flex:1" />
            <a-button size="small" @click="expandAll">展开</a-button>
            <a-button size="small" @click="collapseAll">折叠</a-button>
          </div>
          <div v-if="breadcrumb.length > 0" style="margin-bottom:6px;font-size:12px;color:#8c8c8c">
            所属: <a-breadcrumb style="display:inline">
              <a-breadcrumb-item v-for="(b, i) in breadcrumb" :key="i">{{ b }}</a-breadcrumb-item>
            </a-breadcrumb>
          </div>
          <div style="max-height:360px;overflow-y:auto;border:1px solid #f0f0f0;border-radius:6px;padding:8px">
            <a-tree
              ref="treeRef"
              :tree-data="filteredTree"
              :field-names="{ children: 'children', title: 'displayName', key: 'oid' }"
              :default-expand-all="true"
              :selected-keys="selectedValue ? [selectedValue] : []"
              :expanded-keys="expandedKeys"
              @select="onTreeSelect"
              @expand="onTreeExpand"
            >
              <template #title="nodeData">
                <span>{{ nodeData.displayName || nodeData.name }}</span>
                <code v-if="nodeData.code" style="margin-left:6px;font-size:11px;color:#8c8c8c">{{ nodeData.code }}</code>
              </template>
            </a-tree>
          </div>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { TreeSelect, Button, Modal, Input, Breadcrumb, BreadcrumbItem, Tree, Empty, Spin, message } from 'ant-design-vue'
import { ApartmentOutlined, CloseOutlined } from '@ant-design/icons-vue'
import { getClassificationTree } from '@/api'

// ==================== Props & Emits ====================

const props = defineProps({
  modelValue: { type: [String, Array], default: undefined },
  placeholder: { type: String, default: '请选择分类' },
  disabled: { type: Boolean, default: false },
  allowClear: { type: Boolean, default: true },
  /** true 则使用按钮+弹窗模式（默认），false 则使用 TreeSelect 内联下拉 */
  showInlineButton: { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue', 'change'])

// ==================== 内部状态 ====================

const selectedValue = ref(props.modelValue)
const loading = ref(false)
const treeData = ref([])
const selectedLabel = ref('')
const modalVisible = ref(false)
const searchText = ref('')
const expandedKeys = ref([])
const breadcrumb = ref([])
const treeRef = ref(null)

// 双向绑定
watch(() => props.modelValue, (val) => {
  if (val !== selectedValue.value) {
    selectedValue.value = val
    if (val) buildBreadcrumb(val)
    else breadcrumb.value = []
  }
})

watch(selectedValue, (val) => {
  emit('update:modelValue', val)
})

// 过滤后的树
const filteredTree = computed(() => {
  const keyword = searchText.value?.trim().toLowerCase()
  if (!keyword) return treeData.value
  const filter = (nodes) => {
    const result = []
    for (const node of nodes) {
      const name = (node.displayName || node.name || '').toLowerCase()
      const code = (node.code || '').toLowerCase()
      const children = node.children ? filter(node.children) : []
      if (name.includes(keyword) || code.includes(keyword) || children.length > 0) {
        result.push({ ...node, children: children.length > 0 ? children : node.children })
      }
    }
    return result
  }
  return filter(treeData.value)
})

// ==================== 树操作 ====================

function collectAllKeys(nodes) {
  const keys = []
  for (const node of nodes) {
    if (node.children && node.children.length > 0) {
      keys.push(node.oid)
      keys.push(...collectAllKeys(node.children))
    }
  }
  return keys
}

function expandAll() { expandedKeys.value = collectAllKeys(treeData.value) }
function collapseAll() { expandedKeys.value = [] }
function onTreeExpand(keys) { expandedKeys.value = keys }

function buildBreadcrumb(oid) {
  const path = []
  const find = (nodes, targetOid, parents) => {
    for (const node of nodes) {
      if (node.oid === targetOid) { path.push(...parents, node.displayName || node.name); return true }
      if (node.children && find(node.children, targetOid, [...parents, node.displayName || node.name])) return true
    }
    return false
  }
  find(treeData.value, oid, [])
  breadcrumb.value = path
}

// ==================== 选择事件 ====================

function onSelectChange(value) {
  const node = findNodeByOid(treeData.value, value)
  selectedLabel.value = node ? (node.displayName || node.name) : ''
  emit('change', node ? { oid: node.oid, code: node.code, name: node.displayName || node.name } : null)
}

function onTreeSelect(selectedKeys) {
  if (!selectedKeys || selectedKeys.length === 0) return
  const oid = selectedKeys[0]
  selectedValue.value = oid
  buildBreadcrumb(oid)
  modalVisible.value = false
  const node = findNodeByOid(treeData.value, oid)
  selectedLabel.value = node ? (node.displayName || node.name) : ''
  emit('change', node ? { oid: node.oid, code: node.code, name: node.displayName || node.name } : null)
}

function clearSelection() {
  selectedValue.value = null
  selectedLabel.value = ''
  breadcrumb.value = []
  emit('change', null)
}

// ==================== 弹窗 ====================

function openModal() {
  modalVisible.value = true
  searchText.value = ''
  expandedKeys.value = collectAllKeys(treeData.value)
}

// ==================== 工具函数 ====================

function findNodeByOid(nodes, oid) {
  for (const n of nodes) {
    if (n.oid === oid) return n
    if (n.children?.length) {
      const found = findNodeByOid(n.children, oid)
      if (found) return found
    }
  }
  return null
}

function addTitleField(nodes) {
  if (!nodes || nodes.length === 0) return []
  return nodes.map(node => ({
    ...node,
    title: (node.displayName || node.name || ''),
    children: node.children?.length ? addTitleField(node.children) : undefined,
  }))
}

// ==================== 加载数据 ====================

async function loadTree() {
  loading.value = true
  try {
    const res = await getClassificationTree()
    treeData.value = addTitleField(res?.data || res || [])
    expandedKeys.value = collectAllKeys(treeData.value)
  } catch {
    message.error('加载分类树失败')
  } finally { loading.value = false }
}

onMounted(loadTree)

defineExpose({ loadTree, selectedValue, selectedLabel })
</script>
