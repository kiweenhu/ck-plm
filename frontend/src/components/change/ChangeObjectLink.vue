<template>
  <span class="col-wrap">
    <!-- 零组件有独立详情页：真超链接、新标签页打开 —— 办理页里填了一半的意见不会被导航冲掉 -->
    <a
      v-if="partHref"
      :href="partHref"
      target="_blank"
      rel="noopener"
      class="col-link"
      :title="`在新标签页查看 ${label} 详情`"
    >{{ code }}</a>
    <!-- 文档 / 工程数据没有独立详情路由：复用列表页同一个 DocumentViewer 抽屉 -->
    <a
      v-else-if="isDoc"
      href="#"
      class="col-link"
      :title="`查看 ${label} 详情`"
      @click.prevent="openDoc"
    >{{ code }}</a>
    <span v-else class="col-plain">{{ code }}</span>

    <DocumentViewer
      v-if="viewerDoc"
      v-model:visible="viewerVisible"
      :doc="viewerDoc"
      @close="closeViewer"
    />
  </span>
</template>

<script setup>
import { computed, defineAsyncComponent, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getDocument, getEngineeringDocument } from '@/api'
import { entityDetailPath } from '@/utils/entityRef'

/**
 * 变更单据里的「对象编码」→ 关联详情页。
 *
 * <p>变更请求 / 通告 / 任务各有一张受影响对象表（详情抽屉、执行表单、审批与会签摘要），
 * 编码原先都是纯文本，审批人想核对对象属性只能另开列表再找一遍。这里统一成一处：
 * <ul>
 *   <li><b>PART</b>：走 {@code utils/entityRef.entityDetailPath} 给的唯一详情路由 {@code /part/:oid}
 *       （不带迭代 oid，详情页取该对象最新版本 —— 与刚改出来的 B.2 一致）；</li>
 *   <li><b>DOCUMENT / ENG_DOCUMENT</b>：没有独立详情页，按 {@code TaskContextView} 同一口径
 *       回查 master 后打开列表页共用的 {@code DocumentViewer} 抽屉；</li>
 *   <li>其余类型或缺 oid：只显示文字，不链到一个打不开的地址。</li>
 * </ul>
 *
 * <p>详情抽屉按需异步加载：{@code DocumentViewer} 顶层静态引了 @vue-office/docx，
 * 静态 import 会把整个预览依赖拖进任务表单的模块图（node 环境还会因 window 报错）。
 */
const DocumentViewer = defineAsyncComponent(() => import('@/views/product/DocumentViewer.vue'))

const props = defineProps({
  /** 变更对象行（ChangeAffectedObject / ChangeRequestAffectedObject） */
  record: { type: Object, default: null }
})

const oid = computed(() => props.record?.entityOid || '')
const type = computed(() => String(props.record?.entityType || '').trim().toUpperCase())
const code = computed(() => props.record?.entityCode || oid.value || '—')
const label = computed(() => props.record?.entityName || code.value)

/** 只有 PART 宿主有详情页；非 PART 返回空串（与流程监控的实体标识同一口径） */
const partHref = computed(() => entityDetailPath({ entityOid: oid.value, typeCode: type.value }))
const isDoc = computed(() => !!oid.value && (type.value === 'DOCUMENT' || type.value === 'ENG_DOCUMENT'))

const viewerVisible = ref(false)
const viewerDoc = ref(null)
const loading = ref(false)

async function openDoc() {
  if (!oid.value || loading.value) return
  loading.value = true
  try {
    const res = type.value === 'ENG_DOCUMENT'
      ? await getEngineeringDocument(oid.value)
      : await getDocument(oid.value)
    if (res?.code && res.code !== 200) {
      message.error(res.message || '加载详情失败')
      return
    }
    const data = res?.data ?? res
    if (!data || typeof data !== 'object') {
      message.error('加载详情失败')
      return
    }
    // entityType 显式带上：DocumentViewer 靠它区分工程数据（关联零件 / 数模预览）
    viewerDoc.value = {
      ...data,
      oid: oid.value,
      entityType: type.value,
      code: data.code || data.number || props.record?.entityCode,
      name: data.name || props.record?.entityName
    }
    viewerVisible.value = true
  } catch (e) {
    message.error(e?.response?.data?.message || e?.message || '加载详情失败')
  } finally {
    loading.value = false
  }
}

/** 关掉就卸载：一张表里 N 行也不会挂 N 个抽屉 */
function closeViewer() {
  viewerVisible.value = false
  viewerDoc.value = null
}
</script>

<style scoped>
.col-wrap {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.col-link,
.col-plain {
  font-family: inherit;
}

/* 编码是入口：蓝 + 悬停下划线，与 EntityRefLabel 的编码链接同一观感 */
.col-link,
.col-link:visited {
  color: #1677ff;
  text-decoration: none;
  cursor: pointer;
}

.col-link:hover {
  text-decoration: underline;
}
</style>
