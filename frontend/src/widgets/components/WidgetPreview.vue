<template>
  <component :is="rendered" />
</template>

<script setup>
import { computed } from 'vue'
import { renderWidgetPreview } from '../renderer'

const props = defineProps({
  /** 控件 type */
  type: { type: String, required: true },
  /** 模式 'design' | 'preview' */
  mode: { type: String, default: 'design' },
  /** 用户选项（user-selector 用） */
  userOptions: { type: Array, default: () => [] },
  /** 组织树数据（org-selector 用） */
  orgTreeData: { type: Array, default: () => [] },
  /** 产品线树数据（product-line-select 用） */
  productLineTree: { type: Array, default: () => [] },
  /** 文件夹树数据（folder-select 用） */
  folderTree: { type: Array, default: () => [] },
  /** placeholder */
  placeholder: { type: String, default: '' }
})

const rendered = computed(() =>
  renderWidgetPreview(props.type, props.mode, {
    userOptions: props.userOptions,
    orgTreeData: props.orgTreeData,
    productLineTree: props.productLineTree,
    folderTree: props.folderTree
  })
)
</script>
