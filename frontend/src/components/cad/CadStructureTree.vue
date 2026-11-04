<template>
  <div class="cst">
    <div class="cst-header">
      <span class="cst-title">装配结构</span>
      <span class="cst-count">{{ nodes.length }} 项</span>
    </div>

    <a-input
      v-model:value="keyword"
      size="small"
      placeholder="搜索零件名 / 编号"
      allow-clear
      class="cst-search"
    />

    <div ref="treeHost" class="cst-tree">
      <a-tree
        v-if="treeData.length"
        :tree-data="treeData"
        :selected-keys="selectedKeys"
        :expanded-keys="expandedKeys"
        :auto-expand-parent="searching"
        block-node
        @select="onSelect"
        @expand="onExpand"
      >
        <template #title="row">
          <span class="cst-node">
            <span v-if="row.meta?.color" class="cst-dot" :style="{ background: row.meta.color }" />
            <span class="cst-name" :title="row.title">{{ row.shortTitle }}</span>
            <!-- 折叠过容器链：行内放不下完整路径，用省略号提示"这一行其实合并了几层"，完整路径在 title 里 -->
            <span v-if="row.foldCount > 0" class="cst-fold">⋯</span>
            <a-tag v-if="row.meta?.type === 'assembly'" class="cst-tag">装配 {{ row.childCount }}</a-tag>
            <a-tag v-else-if="row.meta?.instanceOf" class="cst-tag cst-tag-instance">实例</a-tag>
          </span>
        </template>
      </a-tree>
      <!--
        :image="false" 才是"不要占位图"。传 null 会让 ant-design-vue 直接崩：
        Empty 里用 `typeof image === 'object' && 'type' in image` 判断，
        typeof null 也是 'object' → `'type' in null` → TypeError: Cannot use 'in' operator...
        打开三维预览的瞬间结构树还没加载，正好走这个空分支，控制台就报红。
      -->
      <a-empty v-else description="暂无结构树数据" :image="false" class="cst-empty" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'

const props = defineProps({
  /** 扁平节点数组：{ id, parentId, name, number, type, instanceOf, ... } */
  nodes: { type: Array, default: () => [] },
  selectedId: { type: String, default: '' }
})

const emit = defineEmits(['select'])

const keyword = ref('')
const treeHost = ref(null)
const expandedKeys = ref([])
/** 本组件的高亮游标：受控于外部 selectedId，但"再次点同一节点"时不能让它丢掉高亮 */
const innerSelected = ref('')

const searching = computed(() => !!keyword.value.trim())
const selectedKeys = computed(() => (innerSelected.value ? [innerSelected.value] : []))

/**
 * 扁平节点 → 可渲染的行森林。
 *
 * <p>除了建树，还做两件在 CAD 装配树上必须做的事：
 * 1. 单链容器折叠：`n0→n1→…→n5` 这种一路只挂一个子装配的层级，
 *    CATIA / SolidWorks 导出的 STEP 里能连着好几层，展开后每层也只有一行，
 *    除了把用户的手指累死没有别的用处 —— 折成一行，key 取最深的那个节点。
 * 2. 记录 id → 行、行 → 父行 的映射：三维里点中的零件要能在树上被"露出来"
 *    （逐级展开 + 滚动到位），没有这两张表就只能靠猜。
 */
const model = computed(() => {
  const list = props.nodes || []
  const empty = { rows: [], rowKeyOf: new Map(), parentRowOf: new Map() }
  if (!list.length) return empty

  const byId = new Map()
  const items = list.map((n) => ({
    key: n.id,
    title: n.name || n.number || n.id,
    meta: n,
    children: []
  }))
  items.forEach((it) => byId.set(it.key, it))

  const roots = []
  for (let i = 0; i < list.length; i++) {
    const parentId = list[i].parentId
    const item = items[i]
    // 缺 parentId 或父节点不存在的，一律挂到根下（不丢节点，STRUCTURE 容错优先）
    if (parentId && byId.has(parentId) && parentId !== item.key) {
      byId.get(parentId).children.push(item)
    } else {
      roots.push(item)
    }
  }

  const kw = keyword.value.trim().toLowerCase()
  let forest = roots
  if (kw) {
    // 搜索：保留命中节点及其祖先链，避免"搜到了但展开后找不到"
    const matched = new Set()
    const parentOf = new Map()
    list.forEach((n) => { if (n.parentId) parentOf.set(n.id, n.parentId) })

    const keepWithAncestors = (id) => {
      let cur = id
      while (cur && !matched.has(cur)) {
        matched.add(cur)
        cur = parentOf.get(cur)
      }
    }
    list.forEach((n) => {
      if (((n.name || '') + ' ' + (n.number || '')).toLowerCase().includes(kw)) keepWithAncestors(n.id)
    })

    const prune = (item) => {
      const kept = item.children.map(prune).filter(Boolean)
      if (!matched.has(item.key) && !kept.length) return null
      return { ...item, children: kept }
    }
    forest = roots.map(prune).filter(Boolean)
  }

  const rowKeyOf = new Map()
  const parentRowOf = new Map()

  const buildRow = (item, depth, parentKey) => {
    let cur = item
    const chain = [item.key]
    // 搜索态不折叠：命中项的祖先链本身就是"路径信息"，折掉反而看不懂
    if (!kw) {
      while (cur.children.length === 1 && cur.children[0].children.length > 0) {
        cur = cur.children[0]
        chain.push(cur.key)
      }
    }
    chain.forEach((id) => rowKeyOf.set(id, cur.key))
    parentRowOf.set(cur.key, parentKey)

    const titles = chain.map((id) => byId.get(id)?.title || id)
    return {
      key: cur.key,
      /** 完整路径：只在 hover 提示里出现，行内放不下 */
      title: titles.join(' / '),
      shortTitle: titles[titles.length - 1],
      foldCount: chain.length - 1,
      childCount: cur.children.length,
      meta: cur.meta,
      depth,
      children: cur.children.map((c) => buildRow(c, depth + 1, cur.key))
    }
  }

  return { rows: forest.map((r) => buildRow(r, 0, '')), rowKeyOf, parentRowOf }
})

