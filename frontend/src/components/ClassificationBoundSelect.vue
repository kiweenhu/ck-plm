<template>
  <div>
    <!-- 弹窗模式（默认） -->
    <div v-if="showInlineButton" style="display:flex;align-items:center;gap:8px">
      <a-button @click="openModal" :disabled="disabled || !hasBinding" :loading="loading">
        <template #icon><ApartmentOutlined /></template>
        {{ selectedLabel || placeholder }}
      </a-button>
      <a-button v-if="allowClear && selectedValue" size="small" @click="clearSelection" :disabled="disabled">
        <template #icon><CloseOutlined /></template>
      </a-button>
      <a-tag v-if="!hasBinding && !loading" color="warning">未绑定分类</a-tag>
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
      :placeholder="hasBinding ? placeholder : '该类型未绑定分类，请先在类型管理中绑定'"
      :disabled="disabled || !hasBinding"
      :field-names="{ label: 'title', key: 'oid', value: 'oid', children: 'children' }"
      style="width: 100%"
      @change="onSelectChange"
    >
      <template #title="nodeData">
        <span>{{ nodeData.displayName || nodeData.name }}</span>
        <code v-if="nodeData.code" style="margin-left:6px;font-size:11px;color:#8c8c8c">{{ nodeData.code }}</code>
      </template>
    </a-tree-select>

    <!-- 弹窗 -->
    <a-modal v-model:open="modalVisible" title="选择分类" width="520px" :footer="null">
      <a-spin :spinning="loading" size="small">
        <a-empty v-if="!loading && (!hasBinding || treeData.length === 0)" :description="hasBinding ? '暂无可用的分类' : '该类型未绑定分类，请先在类型管理中为当前类型绑定一个分类节点'" :image-style="{ height: '32px' }" />
        <div v-if="hasBinding && treeData.length > 0">
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
import { TreeSelect, Button, Modal, Input, Breadcrumb, BreadcrumbItem, Tree, Empty, Spin, Tag, message } from 'ant-design-vue'
import { ApartmentOutlined, CloseOutlined } from '@ant-design/icons-vue'
import { getTypeClassificationSubtree } from '@/api'

// ==================== Props & Emits ====================

const props = defineProps({
  modelValue: { type: [String, Array], default: undefined },
  /** 类型定义 oid，用于获取该类型绑定的分类子树 */
  typeDefinitionOid: { type: String, default: null },
  placeholder: { type: String, default: '请选择分类' },
  disabled: { type: Boolean, default: false },
  allowClear: { type: Boolean, default: true },
  /** true=按钮+弹窗（默认），false=TreeSelect 内联下拉 */
  showInlineButton: { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue', 'change'])

// ==================== 内部状态 ====================

const selectedValue = ref(props.modelValue)
const loading = ref(false)
const treeData = ref([])
const selectedLabel = ref('')
const hasBinding = ref(false)
const boundRootOid = ref(null)
const modalVisible = ref(false)
const searchText = ref('')
const expandedKeys = ref([])
const breadcrumb = ref([])
const treeRef = ref(null)

// 双向绑定
watch(() => props.modelValue, (val) => {
  if (val !== selectedValue.value) {
    selectedValue.value = val
    if (val) {
      syncSelectedLabel(val)
    } else {
      selectedLabel.value = ''
      breadcrumb.value = []
    }
  }
})

/** 根据 oid 在 treeData 中查找节点并同步 selectedLabel 与 breadcrumb */
function syncSelectedLabel(oid) {
  const node = findNodeByOid(treeData.value, oid)
  selectedLabel.value = node ? (node.displayName || node.name) : ''
  buildBreadcrumb(oid)
}

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
  if (!props.typeDefinitionOid) {
    hasBinding.value = false
    treeData.value = []
    return
  }
  loading.value = true
  try {
    const res = await getTypeClassificationSubtree(props.typeDefinitionOid)
    const data = res?.data || res
    console.log('[ClassificationBoundSelect] raw res:', res)
    console.log('[ClassificationBoundSelect] data:', JSON.stringify(data))
    if (data && data.oid) {
      hasBinding.value = true
      boundRootOid.value = data.oid
      // 将单节点子树包装成数组给树组件
      treeData.value = addTitleField([data])
      expandedKeys.value = collectAllKeys(treeData.value)
      // 编辑/回显场景：初始 modelValue 已有值，treeData 加载完成后补齐 selectedLabel
      if (selectedValue.value) syncSelectedLabel(selectedValue.value)
    } else {
      hasBinding.value = false
      treeData.value = []
    }
  } catch {
    hasBinding.value = false
    treeData.value = []
    message.error('加载分类绑定信息失败')
  } finally { loading.value = false }
}

// 当 typeDefinitionOid 变化时重新加载
watch(() => props.typeDefinitionOid, () => {
  selectedValue.value = null
  selectedLabel.value = ''
  breadcrumb.value = []
  loadTree()
})

onMounted(loadTree)

defineExpose({ loadTree, selectedValue, selectedLabel, hasBinding })
</script>
