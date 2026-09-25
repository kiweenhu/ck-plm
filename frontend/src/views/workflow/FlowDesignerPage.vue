<template>
  <div class="flow-designer-page">
    <!-- 两态：模板库（入口）↔ 设计器（编辑某模板） -->
    <TemplateLibrary
      v-if="!activeOid"
      :category="activeCategory"
      @update:category="pickCategory"
      @open="openTemplate"
    />

    <FlowDesigner v-else :key="activeOid" :template-oid="activeOid" @back="backToList" />
  </div>
</template>

<script setup>
/**
 * 流程设计器页面壳。
 *
 * <p>承载「模板库 ↔ 设计器」的切换与路由同步：
 * 打开模板写入 `?templateOid=`、当前分组写入 `?category=`（值为分组 <b>oid</b>，
 * 改分组名不会让已分享的链接失效），返回列表清空 templateOid ——
 * 于是刷新页面、分享链接、浏览器前进后退都能回到<b>同一个模板</b>，
 * 从设计器返回时也仍停在<b>原分组</b>（"在组内设计流程"的上下文不丢）。
 *
 * <p>分组由本页持有（而不是留在模板库组件内部）：`v-if` 会在进入设计器时卸载模板库，
 * 组件内的选中状态会随之丢失。写在 route query 上则天然跨"两态"存活。
 *
 * <p>画布与编辑逻辑在 `@/flow-designer/app/*`，本文件不做业务。
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FlowDesigner from '@/flow-designer/app/FlowDesigner.vue'
import TemplateLibrary from '@/flow-designer/app/TemplateLibrary.vue'
import { UNCATEGORIZED } from '@/flow-designer/app/category-repo'

const route = useRoute()
const router = useRouter()

const activeOid = ref(null)
/** 当前分组 oid（null = 全部；UNCATEGORIZED = 未分类） */
const activeCategory = ref(null)

onMounted(() => {
  const fromQuery = route.query?.templateOid
  if (fromQuery && typeof fromQuery === 'string') {
    activeOid.value = fromQuery
  }
  const category = route.query?.category
  if (category && typeof category === 'string') {
    activeCategory.value = category
  }
})

function openTemplate(oid) {
  activeOid.value = oid
  router.replace({ query: { ...route.query, templateOid: oid } })
}

/** 分组变化 → 写进 URL。哨兵（未分类）不入 URL：它不是分组名，分享出去没有意义 */
function pickCategory(value) {
  activeCategory.value = value ?? null
  const query = { ...route.query }
  if (activeCategory.value && activeCategory.value !== UNCATEGORIZED) {
    query.category = activeCategory.value
  } else {
    delete query.category
  }
  router.replace({ query })
}

function backToList() {
  activeOid.value = null
  const query = { ...route.query }
  delete query.templateOid
  router.replace({ query })
}
</script>

<style scoped>
.flow-designer-page {
  padding: 12px;
}
</style>
