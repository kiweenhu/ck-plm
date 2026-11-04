<template>
  <TreeSelect
    v-model:value="innerValue"
    :tree-data="docTypeTree"
    :loading="loading"
    tree-default-expand-all
    show-search
    tree-node-filter-prop="title"
    allow-clear
    :placeholder="placeholder"
    :disabled="disabled"
    :field-names="{ label: 'name', key: 'oid', value: 'oid', children: 'children' }"
    style="width: 100%"
    @change="onSelectChange"
  />
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { TreeSelect, message } from 'ant-design-vue'
import { getTypeDefinitionTree } from '@/api'

// ==================== Props & Emits ====================

const props = defineProps({
  modelValue: { type: [String, Number], default: undefined },
  placeholder: { type: String, default: '请选择文档类型' },
  disabled: { type: Boolean, default: false },
  /**
   * 类型树根节点编码：默认 DOCUMENT（文档类型）。
   * 工程数据（ENG_DOCUMENT）传 'ENG_DOCUMENT' —— 否则下拉里只有文档类型，
   * 新建工程数据时根本选不到 FOOTPRINT / PCB_LAYOUT / SCHEMATIC 这些数模类型。
   */
  rootCode: { type: String, default: 'DOCUMENT' },
})

const emit = defineEmits(['update:modelValue', 'change'])

// ==================== 内部状态 ====================

const innerValue = ref(props.modelValue)
const loading = ref(false)
const docTypeTree = ref([])

// 双向绑定
watch(() => props.modelValue, (val) => {
  if (val !== innerValue.value) innerValue.value = val
})

watch(innerValue, (val) => {
  emit('update:modelValue', val)
})

// ==================== 选中事件 ====================

/**
 * 选中节点时，除了 v-model 同步值外，
 * 额外 emit change 事件携带选中节点的 code 和 name。
 * 父组件可据此获取 TypeDefinition 编码用于 page_layout 查询。
 */
function onSelectChange(value) {
  if (!value) {
    emit('change', null)
    return
  }
  const node = findNodeByOid(docTypeTree.value, value)
  emit('change', node ? { oid: node.oid, code: node.code, name: node.name } : null)
}

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

// ==================== 树数据处理 ====================

/**
 * 为每个树节点添加 title 字段用于搜索过滤
 */
function addTitle(nodes) {
  if (!nodes || nodes.length === 0) return []
  return nodes.map(node => ({
    ...node,
    title: node.name,
    children: node.children?.length ? addTitle(node.children) : undefined,
  }))
}

/**
 * 从完整类型树中提取指定根节点（默认 DOCUMENT）及其子树。
 * 返回一个包含该根节点（含其 children）的树，
 * 让用户可以选择根类型本身或其子类型。
 */
function extractSubtree(nodes, rootCode) {
  for (const node of nodes) {
    if (node.code === rootCode) {
      // 返回根节点本身，其 children 保留为子选项
      return addTitle([{
        ...node,
        children: node.children || []
      }])
    }
    if (node.children?.length) {
      const found = extractSubtree(node.children, rootCode)
      if (found) return found
    }
  }
  return []
}

// ==================== 加载数据 ====================

async function loadTree() {
  loading.value = true
  try {
    const res = await getTypeDefinitionTree()
    if (res.code === 200 && res.data) {
      docTypeTree.value = extractSubtree(res.data, props.rootCode)
    }
  } catch {
    message.error('加载类型树失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadTree)

defineExpose({ loadTree })
</script>
