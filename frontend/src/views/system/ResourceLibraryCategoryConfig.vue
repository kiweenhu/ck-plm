<template>
  <div class="clcc-page">
    <div class="clcc-header">
      <div class="clcc-header-left">
        <h3 class="clcc-title">资源库分类绑定</h3>
        <span class="clcc-subtitle">为元器件库 / 标准件库 / 通用件库各自绑定「分类管理」中的分类根节点，决定各资源库左侧展示的分类子树</span>
      </div>
    </div>

    <a-spin :spinning="loading">
      <!--
        三行资源库绑定：直接用 v-for + v-model 绑定 rows（标准 Vue 响应式），
        不经过 a-table 的 slot record 代理层 —— 彻底避免「选择值显示 OK 但保存读到旧值」的脱钩问题。
        v-model 直接读写 rows.value[i].rootOid，选中即写入，保存即读到。
      -->
      <div v-for="row in rows" :key="row.code" class="clcc-row">
        <div class="clcc-row-name">
          <strong>{{ row.libName }}</strong>
          <a-tag size="small" color="blue" style="margin-left:8px">{{ row.code }}</a-tag>
        </div>
        <a-tree-select
          v-model:value="row.rootOid"
          :tree-data="clsTreeSelectData"
          :field-names="{ label: 'label', value: 'oid', children: 'children' }"
          placeholder="从分类管理树中选择该资源库的分类根节点"
          tree-default-expand-all
          show-search
          :tree-node-filter-prop="'label'"
          allow-clear
          class="clcc-row-select"
        />
        <a-button
          size="small"
          type="primary"
          :loading="savingKey === row.code"
          class="clcc-row-btn"
          @click="save(row.code)"
        >保存绑定</a-button>
      </div>

      <div class="clcc-hint">
        三个资源子库（<b>COMPONENT</b> 元器件库 / <b>STD_PART</b> 标准件库 / <b>GEN_PART</b> 通用件库）共享同一张分类表
        <code>ck_library_cls_config</code>，通过 <code>resource_code</code> 列区分各自绑定。
        绑定仅由企业/业务管理员维护；各资源库页面只读展示该分类子树并按分类筛选对应软类型的 Part。
      </div>
    </a-spin>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  getAllCategoryBindings, bindLibraryCategory, getClassificationTree,
} from '@/api'

const loading = ref(false)
// 按行独立的保存状态：savingKey === 'COMPONENT' 表示元器件库在保存中
const savingKey = ref('')

/** 三个资源子库对应的绑定行（结构化配置） */
const rows = ref([
  { code: 'COMPONENT', libName: '元器件库', rootOid: null, rootName: '' },
  { code: 'STD_PART',   libName: '标准件库', rootOid: null, rootName: '' },
  { code: 'GEN_PART',   libName: '通用件库', rootOid: null, rootName: '' },
])

const clsTree = ref([])

/** 查找分类 oid 在分类树中的显示名（回显诊断用） */
function findName(oid, nodes) {
  for (const n of (nodes || [])) {
    if (n.oid === oid) return n.label
    if (n.children?.length) {
      const r = findName(oid, n.children)
      if (r) return r
    }
  }
  return ''
}

const clsTreeSelectData = computed(() => clsTree.value)

async function load() {
  loading.value = true
  try {
    // 1. 加载全部分类树（三个资源库共享同一棵分类树，各自选根节点）
    const treeRes = await getClassificationTree()
    if (treeRes?.code === 200) {
      const mapNode = (n) => ({
        oid: n.oid,
        label: n.displayName || n.name || n.code || n.identifier,
        children: (n.children || []).map(mapNode),
      })
      clsTree.value = (treeRes.data || []).map(mapNode)
    }
    // 2. 一次性加载所有资源库各自的绑定（含平台租户回退，替代 N 次单条调用）
    const bindRes = await getAllCategoryBindings()
    const bindMap = (bindRes?.code === 200 && bindRes.data) ? bindRes.data : {}
    console.log('[资源库分类] 后端返回绑定配置 =', bindMap)
    for (const r of rows.value) {
      // 容错查找：后端已归一化大写，此处再兼容小写/原样
      const oid = bindMap[r.code] || bindMap[r.code?.toLowerCase()] || bindMap[r.code?.toUpperCase()]
      r.rootOid = (oid && oid.length > 0) ? oid : null
      if (r.rootOid && !findName(r.rootOid, clsTree.value)) {
        console.warn(`[资源库分类] ${r.code} 绑定分类 ${r.rootOid} 不在分类树中，可能无法回显名称`)
      }
    }
  } catch (e) {
    // 不静默：加载失败会导致绑定无法回显，必须暴露出来便于定位
    console.error('[资源库分类] 加载失败', e)
    message.warning('分类绑定加载失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally { loading.value = false }
}

/** 按 code 从唯一数据源 rows 中取行 */
function getRow(code) {
  return rows.value.find(r => r.code === code)
}

async function save(code) {
  const data = getRow(code)
  if (!data) {
    message.error('未找到资源库: ' + code)
    return
  }
  console.log('[保存绑定] code =', data.code, 'rootOid =', data.rootOid, 'libName =', data.libName)
  if (!data.rootOid) {
    message.warning(`请选择「${data.libName}」的分类根节点`)
    return
  }
  savingKey.value = data.code
  try {
    const res = await bindLibraryCategory(data.code, data.rootOid)
    console.log('[保存绑定] 响应 =', res)
    if (res?.code === 200) {
      message.success(`${data.libName} 分类绑定已保存`)
    } else {
      message.error(res?.message || '绑定失败')
    }
  } catch (e) {
    console.error('[保存绑定] 异常 =', e)
    message.error(e?.response?.data?.message || '绑定失败')
  } finally {
    savingKey.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.clcc-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.clcc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}
.clcc-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.clcc-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}
.clcc-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

/* 绑定行（替代原 a-table）：flex 布局，名称 + 树选择 + 按钮 */
.clcc-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  margin-bottom: 10px;
  background: #fff;
  transition: border-color 0.2s;
}
.clcc-row:hover {
  border-color: #d9d9d9;
}
.clcc-row-name {
  width: 200px;
  flex-shrink: 0;
  white-space: nowrap;
}
.clcc-row-select {
  flex: 1;
  min-width: 280px;
}
.clcc-row-btn {
  flex-shrink: 0;
}

.clcc-hint {
  margin-top: 12px;
  color: #8c8c8c;
  font-size: 12px;
  background: #fafafa;
  padding: 8px 12px;
  border-radius: 6px;
  line-height: 1.6;
}
.clcc-hint code {
  font-size: 11px;
  background: #f5f5f5;
  padding: 0 4px;
  border-radius: 3px;
  color: #595959;
}
</style>