const treeData = computed(() => model.value.rows)

function flatten(rows, out = []) {
  rows.forEach((r) => {
    out.push(r)
    flatten(r.children, out)
  })
  return out
}

// 首屏默认展开前两层，免得用户面对一棵全折叠的树；折叠掉单链容器后，
// 这两层通常已经能直接看到零件了
watch(() => props.nodes, () => {
  expandedKeys.value = flatten(model.value.rows).filter((r) => r.depth < 2).map((r) => r.key)
}, { immediate: true })

watch(keyword, () => {
  if (searching.value) expandedKeys.value = flatten(model.value.rows).map((r) => r.key)
})

// 三维里点中零件 / 上层把 selectedId 推下来：逐级展开祖先并滚到那一行，
// 否则选中的零件可能藏在四层折叠里，用户只看到"树没反应"
watch(() => props.selectedId, (id) => {
  innerSelected.value = id || ''
  if (!id) return
  reveal(id)
}, { immediate: true })

function reveal(id) {
  const { rowKeyOf, parentRowOf } = model.value
  const rowKey = rowKeyOf.get(id) || id

  const need = []
  let p = parentRowOf.get(rowKey)
  while (p) {
    need.push(p)
    p = parentRowOf.get(p)
  }
  if (need.length) {
    const set = new Set(expandedKeys.value)
    let changed = false
    need.forEach((k) => {
      if (!set.has(k)) {
        set.add(k)
        changed = true
      }
    })
    if (changed) expandedKeys.value = Array.from(set)
  }
  nextTick(scrollToSelected)
}

function scrollToSelected() {
  const host = treeHost.value
  if (!host) return
  // 选中项在树里唯一，直接用 ant 的选中 class 定位，比拼接 data-key 可靠
  const el = host.querySelector('.ant-tree-node-content-wrapper.ant-tree-node-selected')
  if (!el) return
  const r = el.getBoundingClientRect()
  const c = host.getBoundingClientRect()
  if (r.top < c.top) host.scrollTop += r.top - c.top - 8
  else if (r.bottom > c.bottom) host.scrollTop += r.bottom - c.bottom + 8
}

function onSelect(keys, info) {
  const id = keys && keys.length ? keys[0] : (info?.node?.key || '')
  if (!id) return
  innerSelected.value = id
  emit('select', info?.node?.meta || props.nodes.find((n) => n.id === id) || null)
}

function onExpand(keys) {
  expandedKeys.value = keys
}
</script>

<style scoped>
.cst {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 8px;
}
.cst-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.cst-title { font-size: 13px; font-weight: 500; }
.cst-count { font-size: 12px; color: #999; }
.cst-search { margin-bottom: 8px; }
.cst-tree { flex: 1; overflow: auto; }
.cst-node {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  min-width: 0;
}
/* 名字吃掉剩余宽度：容器名都很长，固定 150px 会把"哪个才是具体零件"这最关键的信息截掉 */
.cst-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cst-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.12);
}
.cst-fold {
  flex-shrink: 0;
  font-size: 11px;
  color: #bfbfbf;
}
.cst-tag {
  margin: 0;
  transform: scale(0.85);
  transform-origin: right center;
  flex-shrink: 0;
}
.cst-tag-instance { color: #d46b08; border-color: #ffd591; }
.cst-empty { margin-top: 24px; }

/* 选中行：整行铺蓝底会把"当前零件"糊成一片蓝，改成左侧色条 + 浅底 + 加粗，
   扫一眼就能定位到自己点中的那一个节点 */
.cst-tree :deep(.ant-tree-node-content-wrapper) {
  border-radius: 0 4px 4px 0;
}
.cst-tree :deep(.ant-tree-node-content-wrapper.ant-tree-node-selected) {
  background: #e6f4ff !important;
  box-shadow: inset 2px 0 0 #1668dc;
  color: #0958d9;
  font-weight: 600;
}
</style>
